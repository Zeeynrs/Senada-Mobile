package senada.catalog.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import senada.catalog.AlbumSummary
import senada.catalog.ArtistSummary
import senada.catalog.CatalogException
import senada.catalog.CatalogException.Kind
import senada.model.Track

internal const val DISCOVER_SEED_ARTISTS = 3
internal const val DISCOVER_SEED_SONGS = 2
internal const val DISCOVER_LIMIT = 12
internal const val DISCOVER_SAVE_DATA_LIMIT = 6
private const val RECENTLY_PLAYED_SKIP_COUNT = 10
private const val MAX_PLAY_MULTIPLIER = 5

/** Benih rekomendasi yang dihitung dari riwayat putar, play_event, dan favorit pengguna. */
internal data class DiscoverSeeds(
    val artistNames: List<String> = emptyList(),
    val songVideoIds: List<String> = emptyList(),
    val recentlyPlayedTrackIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = artistNames.isEmpty() && songVideoIds.isEmpty()
}

/** Satu lagu dari radio Innertube `next`, beserta album dan ID kanal artis bila tersedia. */
internal data class RadioEntry(
    val track: Track,
    val album: AlbumSummary? = null,
    val artistIds: Map<String, String> = emptyMap(),
)

/** Artis serupa ("Penggemar mungkin juga suka") dan album dari halaman satu artis. */
internal data class ArtistDiscoverData(
    val relatedArtists: List<ArtistSummary> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
)

/** Kumpulan lagu, artis, dan album dari rak beranda YouTube Music. */
internal data class DiscoverFeed(
    val tracks: List<Track> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
)

/** Hasil rekomendasi "Untuk kamu" yang siap ditampilkan di tab Cari. */
internal data class DiscoverResult(
    val tracks: List<Track> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val personal: Boolean = false,
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && artists.isEmpty() && albums.isEmpty()
}

/** Sumber data jaringan untuk rekomendasi; mudah dipalsukan di unit test. */
internal interface DiscoverSource {
    suspend fun radio(videoId: String): List<RadioEntry>
    suspend fun artistRelated(channelId: String): ArtistDiscoverData
    suspend fun findArtistId(name: String): String?
    suspend fun homeFeed(saveData: Boolean): DiscoverFeed
}

/**
 * Memilih artis yang paling sering didengar (riwayat/putar terbaru berbobot ganda, favorit berbobot satu)
 * serta lagu terakhir yang diputar sebagai benih rekomendasi, tanpa lagu contoh atau file lokal.
 */
internal fun pickDiscoverSeeds(
    history: List<Track>,
    favorites: List<Track>,
    recentPlayCounts: List<Pair<Track, Int>> = emptyList(),
    maxArtists: Int = DISCOVER_SEED_ARTISTS,
    maxSongs: Int = DISCOVER_SEED_SONGS,
): DiscoverSeeds {
    val playCountById = recentPlayCounts.associate { (track, plays) -> track.id to plays }
    val seenHistoryIds = mutableSetOf<String>()

    data class ArtistScore(val displayName: String, var score: Int, val order: Int)
    val scores = mutableMapOf<String, ArtistScore>()
    var order = 0

    fun addArtists(track: Track, weight: Int) {
        if (track.source != YOUTUBE_MUSIC_SOURCE) return
        for (raw in track.artists) {
            val clean = raw.trim()
            val key = clean.lowercase()
            if (key.isEmpty()) continue
            val current = scores[key]
            if (current == null) {
                scores[key] = ArtistScore(clean, weight, order++)
            } else {
                current.score += weight
            }
        }
    }

    for (track in history) {
        seenHistoryIds += track.id
        val multiplier = (playCountById[track.id] ?: 1).coerceIn(1, MAX_PLAY_MULTIPLIER)
        addArtists(track, weight = 2 * multiplier)
    }
    for ((track, plays) in recentPlayCounts) {
        if (track.id !in seenHistoryIds) {
            addArtists(track, weight = 2 * plays.coerceIn(1, MAX_PLAY_MULTIPLIER))
        }
    }
    for (track in favorites) {
        addArtists(track, weight = 1)
    }

    val artistNames = scores.values
        .sortedWith(compareByDescending<ArtistScore> { it.score }.thenBy { it.order })
        .take(maxArtists)
        .map { it.displayName }

    val songVideoIds = (history + favorites)
        .mapNotNull(::videoIdOf)
        .distinct()
        .take(maxSongs)

    val recentlyPlayed = history
        .take(RECENTLY_PLAYED_SKIP_COUNT)
        .map { it.id }
        .toSet()

    return DiscoverSeeds(
        artistNames = artistNames,
        songVideoIds = songVideoIds,
        recentlyPlayedTrackIds = recentlyPlayed,
    )
}

/** Menggabungkan daftar secara bergantian agar satu benih tidak mendominasi seluruh rak. */
internal fun <T> interleave(lists: List<List<T>>): List<T> {
    val maxLen = lists.maxOfOrNull { it.size } ?: return emptyList()
    return buildList {
        for (index in 0 until maxLen) {
            for (list in lists) {
                if (index < list.size) add(list[index])
            }
        }
    }
}

private fun <T> uniqueBy(items: List<T>, skip: Set<String> = emptySet(), key: (T) -> String): List<T> {
    val seen = skip.toMutableSet()
    return buildList {
        for (item in items) {
            val k = key(item)
            if (k.isNotEmpty() && seen.add(k)) add(item)
        }
    }
}

/**
 * Membangun rak lagu, artis, dan album rekomendasi dari benih pengguna; kekurangan (atau pengguna baru
 * tanpa riwayat) diisi dari rak beranda YouTube Music. Mode [saveData] memperkecil jumlah permintaan.
 */
internal suspend fun buildDiscoverRecommendations(
    seeds: DiscoverSeeds,
    source: DiscoverSource,
    saveData: Boolean = false,
    limit: Int = if (saveData) DISCOVER_SAVE_DATA_LIMIT else DISCOVER_LIMIT,
): DiscoverResult = coroutineScope {
    val songSeeds = seeds.songVideoIds.take(if (saveData) 1 else DISCOVER_SEED_SONGS)
    val artistNameSeeds = seeds.artistNames.take(if (saveData) 1 else DISCOVER_SEED_ARTISTS)

    val radioResults = songSeeds.map { videoId ->
        async { safeCall { source.radio(videoId) }.orEmpty() }
    }.awaitAll()

    val knownArtistIds = mutableMapOf<String, String>()
    for (entries in radioResults) {
        for (entry in entries) {
            entry.artistIds.forEach { (key, id) -> if (key !in knownArtistIds) knownArtistIds[key] = id }
        }
    }

    val resolvedArtistIds = artistNameSeeds.map { name ->
        async {
            knownArtistIds[name.trim().lowercase()] ?: safeCall { source.findArtistId(name) }
        }
    }.awaitAll().filterNotNull().distinct()

    val artistPages = resolvedArtistIds.map { id ->
        async { safeCall { source.artistRelated(id) } ?: ArtistDiscoverData() }
    }.awaitAll()

    // Entri pertama dari radio `next` adalah lagu benih itu sendiri, jadi dilewati.
    val radioTail = radioResults.map { it.drop(1) }
    val albumByTrackId = radioTail.flatten().mapNotNull { entry ->
        entry.album?.let { entry.track.id to it }
    }.toMap()

    var tracks = uniqueBy(
        items = interleave(radioTail.map { list -> list.map { it.track } }),
        skip = seeds.recentlyPlayedTrackIds,
        key = { it.id },
    )
    val seedArtistIdSet = resolvedArtistIds.toSet()
    var artists = uniqueBy(
        items = interleave(artistPages.map { it.relatedArtists }),
        skip = seedArtistIdSet,
        key = { it.id },
    )
    val albumsFromTracks = tracks.mapNotNull { albumByTrackId[it.id] }
    var albums = uniqueBy(
        items = interleave(artistPages.map { it.albums.take(4) } + listOf(albumsFromTracks)),
        key = { it.id },
    )
    val personal = tracks.isNotEmpty() || artists.isNotEmpty() || albums.isNotEmpty()

    val needsHome = if (saveData) {
        !personal
    } else {
        tracks.size < 6 || artists.size < 4 || albums.size < 4
    }
    if (needsHome) {
        val home = try {
            source.homeFeed(saveData)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (personal) DiscoverFeed() else throw e
        }
        tracks = uniqueBy(
            items = tracks + home.tracks,
            skip = if (personal) seeds.recentlyPlayedTrackIds else emptySet(),
            key = { it.id },
        )
        artists = uniqueBy(items = artists + home.artists, skip = seedArtistIdSet, key = { it.id })
        albums = uniqueBy(items = albums + home.albums, key = { it.id })
    }

    if (tracks.isEmpty() && artists.isEmpty() && albums.isEmpty()) {
        throw CatalogException(Kind.Network, "Rekomendasi tidak dapat dimuat. Periksa koneksi lalu coba lagi.")
    }
    DiscoverResult(
        tracks = tracks.take(limit),
        artists = artists.take(limit),
        albums = albums.take(limit),
        personal = personal,
    )
}

/** Membaca daftar lagu radio dari respons Innertube `next` (`RDAMVM<videoId>`). */
internal fun parseRadioTracks(response: JsonObject): List<RadioEntry> {
    val contents = response.array(
        "contents",
        "singleColumnMusicWatchNextResultsRenderer",
        "tabbedRenderer",
        "watchNextTabbedResultsRenderer",
        "tabs",
        0,
        "tabRenderer",
        "content",
        "musicQueueRenderer",
        "content",
        "playlistPanelRenderer",
        "contents",
    )
    val entries = contents.mapNotNull { item ->
        val renderer = item.at("playlistPanelVideoRenderer")
            ?: item.at("playlistPanelVideoWrapperRenderer", "primaryRenderer", "playlistPanelVideoRenderer")
            ?: return@mapNotNull null
        parseRadioPanelItem(renderer)
    }
    return entries.distinctBy { it.track.id }
}

private fun parseRadioPanelItem(renderer: JsonElement): RadioEntry? {
    val videoId = renderer.string("videoId")?.takeIf { it.isNotBlank() } ?: return null
    val title = textOf(renderer.at("title"))
    if (title.isEmpty()) return null

    val bylineRuns = renderer.array("longBylineText", "runs")
        .ifEmpty { renderer.array("shortBylineText", "runs") }

    val artists = mutableListOf<String>()
    val artistIds = mutableMapOf<String, String>()
    var albumName: String? = null
    var albumId: String? = null

    for (run in bylineRuns) {
        val text = run.string("text")?.trim().orEmpty()
        if (text.isEmpty() || text == "•" || text == "&" || text == ",") continue
        val target = browseTarget(run)
        when (target?.first) {
            KIND_ARTIST -> {
                artists += text
                artistIds[text.lowercase()] = target.second
            }
            KIND_ALBUM -> if (albumId == null) {
                albumName = text
                albumId = target.second
            }
        }
    }
    if (artists.isEmpty()) {
        bylineRuns.firstOrNull {
            val t = it.string("text")?.trim().orEmpty()
            t.isNotEmpty() && t != "•"
        }?.string("text")?.trim()?.let { artists += it }
    }

    val artworkUrl = bestArtwork(renderer)
    val track = Track(
        id = youTubeTrackId(videoId),
        title = title,
        artists = artists,
        durationMs = parseDuration(textOf(renderer.at("lengthText"))),
        artworkUrl = artworkUrl,
        source = YOUTUBE_MUSIC_SOURCE,
    )
    val album = if (albumName != null && albumId != null && albumId.startsWith("MPREb_")) {
        val subtitle = listOfNotNull("Album", artists.joinToString().takeIf { it.isNotEmpty() }).joinToString(" • ")
        AlbumSummary(id = albumId, title = albumName, subtitle = subtitle, artworkUrl = artworkUrl)
    } else {
        null
    }
    return RadioEntry(track = track, album = album, artistIds = artistIds)
}

/** Membaca artis serupa ("Penggemar mungkin juga suka") dan album dari respons halaman artis. */
internal fun parseArtistRelated(response: JsonObject): ArtistDiscoverData {
    val related = mutableListOf<ArtistSummary>()
    val albums = mutableListOf<AlbumSummary>()

    for (section in sectionListContents(response)) {
        val carousel = section.at("musicCarouselShelfRenderer") ?: continue
        for (item in carousel.array("contents")) {
            val twoRow = item.at("musicTwoRowItemRenderer") ?: continue
            parseArtistCard(twoRow)?.let { related += it }
            parseAlbumCard(twoRow)?.let { albums += it }
        }
    }
    return ArtistDiscoverData(
        relatedArtists = related.distinctBy { it.id },
        albums = albums.distinctBy { it.id },
    )
}

/** Membaca lagu, artis, dan album dari rak beranda/jelajah/tangga lagu YouTube Music. */
internal fun parseHomeFeed(response: JsonObject): DiscoverFeed {
    val sections = sectionListContents(response)
        .ifEmpty { response.array("continuationContents", "sectionListContinuation", "contents") }
    val tracks = mutableListOf<Track>()
    val artists = mutableListOf<ArtistSummary>()
    val albums = mutableListOf<AlbumSummary>()

    for (section in sections) {
        val shelf = section.at("musicCarouselShelfRenderer") ?: section.at("musicShelfRenderer") ?: continue
        for (item in shelf.array("contents")) {
            item.at("musicResponsiveListItemRenderer")?.let { row ->
                val track = parseBrowseTrack(row)
                if (track != null) {
                    tracks += track
                } else {
                    parseArtistSummary(row)?.let { artists += it }
                    parseAlbumSummary(row)?.let { albums += it }
                }
            }
            item.at("musicTwoRowItemRenderer")?.let { card ->
                val track = parseTwoRowTrack(card)
                if (track != null) {
                    tracks += track
                } else {
                    parseArtistCard(card)?.let { artists += it }
                    parseAlbumCard(card)?.let { albums += it }
                }
            }
        }
    }
    return DiscoverFeed(
        tracks = tracks.distinctBy { it.id },
        artists = artists.distinctBy { it.id },
        albums = albums.distinctBy { it.id },
    )
}

private fun parseTwoRowTrack(item: JsonElement): Track? {
    val videoId = item.string("navigationEndpoint", "watchEndpoint", "videoId")
        ?.takeIf { it.isNotBlank() } ?: return null
    val title = textOf(item.at("title"))
    if (title.isEmpty()) return null
    val subRuns = item.array("subtitle", "runs")
    val linked = subRuns
        .filter { pageType(it) == PAGE_TYPE_ARTIST }
        .mapNotNull { it.string("text")?.trim()?.takeIf(String::isNotEmpty) }
    val artists = linked.ifEmpty {
        subRuns.mapNotNull { it.string("text")?.trim() }
            .takeWhile { it != "•" }
            .filter { it.isNotEmpty() && it.lowercase() !in setOf("lagu", "song", "video") }
    }
    return Track(
        id = youTubeTrackId(videoId),
        title = title,
        artists = artists,
        durationMs = subRuns.mapNotNull { it.string("text") }.lastOrNull { parseDuration(it) != null }?.let(::parseDuration),
        artworkUrl = bestArtwork(item),
        source = YOUTUBE_MUSIC_SOURCE,
    )
}

/** Implementasi jaringan [DiscoverSource] lewat Innertube tanpa akun. */
internal class YouTubeMusicDiscoverSource(private val innertube: InnertubeClient) : DiscoverSource {
    private val artistIdCache = mutableMapOf<String, String>()

    override suspend fun radio(videoId: String): List<RadioEntry> =
        parseRadioTracks(innertube.next(videoId, "RDAMVM$videoId"))

    override suspend fun artistRelated(channelId: String): ArtistDiscoverData =
        parseArtistRelated(innertube.browse(channelId))

    override suspend fun findArtistId(name: String): String? {
        val clean = name.trim()
        val key = clean.lowercase()
        if (key.isEmpty()) return null
        artistIdCache[key]?.let { return it }
        val page = parseArtistSearchPage(innertube.search(clean, InnertubeClient.ARTISTS_FILTER))
        val matched = page.items.firstOrNull { it.name.trim().equals(clean, ignoreCase = true) }
            ?: return null
        artistIdCache[key] = matched.id
        return matched.id
    }

    override suspend fun homeFeed(saveData: Boolean): DiscoverFeed = coroutineScope {
        val browseIds = if (saveData) listOf(HOME_BROWSE_ID) else listOf(HOME_BROWSE_ID, EXPLORE_BROWSE_ID, CHARTS_BROWSE_ID)
        val results = browseIds.map { id ->
            async {
                try {
                    Result.success(parseHomeFeed(innertube.browse(id)))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }.awaitAll()

        val feeds = results.mapNotNull { it.getOrNull() }
        if (feeds.isEmpty()) {
            throw results.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: CatalogException(Kind.Network, "Rekomendasi tidak dapat dimuat.")
        }
        DiscoverFeed(
            tracks = feeds.flatMap { it.tracks }.distinctBy { it.id },
            artists = feeds.flatMap { it.artists }.distinctBy { it.id },
            albums = feeds.flatMap { it.albums }.distinctBy { it.id },
        )
    }

    private companion object {
        const val HOME_BROWSE_ID = "FEmusic_home"
        const val EXPLORE_BROWSE_ID = "FEmusic_explore"
        const val CHARTS_BROWSE_ID = "FEmusic_charts"
    }
}

private suspend inline fun <T> safeCall(block: () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}
