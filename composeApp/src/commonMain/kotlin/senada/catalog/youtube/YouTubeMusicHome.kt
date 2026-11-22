package senada.catalog.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import senada.catalog.CatalogException
import senada.catalog.CatalogException.Kind
import senada.catalog.HomeCatalog
import senada.catalog.HomeFeed
import senada.catalog.HomeItem
import senada.catalog.HomeShelf
import senada.catalog.HomeShelfLayout
import senada.catalog.HomeShelfSource
import senada.catalog.MoodChip

private const val HOME_BROWSE_ID = "FEmusic_home"
private const val EXPLORE_BROWSE_ID = "FEmusic_explore"
private const val CHARTS_BROWSE_ID = "FEmusic_charts"
private const val MOOD_CATEGORY_BROWSE_ID = "FEmusic_moods_and_genres_category"
private const val MAX_HOME_CONTINUATION_PAGES = 2

private val NON_MUSIC_PAGE_TYPES = setOf(
    "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE",
    "MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE",
)
private const val VIDEO_TYPE_PODCAST_EPISODE = "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"

/** Hasil penguraian satu halaman jelajah (rak beserta tombol mood bila berasal dari Explore). */
internal data class ParsedHomePage(
    val shelves: List<HomeShelf>,
    val moods: List<MoodChip> = emptyList(),
)

/** Implementasi [HomeCatalog] dari Innertube tanpa akun: Beranda, Jelajah, Tangga lagu, dan halaman mood. */
internal class YouTubeMusicHome(private val innertube: InnertubeClient) : HomeCatalog {
    override suspend fun homeFeed(saveData: Boolean): HomeFeed = coroutineScope {
        val initialVisitor = innertube.currentVisitorData()
        val homeDeferred = async { runCatchingCancellable { fetchHomeShelves(saveData, initialVisitor) } }
        val exploreDeferred = if (saveData) null else async { runCatchingCancellable { fetchExplorePage() } }
        val chartsDeferred = if (saveData) null else async { runCatchingCancellable { fetchChartsShelves() } }

        val homeResult = homeDeferred.await()
        val exploreResult = exploreDeferred?.await()
        val chartsResult = chartsDeferred?.await()

        val anySucceeded = homeResult.isSuccess ||
            exploreResult?.isSuccess == true ||
            chartsResult?.isSuccess == true
        if (!anySucceeded) {
            val firstError = listOfNotNull(
                homeResult.exceptionOrNull(),
                exploreResult?.exceptionOrNull(),
                chartsResult?.exceptionOrNull(),
            ).firstOrNull()
            val catalogError = firstError as? CatalogException
            throw CatalogException(
                kind = catalogError?.kind ?: Kind.Network,
                message = catalogError?.userMessage
                    ?: "Beranda YouTube Music tidak dapat dimuat. Periksa koneksi lalu coba lagi.",
                cause = firstError,
            )
        }

        val combined = buildList {
            homeResult.getOrNull()?.let(::addAll)
            exploreResult?.getOrNull()?.shelves?.let(::addAll)
            chartsResult?.getOrNull()?.let(::addAll)
        }

        HomeFeed(
            shelves = dedupeShelvesByTitle(combined),
            moods = exploreResult?.getOrNull()?.moods.orEmpty(),
        )
    }

    override suspend fun mood(chip: MoodChip): List<HomeShelf> {
        val response = innertube.browse(chip.browseId, chip.params)
        val parsed = parseBrowseShelves(response, HomeShelfSource.Explore, collectMoods = false, allowGridShelves = true)
        return dedupeShelvesByTitle(parsed.shelves)
    }

    private suspend fun fetchHomeShelves(saveData: Boolean, initialVisitor: String?): List<HomeShelf> {
        val first = innertube.browse(HOME_BROWSE_ID)
        val shelves = parseBrowseShelves(first, HomeShelfSource.Home).shelves.toMutableList()
        if (saveData) return shelves

        // Token lanjutan Beranda terikat ke visitorData halaman pertama; pertahankan meski Explore/Charts selesai lebih dulu.
        val contVisitor = initialVisitor ?: first.string("responseContext", "visitorData")
        val seenTokens = HashSet<String>()
        var current: JsonObject = first
        repeat(MAX_HOME_CONTINUATION_PAGES) {
            val token = nextSectionContinuation(current) ?: return shelves
            if (!seenTokens.add(token)) return shelves
            val more = try {
                innertube.browseContinuation(token, visitorOverride = contVisitor)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return shelves
            }
            shelves += parseBrowseShelves(more, HomeShelfSource.Home).shelves
            current = more
        }
        return shelves
    }

    private suspend fun fetchExplorePage(): ParsedHomePage =
        parseBrowseShelves(innertube.browse(EXPLORE_BROWSE_ID), HomeShelfSource.Explore, collectMoods = true)

    private suspend fun fetchChartsShelves(): List<HomeShelf> =
        parseBrowseShelves(innertube.browse(CHARTS_BROWSE_ID), HomeShelfSource.Charts).shelves
}

internal fun parseBrowseShelves(
    response: JsonObject,
    source: HomeShelfSource,
    collectMoods: Boolean = false,
    allowGridShelves: Boolean = false,
): ParsedHomePage {
    val sections = browseSections(response)
    val shelves = mutableListOf<HomeShelf>()
    val moods = mutableListOf<MoodChip>()
    val seenMoods = HashSet<Pair<String, String?>>()

    for (section in sections) {
        val carousel = section.at("musicCarouselShelfRenderer")
        val listShelf = if (carousel == null) section.at("musicShelfRenderer") else null
        val grid = if (carousel == null && listShelf == null) section.at("gridRenderer") else null

        val container = carousel ?: listShelf ?: grid ?: continue
        val rawItems = if (grid != null) container.array("items") else container.array("contents")
        if (rawItems.isEmpty()) continue

        val navButtons = rawItems.mapNotNull { it.at("musicNavigationButtonRenderer") }
        if (navButtons.isNotEmpty()) {
            if (collectMoods && (grid == null || isMoodGrid(container, navButtons))) {
                for (button in navButtons) {
                    val chip = parseMoodChip(button) ?: continue
                    if (seenMoods.add(chip.browseId to chip.params)) {
                        moods += chip
                    }
                }
            }
            continue
        }

        if (grid != null && !allowGridShelves) continue

        val title = shelfTitle(container).trim()
        val parsedItems = parseShelfItems(rawItems)
        if (parsedItems.isEmpty()) continue

        val layout = resolveShelfLayout(
            title = title,
            items = parsedItems,
            isCarousel = carousel != null,
        )
        shelves += HomeShelf(
            title = title,
            source = source,
            items = parsedItems,
            layout = layout,
        )
    }

    return ParsedHomePage(shelves = shelves, moods = moods)
}

internal fun isQuickPicksTitle(title: String): Boolean {
    val normalized = title.trim().lowercase()
    if (normalized.isEmpty()) return false
    return normalized == "quick picks" ||
        normalized.startsWith("quick pick") ||
        normalized == "pilihan cepat" ||
        normalized.startsWith("pilihan cepat") ||
        normalized == "mix lagu"
}

private fun isMoodGrid(grid: JsonElement, navButtons: List<JsonElement>): Boolean {
    val title = shelfTitle(grid).trim().lowercase()
    if (title.isNotEmpty()) return true
    return navButtons.any { btn ->
        val browseId = (btn.at("clickCommand", "browseEndpoint") ?: btn.at("navigationEndpoint", "browseEndpoint"))
            .string("browseId")
        browseId == MOOD_CATEGORY_BROWSE_ID
    }
}

private fun parseMoodChip(button: JsonElement): MoodChip? {
    val title = textOf(button.at("buttonText")).trim()
    if (title.isEmpty()) return null
    val browse = button.at("clickCommand", "browseEndpoint")
        ?: button.at("navigationEndpoint", "browseEndpoint")
        ?: return null
    val browseId = browse.string("browseId")?.trim().orEmpty()
    if (browseId.isEmpty()) return null
    val params = browse.string("params")?.trim()?.takeIf { it.isNotEmpty() }
    return MoodChip(title = title, browseId = browseId, params = params)
}

private fun parseShelfItems(rawItems: List<JsonElement>): List<HomeItem> {
    val seenKeys = HashSet<String>()
    return buildList {
        for (raw in rawItems) {
            val item = raw.at("musicResponsiveListItemRenderer")?.let(::parseHomeListItem)
                ?: raw.at("musicTwoRowItemRenderer")?.let(::parseHomeTwoRowItem)
                ?: continue
            if (seenKeys.add(homeItemKey(item))) {
                add(item)
            }
        }
    }
}

private fun parseHomeListItem(row: JsonElement): HomeItem? {
    if (isUnsupportedNonMusicItem(row)) return null
    parseBrowseTrack(row)?.let { return HomeItem.Song(it) }
    parseArtistSummary(row)?.let { return HomeItem.Artist(it) }
    parseAlbumSummary(row)?.let { return HomeItem.Album(it) }
    return parseListPlaylist(row)
}

private fun parseHomeTwoRowItem(card: JsonElement): HomeItem? {
    if (isUnsupportedNonMusicItem(card)) return null
    parseArtistCard(card)?.let { return HomeItem.Artist(it) }
    parseAlbumCard(card)?.let { return HomeItem.Album(it) }
    return parsePlaylistCard(card)
}

private fun parseListPlaylist(row: JsonElement): HomeItem.Playlist? {
    val target = browseTarget(row)
        ?: columnRuns(row, 0).firstNotNullOfOrNull(::browseTarget)
        ?: return null
    if (target.first != KIND_PLAYLIST) return null
    val cleanId = target.second.trim().removePrefix("VL")
    if (cleanId.isEmpty()) return null
    val title = runsText(columnRuns(row, 0))
    if (title.isEmpty()) return null
    val subtitle = runsText(columnRuns(row, 1)).takeIf { it.isNotEmpty() }
    return HomeItem.Playlist(
        id = cleanId,
        title = title,
        subtitle = subtitle,
        artworkUrl = bestArtwork(row),
    )
}

private fun parsePlaylistCard(card: JsonElement): HomeItem.Playlist? {
    val title = textOf(card.at("title"))
    if (title.isEmpty()) return null
    val cleanId = playlistIdOfCard(card) ?: return null
    return HomeItem.Playlist(
        id = cleanId,
        title = title,
        subtitle = textOf(card.at("subtitle")).takeIf { it.isNotEmpty() },
        artworkUrl = bestArtwork(card),
    )
}

private fun playlistIdOfCard(card: JsonElement): String? {
    val target = browseTarget(card)
        ?: card.array("title", "runs").firstNotNullOfOrNull(::browseTarget)
    if (target != null) {
        return if (target.first == KIND_PLAYLIST) {
            target.second.trim().removePrefix("VL").takeIf { it.isNotEmpty() }
        } else {
            null
        }
    }

    val endpoint = card.at("navigationEndpoint")
        ?: card.array("title", "runs").firstNotNullOfOrNull { it.at("navigationEndpoint") }
    val browse = endpoint.at("browseEndpoint")
    if (browse != null) {
        val rawId = browse.string("browseId")?.trim().orEmpty()
        val page = browse.string(
            "browseEndpointContextSupportedConfigs",
            "browseEndpointContextMusicConfig",
            "pageType",
        )
        if (page == null && (rawId.startsWith("VL") || rawId.startsWith("RD"))) {
            return rawId.removePrefix("VL").takeIf { it.isNotEmpty() }
        }
        return null
    }

    val watch = endpoint.at("watchEndpoint")
    val watchPlaylistId = watch.string("playlistId")?.trim().orEmpty()
    val watchVideoId = watch.string("videoId")?.trim().orEmpty()
    if (watchVideoId.isEmpty() && watchPlaylistId.startsWith("RD")) {
        return watchPlaylistId.removePrefix("VL").takeIf { it.isNotEmpty() }
    }
    return null
}

private fun isUnsupportedNonMusicItem(renderer: JsonElement): Boolean {
    val endpoints = buildList {
        renderer.at("navigationEndpoint")?.let(::add)
        renderer.array("title", "runs").mapNotNullTo(this) { it.at("navigationEndpoint") }
        columnRuns(renderer, 0).mapNotNullTo(this) { it.at("navigationEndpoint") }
        renderer.at(
            "overlay", "musicItemThumbnailOverlayRenderer", "content",
            "musicPlayButtonRenderer", "playNavigationEndpoint",
        )?.let(::add)
        renderer.at(
            "thumbnailOverlay", "musicItemThumbnailOverlayRenderer", "content",
            "musicPlayButtonRenderer", "playNavigationEndpoint",
        )?.let(::add)
    }

    for (ep in endpoints) {
        val browse = ep.at("browseEndpoint")
        if (browse != null) {
            val id = browse.string("browseId")?.trim().orEmpty()
            val page = browse.string(
                "browseEndpointContextSupportedConfigs",
                "browseEndpointContextMusicConfig",
                "pageType",
            )
            if (page in NON_MUSIC_PAGE_TYPES || id.startsWith("MPSP") || id.startsWith("MPED")) {
                return true
            }
        }
        val videoType = ep.string(
            "watchEndpoint", "watchEndpointMusicSupportedConfigs",
            "watchEndpointMusicConfig", "musicVideoType",
        )
        if (videoType == VIDEO_TYPE_PODCAST_EPISODE) {
            return true
        }
    }
    return false
}

private fun resolveShelfLayout(
    title: String,
    items: List<HomeItem>,
    isCarousel: Boolean,
): HomeShelfLayout = when {
    items.all { it is HomeItem.Artist } -> HomeShelfLayout.Artists
    items.all { it is HomeItem.Song } -> {
        if (isCarousel && isQuickPicksTitle(title)) HomeShelfLayout.QuickPicks else HomeShelfLayout.SongList
    }
    else -> HomeShelfLayout.Cards
}

private fun homeItemKey(item: HomeItem): String = when (item) {
    is HomeItem.Song -> "song:${item.track.id}"
    is HomeItem.Album -> "album:${item.album.id}"
    is HomeItem.Artist -> "artist:${item.artist.id}"
    is HomeItem.Playlist -> "playlist:${item.id}"
}

private fun dedupeShelvesByTitle(shelves: List<HomeShelf>): List<HomeShelf> {
    val seenTitles = HashSet<String>()
    return buildList {
        for (shelf in shelves) {
            val key = shelf.title.trim().lowercase()
            if (key.isEmpty() || seenTitles.add(key)) {
                add(shelf)
            }
        }
    }
}

private fun browseSections(response: JsonObject): List<JsonElement> =
    sectionListContents(response)
        .ifEmpty { response.array("continuationContents", "sectionListContinuation", "contents") }

private fun nextSectionContinuation(response: JsonObject): String? {
    val tab = response.at("contents", "singleColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content")
        ?: response.at("contents", "twoColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content")
    val sectionList = tab?.at("sectionListRenderer")
        ?: response.at("continuationContents", "sectionListContinuation")
        ?: return null

    sectionList.string("continuations", 0, "nextContinuationData", "continuation")
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    return sectionList.array("contents").lastOrNull()
        ?.string("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token")
        ?.takeIf { it.isNotBlank() }
}

private suspend inline fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
