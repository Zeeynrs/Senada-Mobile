package senada.catalog.youtube

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import senada.model.Track

/** Kandidat hasil pencarian YouTube Music beserta metadata tipe unggahan dan album untuk skor pencocokan. */
internal data class MatchCandidate(
    val track: Track,
    val videoType: String? = null,
    val album: String? = null,
)

internal fun interface CandidateSearcher {
    suspend fun search(query: String, includeVideos: Boolean): List<MatchCandidate>
}

/** Mencari kandidat lagu atau video lewat Innertube untuk pencocokan playlist Spotify. */
internal class InnertubeCandidateSearcher(
    private val innertube: InnertubeClient,
) : CandidateSearcher {
    override suspend fun search(query: String, includeVideos: Boolean): List<MatchCandidate> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val response = if (includeVideos) innertube.searchVideos(trimmed) else innertube.search(trimmed)
        return parseSearchCandidates(response)
    }
}

internal fun parseSearchCandidates(response: JsonObject): List<MatchCandidate> {
    val shelves = response.at("continuationContents", "musicShelfContinuation")?.let(::listOf)
        ?: response.array(
            "contents", "tabbedSearchResultsRenderer", "tabs", 0, "tabRenderer", "content",
            "sectionListRenderer", "contents",
        ).mapNotNull { it.at("musicShelfRenderer") }

    return shelves.flatMap { shelf ->
        shelf.array("contents").mapNotNull { item ->
            item.at("musicResponsiveListItemRenderer")?.let(::parseCandidate)
        }
    }.distinctBy { it.track.id }
}

private fun parseCandidate(item: JsonElement): MatchCandidate? {
    val titleRuns = item.array("flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text", "runs")
    val videoId = item.string("playlistItemData", "videoId")
        ?: titleRuns.firstNotNullOfOrNull { it.string("navigationEndpoint", "watchEndpoint", "videoId") }
        ?: return null
    val title = titleRuns.joinToString("") { it.string("text").orEmpty() }.trim()
    if (title.isEmpty()) return null

    val videoType = titleRuns.firstNotNullOfOrNull {
        it.string(
            "navigationEndpoint", "watchEndpoint", "watchEndpointMusicSupportedConfigs",
            "watchEndpointMusicConfig", "musicVideoType",
        )
    }

    val details = item.array("flexColumns", 1, "musicResponsiveListItemFlexColumnRenderer", "text", "runs")
    val texts = details.map { it.string("text").orEmpty() }
    val artists = details.filter { candidatePageType(it) == "MUSIC_PAGE_TYPE_ARTIST" }
        .map { it.string("text").orEmpty() }
        .ifEmpty {
            texts.takeWhile { it != SEPARATOR }.joinToString("").split(", ", " & ").map(String::trim)
        }
        .filter { it.isNotEmpty() }

    val album = details.firstOrNull { candidatePageType(it) == "MUSIC_PAGE_TYPE_ALBUM" }
        ?.string("text")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    val durationMs = texts.lastOrNull { parseDuration(it.trim()) != null }?.let(::parseDuration)
    val rawArtwork = item.array("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")
        .lastOrNull()?.string("url")
    val artworkUrl = rawArtwork?.let { url ->
        val normalized = if (url.startsWith("//")) "https:$url" else url
        resizeGoogleArtwork(normalized, ARTWORK_SIZE)
    }

    return MatchCandidate(
        track = Track(
            id = youTubeTrackId(videoId),
            title = title,
            artists = artists,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            source = YOUTUBE_MUSIC_SOURCE,
        ),
        videoType = videoType,
        album = album,
    )
}

private fun candidatePageType(run: JsonElement): String? = run.string(
    "navigationEndpoint", "browseEndpoint", "browseEndpointContextSupportedConfigs",
    "browseEndpointContextMusicConfig", "pageType",
)

private const val SEPARATOR = " • "
