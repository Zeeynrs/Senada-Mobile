package senada.catalog.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import senada.catalog.CatalogException
import senada.catalog.CatalogException.Kind
import senada.catalog.PlaylistImportException
import senada.model.Track

/** Hasil pembacaan playlist YouTube Music beserta judul lagu yang tidak tersedia. */
internal data class YouTubeMusicPlaylist(
    val id: String,
    val name: String,
    val tracks: List<Track>,
    val skippedTitles: List<String> = emptyList(),
    val truncated: Boolean = false,
)

internal interface YouTubePlaylistSource {
    suspend fun fetchPlaylist(
        playlistId: String,
        limit: Int = YouTubeMusicPlaylists.DEFAULT_PLAYLIST_LIMIT,
    ): YouTubeMusicPlaylist
}

/** Membaca playlist publik atau tidak terdaftar dari Innertube `browse` (`VL<playlistId>`). */
internal class YouTubeMusicPlaylists(
    private val innertube: InnertubeClient,
) : YouTubePlaylistSource {
    override suspend fun fetchPlaylist(playlistId: String, limit: Int): YouTubeMusicPlaylist {
        val cleanId = playlistId.removePrefix("VL").ifBlank { playlistId }
        val browseId = "VL$cleanId"
        val firstResponse = try {
            innertube.browse(browseId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CatalogException) {
            throw mapBrowseError(e)
        }

        if (firstResponse.at("error") != null) {
            throw PlaylistImportException(UNREACHABLE_PLAYLIST)
        }

        val firstPage = parsePlaylistFirstPage(cleanId, firstResponse)
        val tracks = firstPage.tracks.toMutableList()
        val skippedTitles = firstPage.skippedTitles.toMutableList()
        val seenTrackIds = tracks.mapTo(HashSet()) { it.id }
        val seenTokens = HashSet<String>()

        var modernToken = firstPage.modernContinuationToken
        var legacyToken = firstPage.legacyContinuationToken
        var pages = 0
        var truncated = false

        while ((modernToken != null || legacyToken != null) && tracks.size < limit && pages < MAX_CONTINUATION_PAGES) {
            val currentToken = modernToken ?: legacyToken ?: break
            if (!seenTokens.add(currentToken)) break
            pages++

            val moreResponse = try {
                innertube.browseContinuation(currentToken)
            } catch (e: CancellationException) {
                throw e
            } catch (_: CatalogException) {
                // Halaman pertama sudah berhasil; pertahankan lagu yang sudah terbaca.
                truncated = true
                break
            }

            val page = if (modernToken != null) {
                parsePlaylistContinuationPage(moreResponse, modern = true)
            } else {
                parsePlaylistContinuationPage(moreResponse, modern = false)
            }

            val allKnown = page.tracks.isNotEmpty() && page.tracks.all { it.id in seenTrackIds } && page.skippedTitles.isEmpty()
            if ((page.tracks.isEmpty() && page.skippedTitles.isEmpty() && page.modernContinuationToken == null && page.legacyContinuationToken == null) || allKnown) {
                break
            }

            tracks += page.tracks
            skippedTitles += page.skippedTitles
            page.tracks.forEach { seenTrackIds += it.id }
            modernToken = page.modernContinuationToken
            legacyToken = page.legacyContinuationToken
        }

        val hasMoreRemaining = (modernToken != null || legacyToken != null) || tracks.size > limit
        val finalTracks = if (tracks.size > limit) tracks.take(limit) else tracks
        if (finalTracks.isEmpty()) {
            throw PlaylistImportException(UNREACHABLE_PLAYLIST)
        }

        return YouTubeMusicPlaylist(
            id = cleanId,
            name = firstPage.name,
            tracks = finalTracks,
            skippedTitles = skippedTitles,
            truncated = truncated || hasMoreRemaining,
        )
    }

    private fun mapBrowseError(error: CatalogException): PlaylistImportException {
        if (error.kind == Kind.RateLimited) {
            return PlaylistImportException(error.userMessage, error)
        }
        val msg = error.message.orEmpty()
        if (error.kind == Kind.Network && (
                "HTTP 400" in msg || "HTTP 403" in msg || "HTTP 404" in msg
            )
        ) {
            return PlaylistImportException(UNREACHABLE_PLAYLIST, error)
        }
        return PlaylistImportException(error.userMessage, error)
    }

    companion object {
        const val DEFAULT_PLAYLIST_LIMIT = 500
        private const val MAX_CONTINUATION_PAGES = 20
        internal const val UNREACHABLE_PLAYLIST =
            "YouTube Music tidak dapat membuka playlist ini. Pastikan playlist bersifat publik atau tidak terdaftar."
    }
}

internal data class ParsedPlaylistFirstPage(
    val id: String,
    val name: String,
    val tracks: List<Track>,
    val skippedTitles: List<String>,
    val modernContinuationToken: String?,
    val legacyContinuationToken: String?,
)

internal data class ParsedPlaylistContinuation(
    val tracks: List<Track>,
    val skippedTitles: List<String>,
    val modernContinuationToken: String?,
    val legacyContinuationToken: String?,
)

private sealed interface ParsedPlaylistItem {
    data class Valid(val track: Track) : ParsedPlaylistItem
    data class Unavailable(val title: String) : ParsedPlaylistItem
}

internal fun parsePlaylistFirstPage(playlistId: String, response: JsonObject): ParsedPlaylistFirstPage {
    val header = findPlaylistHeader(response)
    val name = extractText(header.at("title")).trim().ifEmpty { DEFAULT_PLAYLIST_NAME }

    val candidateSections = response.array(
        "contents", "twoColumnBrowseResultsRenderer", "secondaryContents",
        "sectionListRenderer", "contents",
    ).ifEmpty { sectionListContents(response) }

    val shelf = candidateSections.firstNotNullOfOrNull { section ->
        section.at("musicPlaylistShelfRenderer") ?: section.at("musicShelfRenderer")
    }
    val items = shelf?.array("contents").orEmpty()
    val (tracks, skipped, itemContinuation) = parsePlaylistItems(items)
    val legacyContinuation = shelf?.string("continuations", 0, "nextContinuationData", "continuation")

    return ParsedPlaylistFirstPage(
        id = playlistId,
        name = name,
        tracks = tracks,
        skippedTitles = skipped,
        modernContinuationToken = itemContinuation,
        legacyContinuationToken = if (itemContinuation == null) legacyContinuation else null,
    )
}

internal fun parsePlaylistContinuationPage(
    response: JsonObject,
    modern: Boolean,
): ParsedPlaylistContinuation {
    if (modern) {
        val actionItems = response.array("onResponseReceivedActions")
            .firstNotNullOfOrNull { action ->
                action.at("appendContinuationItemsAction", "continuationItems")
                    ?.let { arrayOrEmpty(it) }
            }
        if (actionItems != null) {
            val (tracks, skipped, nextModern) = parsePlaylistItems(actionItems)
            return ParsedPlaylistContinuation(tracks, skipped, nextModern, null)
        }
    }

    val cont = response.at("continuationContents", "musicPlaylistShelfContinuation")
        ?: response.at("continuationContents", "musicShelfContinuation")
    val items = cont?.array("contents").orEmpty()
    val (tracks, skipped, itemContinuation) = parsePlaylistItems(items)
    val legacyContinuation = cont?.string("continuations", 0, "nextContinuationData", "continuation")

    return ParsedPlaylistContinuation(
        tracks = tracks,
        skippedTitles = skipped,
        modernContinuationToken = itemContinuation,
        legacyContinuationToken = if (itemContinuation == null) legacyContinuation else null,
    )
}

private fun parsePlaylistItems(items: List<JsonElement>): Triple<List<Track>, List<String>, String?> {
    val tracks = mutableListOf<Track>()
    val skipped = mutableListOf<String>()
    var continuationToken: String? = null

    for (item in items) {
        val token = item.string("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token")
        if (token != null) {
            continuationToken = token
            continue
        }
        val renderer = item.at("musicResponsiveListItemRenderer") ?: continue
        when (val parsed = parsePlaylistTrack(renderer)) {
            is ParsedPlaylistItem.Valid -> tracks += parsed.track
            is ParsedPlaylistItem.Unavailable -> skipped += parsed.title
            null -> Unit
        }
    }
    return Triple(tracks, skipped, continuationToken)
}

private fun parsePlaylistTrack(renderer: JsonElement): ParsedPlaylistItem? {
    val flexCols = renderer.array("flexColumns")
        .map { it.at("musicResponsiveListItemFlexColumnRenderer", "text") }
    val fixedCols = renderer.array("fixedColumns")
        .map { it.at("musicResponsiveListItemFixedColumnRenderer", "text") }

    val titleObj = flexCols.firstOrNull()
    val title = extractText(titleObj).trim()
    if (title.isEmpty()) return null

    val titleRuns = titleObj.array("runs")
    val videoId = renderer.string("playlistItemData", "videoId")?.takeIf { it.isNotBlank() }
        ?: titleRuns.firstNotNullOfOrNull { it.string("navigationEndpoint", "watchEndpoint", "videoId") }
            ?.takeIf { it.isNotBlank() }
        ?: renderer.string(
            "overlay", "musicItemThumbnailOverlayRenderer", "content",
            "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint", "videoId",
        )?.takeIf { it.isNotBlank() }

    if (videoId == null) {
        // Baris tanpa videoId di dalam playlist menandakan video dihapus atau tidak tersedia.
        return if (renderer.at("navigationEndpoint") == null) ParsedPlaylistItem.Unavailable(title) else null
    }

    val artists = extractPlaylistArtists(flexCols.drop(1))
    val durationMs = extractPlaylistDuration(flexCols.drop(1) + fixedCols)

    return ParsedPlaylistItem.Valid(
        Track(
            id = youTubeTrackId(videoId),
            title = title,
            artists = artists,
            durationMs = durationMs,
            artworkUrl = playlistArtwork(renderer),
            source = YOUTUBE_MUSIC_SOURCE,
        ),
    )
}

private fun extractPlaylistArtists(detailCols: List<JsonElement?>): List<String> {
    val linked = mutableListOf<String>()
    val plain = mutableListOf<String>()
    for (col in detailCols) {
        val runs = col.array("runs")
        if (runs.isEmpty()) {
            val simple = col.string("simpleText")?.trim().orEmpty()
            if (simple.isNotEmpty()) plain += simple
            continue
        }
        for (run in runs) {
            val text = run.string("text")?.trim().orEmpty()
            if (text.isEmpty() || text in RUN_SEPARATORS) continue
            val page = playlistPageType(run)
            when {
                page == "MUSIC_PAGE_TYPE_ARTIST" -> linked += text
                page == "MUSIC_PAGE_TYPE_ALBUM" || page == "MUSIC_PAGE_TYPE_PLAYLIST" ||
                    page == "MUSIC_PAGE_TYPE_AUDIOBOOK" -> Unit
                run.at("navigationEndpoint", "browseEndpoint") != null -> linked += text
                parseDuration(text) != null -> Unit
                else -> plain += text
            }
        }
    }
    if (linked.isNotEmpty()) return linked.distinct()

    for (candidate in plain) {
        val low = candidate.lowercase()
        if (low in TYPE_LABELS || VIEW_COUNT_REGEX.containsMatchIn(low)) continue
        val parts = candidate.substringBefore(SEPARATOR)
            .split(", ", " & ", " dan ", " and ")
            .map(String::trim)
            .filter { it.isNotEmpty() }
        if (parts.isNotEmpty()) return parts
    }
    return emptyList()
}

private fun extractPlaylistDuration(columns: List<JsonElement?>): Long? {
    for (col in columns) {
        val runs = col.array("runs")
        if (runs.isNotEmpty()) {
            val found = runs.asReversed().firstNotNullOfOrNull { run ->
                run.string("text")?.let(::parseDuration)
            }
            if (found != null) return found
        }
        val simple = col.string("simpleText")?.let(::parseDuration)
        if (simple != null) return simple
    }
    return null
}

private fun findPlaylistHeader(response: JsonObject): JsonElement? {
    for (section in sectionListContents(response)) {
        section.at("musicResponsiveHeaderRenderer")?.let { return it }
        section.at("musicEditablePlaylistDetailHeaderRenderer")?.let { editable ->
            return editable.at("header", "musicResponsiveHeaderRenderer") ?: editable
        }
    }
    return response.at("header", "musicDetailHeaderRenderer")
        ?: response.at("header", "musicResponsiveHeaderRenderer")
}

private fun sectionListContents(response: JsonObject): List<JsonElement> {
    val tabContent = response.at(
        "contents", "singleColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content",
    ) ?: response.at(
        "contents", "twoColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content",
    )
    return tabContent.array("sectionListRenderer", "contents")
}

private fun extractText(textObj: JsonElement?): String {
    if (textObj == null) return ""
    textObj.string("simpleText")?.let { return it }
    return textObj.array("runs").joinToString("") { it.string("text").orEmpty() }
}

private fun playlistPageType(run: JsonElement): String? = run.string(
    "navigationEndpoint", "browseEndpoint", "browseEndpointContextSupportedConfigs",
    "browseEndpointContextMusicConfig", "pageType",
)

private fun playlistArtwork(renderer: JsonElement): String? {
    val thumbnails = renderer.array("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")
    val rawUrl = thumbnails.lastOrNull()?.string("url") ?: return null
    val url = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
    return resizeGoogleArtwork(url, ARTWORK_SIZE)
}

private fun arrayOrEmpty(element: JsonElement): List<JsonElement> =
    element as? JsonArray ?: emptyList()

private const val DEFAULT_PLAYLIST_NAME = "Playlist YouTube Music"
private const val SEPARATOR = " • "
private val RUN_SEPARATORS = setOf("•", ",", "&", "dan", "and")
private val TYPE_LABELS = setOf(
    "lagu", "song", "video", "episode", "episode podcast", "single", "album", "ep", "playlist", "artis", "artist",
)
private val VIEW_COUNT_REGEX = Regex("""\d.*(?:x|jt|rb|views|plays|pemutaran|ditonton)""")
