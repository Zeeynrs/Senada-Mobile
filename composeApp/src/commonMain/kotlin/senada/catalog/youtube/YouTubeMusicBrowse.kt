package senada.catalog.youtube

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import senada.catalog.AlbumDetail
import senada.catalog.AlbumPage
import senada.catalog.AlbumSummary
import senada.catalog.ArtistDetail
import senada.catalog.ArtistPage
import senada.catalog.ArtistSummary
import senada.catalog.CatalogException
import senada.catalog.CatalogException.Kind
import senada.model.Track

internal const val PAGE_TYPE_ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
internal const val PAGE_TYPE_ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
private const val PAGE_TYPE_AUDIOBOOK = "MUSIC_PAGE_TYPE_AUDIOBOOK"
internal const val PAGE_TYPE_PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"

internal const val KIND_ARTIST = "artist"
internal const val KIND_ALBUM = "album"
internal const val KIND_PLAYLIST = "playlist"

private val NON_ARTIST_PAGE_TYPES = setOf(PAGE_TYPE_ALBUM, PAGE_TYPE_AUDIOBOOK, PAGE_TYPE_PLAYLIST)
private val TYPE_LABELS = setOf(
    "lagu", "song", "video", "episode", "episode podcast",
    "single", "album", "ep", "playlist", "artis", "artist",
)
private val SEPARATORS = setOf("•", ",", "&", "dan", "and")
private val YEAR = Regex("^\\d{4}$")
private val COUNT_HINT = Regex("(x|jt|rb\\.?|views|plays|pemutaran|ditonton|pendengar|audiens|subscriber)")
internal const val POPULAR_ALBUMS_MIN_COUNT = 4
private val ALBUM_SHELF_TITLES = setOf("album", "albums")
private val SINGLE_SHELF_TITLE = Regex("""\b(singles?|eps?)\b""", RegexOption.IGNORE_CASE)
private val SINGLE_SUBTITLE_TYPES = setOf("single", "ep")

private enum class ArtistCarouselKind { Albums, Singles, Other }

/** Membaca halaman pertama pencarian artis atau halaman lanjutannya. */
internal fun parseArtistSearchPage(response: JsonObject): ArtistPage {
    val shelves = searchShelves(response)
    val artists = shelves.flatMap { shelf ->
        shelf.array("contents").mapNotNull { item ->
            item.at("musicResponsiveListItemRenderer")?.let(::parseArtistSummary)
        }
    }
    return ArtistPage(artists.distinctBy { it.id }, nextContinuation(shelves))
}

/** Membaca halaman pertama pencarian album atau halaman lanjutannya. */
internal fun parseAlbumSearchPage(response: JsonObject): AlbumPage {
    val shelves = searchShelves(response)
    val albums = shelves.flatMap { shelf ->
        shelf.array("contents").mapNotNull { item ->
            item.at("musicResponsiveListItemRenderer")?.let(::parseAlbumSummary)
        }
    }
    return AlbumPage(albums.distinctBy { it.id }, nextContinuation(shelves))
}

/** Membaca halaman artis: nama, foto, lagu terpopuler beserta tautan albumnya, serta album terpopuler & terbaru. */
internal fun parseArtistPage(
    channelId: String,
    response: JsonObject,
    onArtistId: (trackId: String, artistName: String, channelId: String) -> Unit = { _, _, _ -> },
): ArtistDetail {
    val header = response.at("header", "musicImmersiveHeaderRenderer")
        ?: response.at("header", "musicVisualHeaderRenderer")
    val name = textOf(header.at("title"))
    val artworkUrl = bestArtwork(header)
    val monthlyListeners = textOf(header.at("monthlyListenerCount")).takeIf { it.isNotEmpty() }
    val fallbackArtists = listOfNotNull(name.takeIf { it.isNotEmpty() })

    var topSongs = emptyList<Track>()
    var topSongsPlaylistId: String? = null
    val topSongAlbums = linkedMapOf<String, AlbumSummary>()
    val albumsShelf = mutableListOf<AlbumSummary>()
    val singlesShelf = mutableListOf<AlbumSummary>()

    for (section in sectionListContents(response)) {
        val shelf = section.at("musicShelfRenderer")
        if (shelf != null && topSongs.isEmpty()) {
            topSongs = shelf.array("contents")
                .mapNotNull {
                    it.at("musicResponsiveListItemRenderer")?.let { row ->
                        parseBrowseTrack(
                            row,
                            fallbackArtists,
                            onArtistId = onArtistId,
                            onAlbum = { trackId, album ->
                                if (trackId !in topSongAlbums) topSongAlbums[trackId] = album
                            },
                        )
                    }
                }
                .distinctBy { it.id }
            if (topSongs.isNotEmpty()) {
                topSongsPlaylistId = parseTopSongsPlaylistId(shelf)
            }
            if (name.isNotEmpty()) {
                topSongs.forEach { song -> onArtistId(song.id, name, channelId) }
            }
            continue
        }
        val carousel = section.at("musicCarouselShelfRenderer") ?: continue
        val cards = carousel.array("contents")
            .mapNotNull { it.at("musicTwoRowItemRenderer")?.let(::parseAlbumCard) }
            .distinctBy { it.id }
        if (cards.isEmpty()) continue
        when (classifyArtistCarousel(shelfTitle(carousel), cards)) {
            ArtistCarouselKind.Albums -> albumsShelf += cards
            ArtistCarouselKind.Singles -> singlesShelf += cards
            ArtistCarouselKind.Other -> Unit
        }
    }

    val uniqueAlbums = albumsShelf.distinctBy { it.id }
    val uniqueSingles = singlesShelf.distinctBy { it.id }
    val popularAlbums = buildPopularAlbums(topSongs, topSongAlbums, uniqueAlbums, uniqueSingles)
    val newestAlbums = buildNewestAlbums(uniqueAlbums, uniqueSingles)

    if (name.isEmpty() && topSongs.isEmpty() && popularAlbums.isEmpty() && newestAlbums.isEmpty()) {
        throw CatalogException(Kind.Unreadable, "Halaman artis tidak memuat data yang dapat dibaca")
    }
    return ArtistDetail(
        id = channelId,
        name = name,
        artworkUrl = artworkUrl,
        monthlyListeners = monthlyListeners,
        topSongs = topSongs,
        topSongsPlaylistId = topSongsPlaylistId,
        topSongAlbums = topSongAlbums,
        popularAlbums = popularAlbums,
        newestAlbums = newestAlbums,
    )
}

/**
 * Menyusun daftar "Album terpopuler": album yang memuat lagu terpopuler diurutkan menurut peringkat
 * lagu terbaiknya, dicocokkan ke rak album untuk sampul/judul lengkap (atau memakai info baris lagu
 * bila tidak ada di rak). Bila hasilnya kurang dari [minCount], dilengkapi sisa rak album sesuai
 * urutan YouTube Music. Single & EP hanya dipakai jika artis tidak memiliki album penuh.
 */
internal fun buildPopularAlbums(
    topSongs: List<Track>,
    topSongAlbums: Map<String, AlbumSummary>,
    albumsShelf: List<AlbumSummary>,
    singlesShelf: List<AlbumSummary>,
    minCount: Int = POPULAR_ALBUMS_MIN_COUNT,
): List<AlbumSummary> {
    val hasProperAlbums = albumsShelf.isNotEmpty()
    val baseShelf = if (hasProperAlbums) albumsShelf else singlesShelf
    val shelfById = baseShelf.associateBy { it.id }
    val excludedSingleIds = if (hasProperAlbums) singlesShelf.mapTo(HashSet()) { it.id } else emptySet()

    val result = mutableListOf<AlbumSummary>()
    val seen = HashSet<String>()

    for (song in topSongs) {
        val fromSong = topSongAlbums[song.id] ?: continue
        if (fromSong.id in seen) continue
        val fromShelf = shelfById[fromSong.id]
        if (fromShelf != null) {
            seen += fromShelf.id
            result += fromShelf
        } else if (fromSong.id !in excludedSingleIds) {
            seen += fromSong.id
            result += fromSong
        }
    }

    if (result.size < minCount) {
        for (album in baseShelf) {
            if (result.size >= minCount) break
            if (seen.add(album.id)) {
                result += album
            }
        }
    }
    return result
}

/**
 * Menyusun daftar "Album terbaru": rak album (atau Single & EP bila rak album kosong) diurutkan
 * menurut tahun terbaru ke terlama, tahun tak diketahui di akhir, dan urutan rak dipertahankan bila
 * tahunnya sama.
 */
internal fun buildNewestAlbums(
    albumsShelf: List<AlbumSummary>,
    singlesShelf: List<AlbumSummary>,
): List<AlbumSummary> = sortNewestAlbums(albumsShelf.ifEmpty { singlesShelf })

internal fun sortNewestAlbums(albums: List<AlbumSummary>): List<AlbumSummary> =
    albums.sortedWith(
        Comparator { a, b ->
            val yearA = a.year?.trim()?.toIntOrNull()
            val yearB = b.year?.trim()?.toIntOrNull()
            when {
                yearA == yearB -> 0
                yearA == null -> 1
                yearB == null -> -1
                else -> yearB.compareTo(yearA)
            }
        },
    )

private fun classifyArtistCarousel(title: String, cards: List<AlbumSummary>): ArtistCarouselKind {
    val normalized = title.trim().lowercase()
    return when {
        normalized in ALBUM_SHELF_TITLES -> ArtistCarouselKind.Albums
        SINGLE_SHELF_TITLE.containsMatchIn(normalized) -> ArtistCarouselKind.Singles
        normalized.isEmpty() -> {
            val allSingles = cards.all { card ->
                val head = card.subtitle?.split("•", "·")?.firstOrNull()?.trim()?.lowercase()
                head in SINGLE_SUBTITLE_TYPES
            }
            if (allSingles) ArtistCarouselKind.Singles else ArtistCarouselKind.Albums
        }
        else -> ArtistCarouselKind.Other
    }
}

private fun parseTopSongsPlaylistId(shelf: JsonElement): String? {
    val endpoints = buildList {
        shelf.at("bottomEndpoint")?.let(::add)
        shelf.array("title", "runs").mapNotNullTo(this) { it.at("navigationEndpoint") }
    }
    for (endpoint in endpoints) {
        val target = browseTarget(endpoint)
        if (target != null) {
            if (target.first == KIND_PLAYLIST) {
                return target.second.trim().removePrefix("VL").takeIf { it.isNotEmpty() }
            }
            continue
        }
        val browse = endpoint.at("browseEndpoint") ?: continue
        val explicitPageType = browse.string(
            "browseEndpointContextSupportedConfigs",
            "browseEndpointContextMusicConfig",
            "pageType",
        )
        if (explicitPageType != null && explicitPageType != PAGE_TYPE_PLAYLIST) continue
        val rawId = browse.string("browseId")?.trim().orEmpty()
        if (rawId.startsWith("VL")) {
            return rawId.removePrefix("VL").takeIf { it.isNotEmpty() }
        }
    }
    return null
}

/** Membaca halaman album: sampul, judul, artis, tahun (bila ada), dan daftar lagu berurutan. */
internal fun parseAlbumPage(
    browseId: String,
    response: JsonObject,
    onArtistId: (trackId: String, artistName: String, channelId: String) -> Unit = { _, _, _ -> },
): AlbumDetail {
    val header = responsiveHeader(response)
    val title = textOf(header.at("title"))
    val headerArtwork = bestArtwork(header)

    val subtitleRuns = header.array("subtitle", "runs")
    val subtitleParts = subtitleRuns
        .map { it.string("text").orEmpty().trim() }
        .filter { it.isNotEmpty() && it != "•" }
    val year = subtitleParts.firstOrNull { YEAR.matches(it) }
    val typeLabel = subtitleParts.firstOrNull { !YEAR.matches(it) }

    val artistRuns = header.array("straplineTextOne", "runs").ifEmpty { subtitleRuns }
    val headerArtistLinks = artistRuns
        .filter { pageType(it) == PAGE_TYPE_ARTIST }
        .mapNotNull { run ->
            val artistName = run.string("text")?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val id = run.string("navigationEndpoint", "browseEndpoint", "browseId")?.trim()?.takeIf(String::isNotEmpty)
            artistName to id
        }
    val linkedArtists = headerArtistLinks.map { it.first }
    val artistId = headerArtistLinks.firstNotNullOfOrNull { it.second }
    val artists = linkedArtists.ifEmpty { unlinkedHeaderArtists(artistRuns) }

    val trackSections = secondarySectionContents(response).ifEmpty { sectionListContents(response) }
    val tracks = trackSections
        .firstNotNullOfOrNull { section ->
            section.at("musicShelfRenderer") ?: section.at("musicPlaylistShelfRenderer")
        }
        ?.array("contents")
        .orEmpty()
        .mapNotNull { item ->
            item.at("musicResponsiveListItemRenderer")?.let { row ->
                parseBrowseTrack(
                    row,
                    fallbackArtists = artists,
                    fallbackArtworkUrl = headerArtwork,
                    onArtistId = onArtistId,
                )
            }
        }
        .distinctBy { it.id }

    for ((artistName, id) in headerArtistLinks) {
        if (id != null) {
            tracks.forEach { track -> onArtistId(track.id, artistName, id) }
        }
    }

    if (title.isEmpty() && tracks.isEmpty()) {
        throw CatalogException(Kind.Unreadable, "Halaman album tidak memuat data yang dapat dibaca")
    }
    return AlbumDetail(
        id = browseId,
        title = title,
        artists = artists,
        artistId = artistId,
        year = year,
        typeLabel = typeLabel,
        artworkUrl = headerArtwork ?: tracks.firstOrNull()?.artworkUrl,
        tracks = tracks,
    )
}

internal fun parseArtistSummary(item: JsonElement): ArtistSummary? {
    val (kind, browseId) = browseTarget(item) ?: return null
    if (kind != KIND_ARTIST) return null
    val name = runsText(columnRuns(item, 0))
    if (name.isEmpty()) return null
    val subtitle = runsText(columnRuns(item, 1)).takeIf { it.isNotEmpty() }
    return ArtistSummary(
        id = browseId,
        name = name,
        subtitle = subtitle,
        artworkUrl = bestArtwork(item),
    )
}

internal fun parseAlbumSummary(item: JsonElement): AlbumSummary? {
    val (kind, browseId) = browseTarget(item) ?: return null
    if (kind != KIND_ALBUM) return null
    val title = runsText(columnRuns(item, 0))
    if (title.isEmpty()) return null
    val subRuns = columnRuns(item, 1)
    return AlbumSummary(
        id = browseId,
        title = title,
        subtitle = runsText(subRuns).takeIf { it.isNotEmpty() },
        year = findYear(subRuns),
        artworkUrl = bestArtwork(item),
    )
}

internal fun parseAlbumCard(item: JsonElement): AlbumSummary? {
    val target = browseTarget(item)
        ?: item.array("title", "runs").firstNotNullOfOrNull(::browseTarget)
        ?: return null
    if (target.first != KIND_ALBUM) return null
    val title = textOf(item.at("title"))
    if (title.isEmpty()) return null
    val subtitleObj = item.at("subtitle")
    val subRuns = subtitleObj.array("runs")
    val year = findYear(subRuns) ?: findYearInText(subtitleObj.string("simpleText"))
    return AlbumSummary(
        id = target.second,
        title = title,
        subtitle = textOf(subtitleObj).takeIf { it.isNotEmpty() },
        year = year,
        artworkUrl = bestArtwork(item),
    )
}

internal fun parseArtistCard(item: JsonElement): ArtistSummary? {
    val target = browseTarget(item)
        ?: item.array("title", "runs").firstNotNullOfOrNull(::browseTarget)
        ?: return null
    if (target.first != KIND_ARTIST) return null
    val name = textOf(item.at("title"))
    if (name.isEmpty()) return null
    return ArtistSummary(
        id = target.second,
        name = name,
        subtitle = textOf(item.at("subtitle")).takeIf { it.isNotEmpty() },
        artworkUrl = bestArtwork(item),
    )
}

internal fun parseBrowseTrack(
    item: JsonElement,
    fallbackArtists: List<String> = emptyList(),
    fallbackArtworkUrl: String? = null,
    onArtistId: (trackId: String, artistName: String, channelId: String) -> Unit = { _, _, _ -> },
    onAlbum: (trackId: String, album: AlbumSummary) -> Unit = { _, _ -> },
): Track? {
    val titleRuns = columnRuns(item, 0)
    val videoId = item.string("playlistItemData", "videoId")
        ?: titleRuns.firstNotNullOfOrNull { it.string("navigationEndpoint", "watchEndpoint", "videoId") }
        ?: item.string("navigationEndpoint", "watchEndpoint", "videoId")
        ?: item.string(
            "overlay", "musicItemThumbnailOverlayRenderer", "content",
            "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint", "videoId",
        )
        ?: return null
    val title = runsText(titleRuns)
    if (title.isEmpty()) return null

    val trackId = youTubeTrackId(videoId)
    val detailColumns = item.array("flexColumns").drop(1).map {
        it.array("musicResponsiveListItemFlexColumnRenderer", "text", "runs")
    }
    val fixedRuns = item.array("fixedColumns").flatMap {
        it.array("musicResponsiveListItemFixedColumnRenderer", "text", "runs")
    }
    val allMetaRuns = detailColumns.flatten() + fixedRuns

    val linkedArtistRuns = allMetaRuns.filter { pageType(it) == PAGE_TYPE_ARTIST }
    for (run in linkedArtistRuns) {
        val artistName = run.string("text")?.trim().orEmpty()
        val channelId = run.string("navigationEndpoint", "browseEndpoint", "browseId")?.trim().orEmpty()
        if (artistName.isNotEmpty() && channelId.isNotEmpty()) {
            onArtistId(trackId, artistName, channelId)
        }
    }
    val linkedArtists = linkedArtistRuns.mapNotNull { it.string("text")?.trim()?.takeIf(String::isNotEmpty) }

    val channelArtists = linkedArtists.ifEmpty {
        allMetaRuns
            .filter { run ->
                run.at("navigationEndpoint", "browseEndpoint") != null &&
                    pageType(run) !in NON_ARTIST_PAGE_TYPES
            }
            .mapNotNull { it.string("text")?.trim()?.takeIf(String::isNotEmpty) }
    }

    val artists = channelArtists
        .ifEmpty { unlinkedTrackArtists(detailColumns.firstOrNull().orEmpty()) }
        .ifEmpty { fallbackArtists }

    val durationMs = allMetaRuns
        .mapNotNull { it.string("text")?.trim() }
        .lastOrNull { parseDuration(it) != null }
        ?.let(::parseDuration)

    val resolvedArtworkUrl = bestArtwork(item) ?: fallbackArtworkUrl
    val albumRun = allMetaRuns.firstOrNull { pageType(it) == PAGE_TYPE_ALBUM }
    if (albumRun != null) {
        val albumId = albumRun.string("navigationEndpoint", "browseEndpoint", "browseId")?.trim().orEmpty()
        val albumTitle = albumRun.string("text")?.trim().orEmpty()
        if (albumId.isNotEmpty() && albumTitle.isNotEmpty()) {
            onAlbum(
                trackId,
                AlbumSummary(
                    id = albumId,
                    title = albumTitle,
                    artworkUrl = resolvedArtworkUrl,
                ),
            )
        }
    }

    return Track(
        id = trackId,
        title = title,
        artists = artists,
        durationMs = durationMs,
        artworkUrl = resolvedArtworkUrl,
        source = YOUTUBE_MUSIC_SOURCE,
    )
}

private fun unlinkedHeaderArtists(runs: List<JsonElement>): List<String> = runs
    .mapNotNull { it.string("text")?.trim() }
    .filter { text ->
        text.isNotEmpty() &&
            text !in SEPARATORS &&
            text.lowercase() !in TYPE_LABELS &&
            !YEAR.matches(text)
    }

private fun unlinkedTrackArtists(runs: List<JsonElement>): List<String> {
    for (run in runs) {
        val text = run.string("text")?.trim().orEmpty()
        if (text.isEmpty() || text in SEPARATORS) continue
        val low = text.lowercase()
        if (low in TYPE_LABELS || parseDuration(text) != null) continue
        if (text.any(Char::isDigit) && COUNT_HINT.containsMatchIn(low)) continue
        return text.split(", ", " & ").map(String::trim).filter(String::isNotEmpty)
    }
    return emptyList()
}

private fun searchShelves(response: JsonObject): List<JsonElement> =
    response.at("continuationContents", "musicShelfContinuation")?.let(::listOf)
        ?: response.array(
            "contents", "tabbedSearchResultsRenderer", "tabs", 0, "tabRenderer", "content",
            "sectionListRenderer", "contents",
        ).mapNotNull { it.at("musicShelfRenderer") }

private fun nextContinuation(shelves: List<JsonElement>): String? =
    shelves.firstNotNullOfOrNull { it.string("continuations", 0, "nextContinuationData", "continuation") }

internal fun sectionListContents(response: JsonObject): List<JsonElement> {
    val tab = response.at(
        "contents", "singleColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content",
    ) ?: response.at(
        "contents", "twoColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content",
    )
    return tab.array("sectionListRenderer", "contents")
}

private fun secondarySectionContents(response: JsonObject): List<JsonElement> =
    response.array(
        "contents", "twoColumnBrowseResultsRenderer", "secondaryContents",
        "sectionListRenderer", "contents",
    )

private fun responsiveHeader(response: JsonObject): JsonElement? {
    for (section in sectionListContents(response)) {
        section.at("musicResponsiveHeaderRenderer")?.let { return it }
        section.at("musicEditablePlaylistDetailHeaderRenderer")?.let { editable ->
            return editable.at("header", "musicResponsiveHeaderRenderer") ?: editable
        }
    }
    return response.at("header", "musicDetailHeaderRenderer")
}

internal fun shelfTitle(shelf: JsonElement): String =
    textOf(shelf.at("title"))
        .ifEmpty { textOf(shelf.at("header", "musicCarouselShelfBasicHeaderRenderer", "title")) }
        .ifEmpty { textOf(shelf.at("header", "gridHeaderRenderer", "title")) }

internal fun browseTarget(element: JsonElement?): Pair<String, String>? {
    val endpoint = element.at("navigationEndpoint") ?: element
    val browse = endpoint.at("browseEndpoint") ?: return null
    val browseId = browse.string("browseId")?.takeIf { it.isNotBlank() } ?: return null
    val pageType = browse.string(
        "browseEndpointContextSupportedConfigs",
        "browseEndpointContextMusicConfig",
        "pageType",
    ) ?: return null
    val kind = when (pageType) {
        PAGE_TYPE_ARTIST -> KIND_ARTIST
        PAGE_TYPE_ALBUM, PAGE_TYPE_AUDIOBOOK -> KIND_ALBUM
        PAGE_TYPE_PLAYLIST -> KIND_PLAYLIST
        else -> return null
    }
    return kind to browseId
}

internal fun findYear(runs: List<JsonElement>): String? =
    runs.firstNotNullOfOrNull { findYearInText(it.string("text")) }

private fun findYearInText(text: String?): String? =
    text?.split("•", "·")?.map(String::trim)?.firstOrNull { YEAR.matches(it) }

internal fun runsText(runs: List<JsonElement>): String =
    runs.joinToString("") { it.string("text").orEmpty() }.trim()

internal fun textOf(textObj: JsonElement?): String =
    textObj.string("simpleText")?.trim() ?: runsText(textObj.array("runs"))

private fun thumbnailsOf(renderer: JsonElement?): List<JsonElement> =
    renderer.array("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")
        .ifEmpty { renderer.array("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails") }
        .ifEmpty { renderer.array("foregroundThumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails") }
        .ifEmpty { renderer.array("thumbnail", "thumbnails") }

internal fun bestArtwork(renderer: JsonElement?, size: Int = ARTWORK_SIZE): String? {
    val thumbnails = thumbnailsOf(renderer)
    if (thumbnails.isEmpty()) return null
    val best = thumbnails.maxByOrNull { (it.at("width") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0 }
        ?: thumbnails.last()
    val raw = best.string("url")?.takeIf { it.isNotEmpty() } ?: return null
    val normalized = if (raw.startsWith("//")) "https:$raw" else raw
    return resizeGoogleArtwork(normalized, size)
}
