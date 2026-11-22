package senada.catalog.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import senada.catalog.CatalogException
import senada.catalog.PlaylistPage
import senada.catalog.PlaylistSummary

/**
 * Mencari playlist di YouTube Music (playlist unggulan YouTube Music maupun playlist komunitas)
 * serta mendukung pembacaan halaman lanjutan via [pageToken].
 */
internal suspend fun searchYouTubePlaylists(
    innertube: InnertubeClient,
    query: String,
    pageToken: String? = null,
): PlaylistPage {
    if (pageToken != null) {
        return parsePlaylistSearchPage(innertube.searchContinuation(pageToken))
    }
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return PlaylistPage(emptyList())

    return coroutineScope {
        val featuredDeferred = async {
            runCatchingCatalog { innertube.search(trimmed, InnertubeClient.FEATURED_PLAYLISTS_FILTER) }
        }
        val communityDeferred = async {
            runCatchingCatalog { innertube.search(trimmed, InnertubeClient.COMMUNITY_PLAYLISTS_FILTER) }
        }
        val featuredResult = featuredDeferred.await()
        val communityResult = communityDeferred.await()

        val featuredError = featuredResult.exceptionOrNull()
        val communityError = communityResult.exceptionOrNull()
        val featuredPage = featuredResult.getOrNull()?.let(::parsePlaylistSearchPage)
        val communityPage = communityResult.getOrNull()?.let(::parsePlaylistSearchPage)

        if (featuredPage == null && communityPage == null) {
            throw (featuredError ?: communityError ?: CatalogException(
                CatalogException.Kind.Network,
                "Pencarian playlist gagal.",
            ))
        }
        mergePlaylistPages(
            featured = featuredPage ?: PlaylistPage(emptyList()),
            community = communityPage ?: PlaylistPage(emptyList()),
        )
    }
}

private suspend inline fun runCatchingCatalog(block: suspend () -> JsonObject): Result<JsonObject> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** Menggabungkan hasil playlist unggulan dan playlist komunitas secara bergantian tanpa duplikat ID. */
internal fun mergePlaylistPages(featured: PlaylistPage, community: PlaylistPage): PlaylistPage {
    val merged = mutableListOf<PlaylistSummary>()
    val seenIds = HashSet<String>()
    val maxSize = maxOf(featured.items.size, community.items.size)
    for (index in 0 until maxSize) {
        featured.items.getOrNull(index)?.let { item ->
            if (seenIds.add(item.id)) merged += item
        }
        community.items.getOrNull(index)?.let { item ->
            if (seenIds.add(item.id)) merged += item
        }
    }
    return PlaylistPage(
        items = merged,
        nextPageToken = community.nextPageToken ?: featured.nextPageToken,
    )
}

/** Mengurai satu halaman hasil pencarian playlist atau halaman lanjutannya dari Innertube `search`. */
internal fun parsePlaylistSearchPage(response: JsonObject): PlaylistPage {
    val shelves = playlistSearchShelves(response)
    val items = mutableListOf<PlaylistSummary>()
    val seenIds = HashSet<String>()
    var continuationToken: String? = null

    for (shelf in shelves) {
        val rawItems = shelf.contents
        for (rawItem in rawItems) {
            val itemContinuation = rawItem.string(
                "continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token",
            )
            if (itemContinuation != null) {
                if (continuationToken == null) continuationToken = itemContinuation
                continue
            }
            val renderer = rawItem.at("musicResponsiveListItemRenderer") ?: continue
            val parsed = parsePlaylistSummary(renderer) ?: continue
            if (seenIds.add(parsed.id)) {
                items += parsed
            }
        }
        if (shelf.nextToken != null && continuationToken == null) {
            continuationToken = shelf.nextToken
        }
    }

    return PlaylistPage(items = items, nextPageToken = continuationToken)
}

private data class ShelfSlice(
    val contents: List<JsonElement>,
    val nextToken: String?,
)

private fun playlistSearchShelves(response: JsonObject): List<ShelfSlice> {
    val cont = response.at("continuationContents", "musicShelfContinuation")
        ?: response.at("continuationContents", "musicPlaylistShelfContinuation")
    if (cont != null) {
        return listOf(
            ShelfSlice(
                contents = cont.array("contents"),
                nextToken = cont.string("continuations", 0, "nextContinuationData", "continuation"),
            ),
        )
    }

    val actionItems = response.array("onResponseReceivedActions").mapNotNull { action ->
        (action.at("appendContinuationItemsAction", "continuationItems") as? JsonArray)?.let {
            ShelfSlice(contents = it, nextToken = null)
        }
    }
    if (actionItems.isNotEmpty()) return actionItems

    val sections = response.array(
        "contents", "tabbedSearchResultsRenderer", "tabs", 0, "tabRenderer", "content",
        "sectionListRenderer", "contents",
    ).ifEmpty {
        response.array("contents", "sectionListRenderer", "contents")
    }

    val out = mutableListOf<ShelfSlice>()
    for (section in sections) {
        section.at("musicShelfRenderer")?.let { shelf ->
            out += ShelfSlice(
                contents = shelf.array("contents"),
                nextToken = shelf.string("continuations", 0, "nextContinuationData", "continuation"),
            )
        }
        for (nested in section.array("itemSectionRenderer", "contents")) {
            nested.at("musicShelfRenderer")?.let { shelf ->
                out += ShelfSlice(
                    contents = shelf.array("contents"),
                    nextToken = shelf.string("continuations", 0, "nextContinuationData", "continuation"),
                )
            }
        }
    }
    return out
}

private fun parsePlaylistSummary(renderer: JsonElement): PlaylistSummary? {
    val flexTexts = renderer.array("flexColumns")
        .map { it.at("musicResponsiveListItemFlexColumnRenderer", "text") }

    val directBrowseId = renderer.string("navigationEndpoint", "browseEndpoint", "browseId")
    val directPageType = renderer.string(
        "navigationEndpoint", "browseEndpoint", "browseEndpointContextSupportedConfigs",
        "browseEndpointContextMusicConfig", "pageType",
    )

    var runBrowseId: String? = null
    var runPageType: String? = null
    if (directBrowseId == null) {
        for (textObj in flexTexts) {
            for (run in textObj.array("runs")) {
                val id = run.string("navigationEndpoint", "browseEndpoint", "browseId")
                if (!id.isNullOrBlank()) {
                    runBrowseId = id
                    runPageType = run.string(
                        "navigationEndpoint", "browseEndpoint", "browseEndpointContextSupportedConfigs",
                        "browseEndpointContextMusicConfig", "pageType",
                    )
                    break
                }
            }
            if (runBrowseId != null) break
        }
    }

    val browseId = (directBrowseId ?: runBrowseId)?.trim().orEmpty()
    if (browseId.isEmpty()) return null

    val resolvedPageType = directPageType ?: runPageType
    if (resolvedPageType != null && resolvedPageType != PLAYLIST_PAGE_TYPE) return null
    if (resolvedPageType == null && !browseId.startsWith("VL")) return null

    val id = browseId.removePrefix("VL").trim()
    if (id.isEmpty()) return null

    val title = flexTextString(flexTexts.firstOrNull()).trim()
    if (title.isEmpty()) return null

    val subtitleParts = mutableListOf<String>()
    for (col in flexTexts.drop(1)) {
        val runs = col.array("runs")
        if (runs.isEmpty()) {
            val simple = col?.string("simpleText")?.trim().orEmpty()
            if (simple.isNotEmpty() && simple.lowercase() !in IGNORED_TYPE_PREFIXES) {
                subtitleParts += simple
            }
            continue
        }
        val colParts = runs.mapNotNull { run ->
            val piece = run.string("text")?.trim().orEmpty()
            if (piece.isEmpty() || piece == "•" || piece.lowercase() in IGNORED_TYPE_PREFIXES) null else piece
        }
        if (colParts.isNotEmpty()) {
            subtitleParts += colParts.joinToString(" • ")
        }
    }
    val subtitle = subtitleParts.joinToString(" • ").takeIf { it.isNotBlank() }

    return PlaylistSummary(
        id = id,
        title = title,
        subtitle = subtitle,
        artworkUrl = searchPlaylistArtwork(renderer),
    )
}

private fun flexTextString(textObj: JsonElement?): String {
    if (textObj == null) return ""
    textObj.string("simpleText")?.let { return it }
    return textObj.array("runs").joinToString("") { it.string("text").orEmpty() }
}

private fun searchPlaylistArtwork(renderer: JsonElement): String? {
    val thumbnails = renderer.array("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")
    val rawUrl = thumbnails.lastOrNull()?.string("url") ?: return null
    val url = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
    return resizeGoogleArtwork(url, ARTWORK_SIZE)
}

private const val PLAYLIST_PAGE_TYPE = "MUSIC_PAGE_TYPE_PLAYLIST"
private val IGNORED_TYPE_PREFIXES = setOf("playlist", "daftar putar")
