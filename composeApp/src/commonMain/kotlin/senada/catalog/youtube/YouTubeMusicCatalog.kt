package senada.catalog.youtube

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import senada.catalog.AlbumDetail
import senada.catalog.AlbumPage
import senada.catalog.ArtistDetail
import senada.catalog.ArtistPage
import senada.catalog.CatalogBrowser
import senada.catalog.TrackPage
import senada.model.Track

internal const val YOUTUBE_MUSIC_SOURCE = "ytmusic"
private const val TRACK_ID_PREFIX = "yt:track:"

/** ID yang sama dengan Senada desktop, sehingga library dapat dipindahkan lewat backup. */
internal fun youTubeTrackId(videoId: String) = "$TRACK_ID_PREFIX$videoId"

internal fun videoIdOf(track: Track): String? = track.id.removePrefix(TRACK_ID_PREFIX).takeIf { it != track.id }

internal class YouTubeMusicCatalog(private val innertube: InnertubeClient) : CatalogBrowser {
    override suspend fun search(query: String, pageToken: String?): TrackPage {
        if (pageToken != null) return parseSearchPage(innertube.searchContinuation(pageToken))
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return TrackPage(emptyList())
        return parseSearchPage(innertube.search(trimmed))
    }

    override suspend fun searchArtists(query: String, pageToken: String?): ArtistPage {
        if (pageToken != null) return parseArtistSearchPage(innertube.searchContinuation(pageToken))
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return ArtistPage(emptyList())
        return parseArtistSearchPage(innertube.search(trimmed, InnertubeClient.ARTISTS_FILTER))
    }

    override suspend fun searchAlbums(query: String, pageToken: String?): AlbumPage {
        if (pageToken != null) return parseAlbumSearchPage(innertube.searchContinuation(pageToken))
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return AlbumPage(emptyList())
        return parseAlbumSearchPage(innertube.search(trimmed, InnertubeClient.ALBUMS_FILTER))
    }

    override suspend fun artist(id: String): ArtistDetail =
        parseArtistPage(id, innertube.browse(id))

    override suspend fun album(id: String): AlbumDetail =
        parseAlbumPage(id, innertube.browse(id))
}

/** Membaca halaman pertama pencarian lagu atau halaman lanjutannya. */
internal fun parseSearchPage(response: JsonObject): TrackPage {
    val shelves = response.at("continuationContents", "musicShelfContinuation")?.let(::listOf)
        ?: response.array(
            "contents", "tabbedSearchResultsRenderer", "tabs", 0, "tabRenderer", "content",
            "sectionListRenderer", "contents",
        ).mapNotNull { it.at("musicShelfRenderer") }

    val tracks = shelves.flatMap { shelf ->
        shelf.array("contents").mapNotNull { item -> item.at("musicResponsiveListItemRenderer")?.let(::parseTrack) }
    }
    val next = shelves.firstNotNullOfOrNull { it.string("continuations", 0, "nextContinuationData", "continuation") }
    return TrackPage(tracks.distinctBy { it.id }, next)
}

private fun parseTrack(item: JsonElement): Track? {
    val titleRuns = columnRuns(item, 0)
    val videoId = item.string("playlistItemData", "videoId")
        ?: titleRuns.firstNotNullOfOrNull { it.string("navigationEndpoint", "watchEndpoint", "videoId") }
        ?: return null
    val title = titleRuns.joinToString("") { it.string("text").orEmpty() }.trim()
    if (title.isEmpty()) return null

    val details = columnRuns(item, 1)
    val texts = details.map { it.string("text").orEmpty() }
    val artists = details.filter { pageType(it) == "MUSIC_PAGE_TYPE_ARTIST" }.map { it.string("text").orEmpty() }
        .ifEmpty {
            // Artis tanpa tautan, misalnya "Various Artists": ambil teks sebelum pemisah pertama.
            texts.takeWhile { it != SEPARATOR }.joinToString("").split(", ", " & ").map(String::trim)
        }
        .filter { it.isNotEmpty() }

    return Track(
        id = youTubeTrackId(videoId),
        title = title,
        artists = artists,
        durationMs = texts.lastOrNull { DURATION.matches(it.trim()) }?.let(::parseDuration),
        artworkUrl = artwork(item),
        source = YOUTUBE_MUSIC_SOURCE,
    )
}

internal fun columnRuns(item: JsonElement, column: Int): List<JsonElement> =
    item.array("flexColumns", column, "musicResponsiveListItemFlexColumnRenderer", "text", "runs")

internal fun pageType(run: JsonElement): String? = run.string(
    "navigationEndpoint", "browseEndpoint", "browseEndpointContextSupportedConfigs",
    "browseEndpointContextMusicConfig", "pageType",
)

private fun artwork(item: JsonElement): String? {
    val thumbnails = item.array("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")
    val url = thumbnails.lastOrNull()?.string("url") ?: return null
    return resizeGoogleArtwork(url, ARTWORK_SIZE)
}

private val GOOGLE_ARTWORK_HOST = Regex(
    """^https?://(?:[a-z0-9-]+\.)*(?:googleusercontent\.com|ggpht\.com)/""", RegexOption.IGNORE_CASE,
)
private val ARTWORK_DIMENSIONS = Regex("""=w[0-9]+-h[0-9]+(?=-|$)""")

/** Hanya ubah ukuran pada nama gambar; pertahankan opsi potong, kueri, dan fragmen URL. */
internal fun resizeGoogleArtwork(url: String, size: Int): String {
    if (!GOOGLE_ARTWORK_HOST.containsMatchIn(url)) return url
    val end = url.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) url.length else it }
    val start = url.lastIndexOf('/', end) + 1
    val name = url.substring(start, end)
    val dimensions = "=w$size-h$size"
    val resized = when {
        ARTWORK_DIMENSIONS.containsMatchIn(name) -> ARTWORK_DIMENSIONS.replaceFirst(name, dimensions)
        name.isNotEmpty() && '=' !in name -> name + dimensions
        else -> return url
    }
    return url.replaceRange(start, end, resized)
}

private const val SEPARATOR = " • "
internal const val ARTWORK_SIZE = 544

// Bahasa Indonesia memakai titik ("3.33"), bahasa lain titik dua ("3:33").
private val DURATION = Regex("^(\\d+)[:.](\\d{2})(?:[:.](\\d{2}))?$")

internal fun parseDuration(text: String): Long? {
    val (a, b, c) = DURATION.matchEntire(text.trim())?.destructured ?: return null
    val seconds = if (c.isEmpty()) a.toLong() * 60 + b.toLong() else a.toLong() * 3600 + b.toLong() * 60 + c.toLong()
    return seconds * 1_000
}
