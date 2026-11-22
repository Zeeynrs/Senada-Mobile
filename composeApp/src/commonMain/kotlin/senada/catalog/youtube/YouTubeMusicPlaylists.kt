package senada.catalog.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import senada.catalog.CatalogException
import senada.catalog.CatalogException.Kind
import senada.catalog.PlaylistDetail
import senada.catalog.PlaylistImportException
import senada.catalog.isYouTubeMixPlaylist
import senada.model.Track

/** Hasil pembacaan playlist YouTube Music beserta judul lagu yang tidak tersedia. */
internal data class YouTubeMusicPlaylist(
    val id: String,
    val name: String,
    val tracks: List<Track>,
    val skippedTitles: List<String> = emptyList(),
    val truncated: Boolean = false,
    val owner: String? = null,
    val songCountText: String? = null,
    val artworkUrl: String? = null,
)

internal interface YouTubePlaylistSource {
    suspend fun fetchPlaylist(
        playlistId: String,
        limit: Int = YouTubeMusicPlaylists.DEFAULT_PLAYLIST_LIMIT,
    ): YouTubeMusicPlaylist
}

/** Membaca playlist publik atau tidak terdaftar dari Innertube `browse` (`VL<playlistId>`) maupun mix via `next`. */
internal class YouTubeMusicPlaylists(
    private val innertube: InnertubeClient,
) : YouTubePlaylistSource {
    override suspend fun fetchPlaylist(playlistId: String, limit: Int): YouTubeMusicPlaylist =
        fetchInternal(playlistId = playlistId, limit = limit, allowMixFallback = false)

    /** Membuka isi halaman playlist beserta metadata pemilik, jumlah lagu, dan dukungan mix/radio. */
    suspend fun fetchPlaylistDetail(
        playlistId: String,
        limit: Int = DEFAULT_PLAYLIST_LIMIT,
    ): PlaylistDetail {
        val result = fetchInternal(playlistId = playlistId, limit = limit, allowMixFallback = true)
        return PlaylistDetail(
            id = result.id,
            title = result.name,
            owner = result.owner,
            songCountText = result.songCountText,
            artworkUrl = result.artworkUrl ?: result.tracks.firstOrNull()?.artworkUrl,
            tracks = result.tracks,
            truncated = result.truncated,
        )
    }

    private suspend fun fetchInternal(
        playlistId: String,
        limit: Int,
        allowMixFallback: Boolean,
    ): YouTubeMusicPlaylist {
        val cleanId = playlistId.removePrefix("VL").ifBlank { playlistId }
        val browseId = "VL$cleanId"
        val isMix = allowMixFallback && isYouTubeMixPlaylist(cleanId)

        var browseError: PlaylistImportException? = null
        val firstResponse = try {
            innertube.browse(browseId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CatalogException) {
            val mapped = mapBrowseError(e)
            if (isMix && e.kind != Kind.RateLimited) {
                browseError = mapped
                null
            } else {
                throw mapped
            }
        }

        if (firstResponse == null || firstResponse.at("error") != null) {
            if (isMix) {
                return fetchMixFromNext(cleanId, headerMeta = null, limit = limit)
                    ?: throw (browseError ?: PlaylistImportException(UNREACHABLE_PLAYLIST))
            }
            throw (browseError ?: PlaylistImportException(UNREACHABLE_PLAYLIST))
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
            if (isMix) {
                return fetchMixFromNext(cleanId, headerMeta = firstPage, limit = limit)
                    ?: throw PlaylistImportException(UNREACHABLE_PLAYLIST)
            }
            if (!allowMixFallback || !firstPage.hasHeader) {
                throw PlaylistImportException(UNREACHABLE_PLAYLIST)
            }
        }

        return YouTubeMusicPlaylist(
            id = cleanId,
            name = firstPage.name,
            tracks = finalTracks,
            skippedTitles = skippedTitles,
            truncated = truncated || hasMoreRemaining,
            owner = firstPage.owner,
            songCountText = firstPage.songCountText,
            artworkUrl = firstPage.artworkUrl ?: finalTracks.firstOrNull()?.artworkUrl,
        )
    }

    private suspend fun fetchMixFromNext(
        playlistId: String,
        headerMeta: ParsedPlaylistFirstPage?,
        limit: Int,
    ): YouTubeMusicPlaylist? {
        val nextResponse = try {
            innertube.nextPlaylist(playlistId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CatalogException) {
            throw mapBrowseError(e)
        }
        val parsed = parseMixNextResponse(playlistId, nextResponse) ?: return null
        val boundedTracks = if (parsed.tracks.size > limit) parsed.tracks.take(limit) else parsed.tracks
        val title = headerMeta?.takeIf { it.hasHeader && it.name != DEFAULT_PLAYLIST_NAME }?.name ?: parsed.name
        return parsed.copy(
            name = title,
            owner = headerMeta?.owner ?: parsed.owner,
            songCountText = headerMeta?.songCountText ?: parsed.songCountText,
            artworkUrl = headerMeta?.artworkUrl ?: boundedTracks.firstOrNull()?.artworkUrl,
            tracks = boundedTracks,
            truncated = parsed.tracks.size > limit,
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
    val owner: String? = null,
    val songCountText: String? = null,
    val artworkUrl: String? = null,
    val hasHeader: Boolean = false,
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
    val owner = extractHeaderOwner(header)
    val songCountText = extractHeaderSongCount(header)
    val headerArt = headerArtwork(header)

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
        owner = owner,
        songCountText = songCountText,
        artworkUrl = headerArt ?: tracks.firstOrNull()?.artworkUrl,
        hasHeader = header != null,
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

private fun extractHeaderOwner(header: JsonElement?): String? {
    if (header == null) return null
    header.string("facepile", "avatarStackViewModel", "text", "content")
        ?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    extractText(header.at("straplineTextOne")).trim().takeIf { it.isNotEmpty() }?.let { return it }
    for (run in header.at("subtitle").array("runs")) {
        if (run.at("navigationEndpoint", "browseEndpoint") != null) {
            run.string("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
    }
    return null
}

private fun extractHeaderSongCount(header: JsonElement?): String? {
    if (header == null) return null
    val secondSubtitle = header.at("secondSubtitle") ?: return null
    for (run in secondSubtitle.array("runs")) {
        val text = run.string("text")?.trim().orEmpty()
        if (text.isNotEmpty() && SONG_COUNT_REGEX.containsMatchIn(text)) {
            return text
        }
    }
    val full = extractText(secondSubtitle).trim()
    if (full.isNotEmpty()) {
        full.split(SEPARATOR).map(String::trim)
            .firstOrNull { SONG_COUNT_REGEX.containsMatchIn(it) }
            ?.let { return it }
    }
    return null
}

private fun headerArtwork(header: JsonElement?): String? {
    if (header == null) return null
    val thumbnails = header.array("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")
        .ifEmpty { header.array("thumbnail", "croppedSquareThumbnailRenderer", "thumbnail", "thumbnails") }
    val rawUrl = thumbnails.lastOrNull()?.string("url") ?: return null
    val url = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
    return resizeGoogleArtwork(url, ARTWORK_SIZE)
}

internal fun parseMixNextResponse(playlistId: String, response: JsonObject): YouTubeMusicPlaylist? {
    val tabs = response.array(
        "contents", "singleColumnMusicWatchNextResultsRenderer", "tabbedRenderer",
        "watchNextTabbedResultsRenderer", "tabs",
    )
    val panel = tabs.firstNotNullOfOrNull { tab ->
        tab.at("tabRenderer", "content", "musicQueueRenderer", "content", "playlistPanelRenderer")
    } ?: return null

    val tracks = mutableListOf<Track>()
    for (item in panel.array("contents")) {
        val renderer = item.at("playlistPanelVideoRenderer") ?: continue
        parseMixPanelTrack(renderer)?.let { tracks += it }
    }
    if (tracks.isEmpty()) return null

    val title = panel.string("title")?.trim()?.takeIf { it.isNotEmpty() }
        ?: extractText(panel.at("title")).trim().takeIf { it.isNotEmpty() }
        ?: DEFAULT_MIX_NAME

    return YouTubeMusicPlaylist(
        id = playlistId,
        name = title,
        tracks = tracks,
        songCountText = "${tracks.size} lagu",
        artworkUrl = tracks.firstOrNull()?.artworkUrl,
    )
}

private fun parseMixPanelTrack(renderer: JsonElement): Track? {
    val videoId = renderer.string("videoId")?.takeIf { it.isNotBlank() }
        ?: renderer.string("navigationEndpoint", "watchEndpoint", "videoId")?.takeIf { it.isNotBlank() }
        ?: return null
    val title = extractText(renderer.at("title")).trim()
    if (title.isEmpty()) return null

    val longRuns = renderer.array("longBylineText", "runs")
    val linkedArtists = longRuns.mapNotNull { run ->
        val text = run.string("text")?.trim().orEmpty()
        if (text.isEmpty() || text in RUN_SEPARATORS) return@mapNotNull null
        if (playlistPageType(run) == "MUSIC_PAGE_TYPE_ARTIST") text else null
    }.distinct()

    val artists = linkedArtists.ifEmpty {
        val shortNames = renderer.array("shortBylineText", "runs").mapNotNull { run ->
            val text = run.string("text")?.trim().orEmpty()
            if (text.isEmpty() || text in RUN_SEPARATORS) null else text
        }.distinct()
        if (shortNames.isNotEmpty()) {
            shortNames
        } else {
            extractText(renderer.at("longBylineText"))
                .substringBefore(SEPARATOR)
                .split(", ", " & ", " dan ", " and ")
                .map(String::trim)
                .filter { it.isNotEmpty() }
        }
    }

    val rawLength = extractText(renderer.at("lengthText")).trim()
    val normalizedLength = if (':' !in rawLength && DOT_DURATION_REGEX.matches(rawLength)) {
        rawLength.replace('.', ':')
    } else {
        rawLength
    }
    val durationMs = parseDuration(normalizedLength)

    val rawArt = renderer.array("thumbnail", "thumbnails").lastOrNull()?.string("url")
    val artUrl = rawArt?.let { if (it.startsWith("//")) "https:$it" else it }
        ?.let { resizeGoogleArtwork(it, ARTWORK_SIZE) }

    return Track(
        id = youTubeTrackId(videoId),
        title = title,
        artists = artists,
        durationMs = durationMs,
        artworkUrl = artUrl,
        source = YOUTUBE_MUSIC_SOURCE,
    )
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
private const val DEFAULT_MIX_NAME = "Mix YouTube Music"
private const val SEPARATOR = " • "
private val SONG_COUNT_REGEX = Regex("""\b(?:lagu|songs?|tracks?)\b""", RegexOption.IGNORE_CASE)
private val DOT_DURATION_REGEX = Regex("""^\d+\.\d{2}(?:\.\d{2})?$""")
private val RUN_SEPARATORS = setOf("•", ",", "&", "dan", "and")
private val TYPE_LABELS = setOf(
    "lagu", "song", "video", "episode", "episode podcast", "single", "album", "ep", "playlist", "artis", "artist",
)
private val VIEW_COUNT_REGEX = Regex("""\d.*(?:x|jt|rb|views|plays|pemutaran|ditonton)""")
