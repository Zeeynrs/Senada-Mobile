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
import senada.catalog.ReleaseSection
import senada.model.Track

private const val PAGE_TYPE_ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
private const val PAGE_TYPE_ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
private const val PAGE_TYPE_AUDIOBOOK = "MUSIC_PAGE_TYPE_AUDIOBOOK"
private const val PAGE_TYPE_PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"

private const val KIND_ARTIST = "artist"
private const val KIND_ALBUM = "album"
private const val KIND_PLAYLIST = "playlist"

private val NON_ARTIST_PAGE_TYPES = setOf(PAGE_TYPE_ALBUM, PAGE_TYPE_AUDIOBOOK, PAGE_TYPE_PLAYLIST)
private val TYPE_LABELS = setOf(
    "lagu", "song", "video", "episode", "episode podcast",
    "single", "album", "ep", "playlist", "artis", "artist",
)
private val SEPARATORS = setOf("•", ",", "&", "dan", "and")
private val YEAR = Regex("^\\d{4}$")
private val COUNT_HINT = Regex("(x|jt|rb\\.?|views|plays|pemutaran|ditonton|pendengar|audiens|subscriber)")

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

/** Membaca halaman artis: nama, foto, lagu teratas, serta rak album/single. */
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
    val releases = mutableListOf<ReleaseSection>()

    for (section in sectionListContents(response)) {
        val shelf = section.at("musicShelfRenderer")
        if (shelf != null && topSongs.isEmpty()) {
            topSongs = shelf.array("contents")
                .mapNotNull {
                    it.at("musicResponsiveListItemRenderer")?.let { row ->
                        parseBrowseTrack(row, fallbackArtists, onArtistId = onArtistId)
                    }
                }
                .distinctBy { it.id }
            if (name.isNotEmpty()) {
                topSongs.forEach { song -> onArtistId(song.id, name, channelId) }
            }
            continue
        }
        val carousel = section.at("musicCarouselShelfRenderer") ?: continue
        val cards = carousel.array("contents")
            .mapNotNull { it.at("musicTwoRowItemRenderer")?.let(::parseAlbumCard) }
            .distinctBy { it.id }
        if (cards.isNotEmpty()) {
            val sectionTitle = shelfTitle(carousel).ifEmpty { "Album & Single" }
            releases += ReleaseSection(title = sectionTitle, items = cards)
        }
    }

    if (name.isEmpty() && topSongs.isEmpty() && releases.isEmpty()) {
        throw CatalogException(Kind.Unreadable, "Halaman artis tidak memuat data yang dapat dibaca")
    }
    return ArtistDetail(
        id = channelId,
        name = name,
        artworkUrl = artworkUrl,
        monthlyListeners = monthlyListeners,
        topSongs = topSongs,
        releases = releases,
    )
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

private fun parseArtistSummary(item: JsonElement): ArtistSummary? {
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

private fun parseAlbumSummary(item: JsonElement): AlbumSummary? {
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

private fun parseAlbumCard(item: JsonElement): AlbumSummary? {
    val target = browseTarget(item)
        ?: item.array("title", "runs").firstNotNullOfOrNull(::browseTarget)
        ?: return null
    if (target.first != KIND_ALBUM) return null
    val title = textOf(item.at("title"))
    if (title.isEmpty()) return null
    val subRuns = item.array("subtitle", "runs")
    return AlbumSummary(
        id = target.second,
        title = title,
        subtitle = textOf(item.at("subtitle")).takeIf { it.isNotEmpty() },
        year = findYear(subRuns),
        artworkUrl = bestArtwork(item),
    )
}

private fun parseBrowseTrack(
    item: JsonElement,
    fallbackArtists: List<String> = emptyList(),
    fallbackArtworkUrl: String? = null,
    onArtistId: (trackId: String, artistName: String, channelId: String) -> Unit = { _, _, _ -> },
): Track? {
    val titleRuns = columnRuns(item, 0)
    val videoId = item.string("playlistItemData", "videoId")
        ?: titleRuns.firstNotNullOfOrNull { it.string("navigationEndpoint", "watchEndpoint", "videoId") }
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

    return Track(
        id = trackId,
        title = title,
        artists = artists,
        durationMs = durationMs,
        artworkUrl = bestArtwork(item) ?: fallbackArtworkUrl,
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

private fun sectionListContents(response: JsonObject): List<JsonElement> {
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

private fun shelfTitle(shelf: JsonElement): String =
    textOf(shelf.at("title")).ifEmpty {
        textOf(shelf.at("header", "musicCarouselShelfBasicHeaderRenderer", "title"))
    }

private fun browseTarget(element: JsonElement?): Pair<String, String>? {
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

private fun findYear(runs: List<JsonElement>): String? =
    runs.mapNotNull { it.string("text")?.trim() }.firstOrNull { YEAR.matches(it) }

private fun runsText(runs: List<JsonElement>): String =
    runs.joinToString("") { it.string("text").orEmpty() }.trim()

private fun textOf(textObj: JsonElement?): String =
    textObj.string("simpleText")?.trim() ?: runsText(textObj.array("runs"))

private fun thumbnailsOf(renderer: JsonElement?): List<JsonElement> =
    renderer.array("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")
        .ifEmpty { renderer.array("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails") }
        .ifEmpty { renderer.array("foregroundThumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails") }
        .ifEmpty { renderer.array("thumbnail", "thumbnails") }

private fun bestArtwork(renderer: JsonElement?, size: Int = ARTWORK_SIZE): String? {
    val thumbnails = thumbnailsOf(renderer)
    if (thumbnails.isEmpty()) return null
    val best = thumbnails.maxByOrNull { (it.at("width") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0 }
        ?: thumbnails.last()
    val raw = best.string("url")?.takeIf { it.isNotEmpty() } ?: return null
    val normalized = if (raw.startsWith("//")) "https:$raw" else raw
    return resizeGoogleArtwork(normalized, size)
}
