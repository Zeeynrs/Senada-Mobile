package senada.app.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import senada.app.AdaptiveRow
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.TrackMenu
import senada.app.TrackRow
import senada.app.minTouchTarget
import senada.app.shell.SenadaIcons
import senada.app.trackAccessibilityLabel
import senada.catalog.AlbumSummary
import senada.catalog.ArtistSummary
import senada.catalog.HomeItem
import senada.catalog.HomeShelf
import senada.catalog.HomeShelfLayout
import senada.catalog.MoodChip
import senada.library.RecentHomeItem
import senada.model.Track

internal const val QUICK_PICKS_DEFAULT_ROWS = 4
internal const val QUICK_PICKS_MEDIUM_FONT_ROWS = 3
internal const val QUICK_PICKS_LARGE_FONT_ROWS = 2
internal const val QUICK_PICKS_BASE_MIN_COLUMN_WIDTH_DP = 280f
internal const val QUICK_PICKS_COLUMN_GAP_DP = 12f
internal const val QUICK_PICKS_MAX_COLUMNS = 3
internal const val SONG_LIST_MAX_ITEMS = 5

/** Susunan bagian Beranda dari atas ke bawah agar urutannya tetap konsisten dan mudah diuji. */
internal sealed interface HomeSection {
    data object Hero : HomeSection
    data object Shortcuts : HomeSection
    data class Recent(val items: List<RecentHomeItem>) : HomeSection
    data class Personal(val state: DiscoverUiState) : HomeSection
    data class Moods(val chips: List<MoodChip>, val showRefresh: Boolean = false) : HomeSection
    data object YouTubeLoading : HomeSection
    data object YouTubeDataSaver : HomeSection
    data class YouTubeError(val message: String) : HomeSection
    data class YouTubeShelf(val shelf: HomeShelf, val index: Int, val showRefresh: Boolean) : HomeSection
}

/**
 * Menyusun urutan bagian Beranda:
 * 1. Hero ringkas, 2. Pintasan, 3. Baru diputar, 4. Pribadi ("Untuk kamu"),
 * 5. Chip mood & genre, 6. Rak YouTube Music sesuai urutan feed.
 */
internal fun buildHomeSections(
    recentItems: List<RecentHomeItem>,
    discover: DiscoverUiState,
    homeShelves: HomeShelvesUiState,
): List<HomeSection> = buildList {
    add(HomeSection.Hero)
    add(HomeSection.Shortcuts)
    if (recentItems.isNotEmpty()) {
        add(HomeSection.Recent(recentItems))
    }

    val homeDeferred = homeShelves.deferredByDataSaver && homeShelves.feed == null
    val discoverDeferredOnly = discover.deferredByDataSaver && discover.result == null
    // Saat Hemat data menunda keduanya, cukup tampilkan satu tombol tenang di Beranda tanpa kartu ganda.
    if (!(homeDeferred && discoverDeferredOnly)) {
        add(HomeSection.Personal(discover))
    }

    if (homeDeferred) {
        add(HomeSection.YouTubeDataSaver)
        return@buildList
    }

    val feed = homeShelves.feed
    if (homeShelves.loading && feed == null) {
        add(HomeSection.YouTubeLoading)
        return@buildList
    }

    homeShelves.error?.let { message ->
        add(HomeSection.YouTubeError(message))
    }

    if (feed == null) return@buildList

    val moods = feed.moods.filter { it.title.isNotBlank() && it.browseId.isNotBlank() }
    val shelves = feed.shelves.filter { it.title.isNotBlank() && it.hasRenderableItems() }

    if (moods.isNotEmpty()) {
        add(HomeSection.Moods(chips = moods, showRefresh = shelves.isEmpty()))
    }
    shelves.forEachIndexed { index, shelf ->
        add(HomeSection.YouTubeShelf(shelf = shelf, index = index, showRefresh = index == 0))
    }
}

/** Benar bila rak punya minimal satu item yang sesuai dengan tata letaknya. */
internal fun HomeShelf.hasRenderableItems(): Boolean = when (layout) {
    HomeShelfLayout.QuickPicks, HomeShelfLayout.SongList -> items.any { it is HomeItem.Song }
    HomeShelfLayout.Cards -> items.isNotEmpty()
    HomeShelfLayout.Artists -> items.any { it is HomeItem.Artist }
}

/** Jumlah baris per kolom dan kolom per halaman pada kisi QuickPicks untuk lebar dan skala huruf tertentu. */
internal data class QuickPicksGridSpec(
    val rowsPerColumn: Int,
    val columnsPerPage: Int,
) {
    val itemsPerPage: Int get() = rowsPerColumn * columnsPerPage
}

/**
 * Menghitung kisi halaman QuickPicks: baku 4 baris per kolom; pada skala huruf besar baris dikurangi
 * dan lebar minimal kolom melebar agar baris lagu tetap lega dan mudah dipakai hingga 200%.
 */
internal fun quickPicksGridSpec(
    availableWidthDp: Float,
    fontScale: Float,
): QuickPicksGridSpec {
    val safeScale = fontScale.coerceAtLeast(1f)
    val rows = when {
        safeScale >= 1.75f -> QUICK_PICKS_LARGE_FONT_ROWS
        safeScale >= 1.35f -> QUICK_PICKS_MEDIUM_FONT_ROWS
        else -> QUICK_PICKS_DEFAULT_ROWS
    }
    val minColumnWidth = QUICK_PICKS_BASE_MIN_COLUMN_WIDTH_DP * (1f + (safeScale - 1f) * 0.5f)
    val safeWidth = availableWidthDp.coerceAtLeast(0f)
    val columns = ((safeWidth + QUICK_PICKS_COLUMN_GAP_DP) / (minColumnWidth + QUICK_PICKS_COLUMN_GAP_DP))
        .toInt()
        .coerceIn(1, QUICK_PICKS_MAX_COLUMNS)
    return QuickPicksGridSpec(rowsPerColumn = rows, columnsPerPage = columns)
}

/** Membagi daftar lagu ke halaman kisi; tiap halaman berisi daftar kolom dengan maks [QuickPicksGridSpec.rowsPerColumn] baris. */
internal fun <T> paginateQuickPicks(
    items: List<T>,
    spec: QuickPicksGridSpec,
): List<List<List<T>>> {
    if (items.isEmpty()) return emptyList()
    val perPage = spec.itemsPerPage.coerceAtLeast(1)
    val rows = spec.rowsPerColumn.coerceAtLeast(1)
    return items.chunked(perPage).map { page -> page.chunked(rows) }
}

private val ALBUM_TYPE_TOKENS = setOf("album", "ep", "single", "singel")
private val FOUR_DIGIT_YEAR = Regex("^\\d{4}$")

/** Label pembaca layar untuk kartu album dengan format "Album X oleh Y" bila nama artis tersedia. */
internal fun albumCardAccessibilityLabel(album: AlbumSummary): String {
    val title = album.title.trim()
    val rawParts = album.subtitle
        ?.split("•", "·")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        .orEmpty()
    val artistParts = rawParts.filter { part ->
        part.lowercase() !in ALBUM_TYPE_TOKENS && !FOUR_DIGIT_YEAR.matches(part)
    }
    val yearPart = album.year?.trim()?.takeIf(String::isNotEmpty)
        ?: rawParts.firstOrNull { FOUR_DIGIT_YEAR.matches(it) }
    val base = if (artistParts.isNotEmpty()) {
        "Album $title oleh ${artistParts.joinToString(", ")}"
    } else {
        "Album $title"
    }
    return if (yearPart != null) "$base, $yearPart" else base
}

/** Label pembaca layar untuk kartu playlist/mix dengan format "Playlist X". */
internal fun playlistCardAccessibilityLabel(playlist: HomeItem.Playlist): String {
    val base = "Playlist ${playlist.title.trim()}"
    val detail = playlist.subtitle?.trim()?.takeIf(String::isNotEmpty)
    return if (detail != null) "$base, $detail" else base
}

/** Label pembaca layar untuk kartu artis dengan format "Artis X". */
internal fun artistCardAccessibilityLabel(artist: ArtistSummary): String {
    val base = "Artis ${artist.name.trim()}"
    val detail = artist.subtitle?.trim()?.takeIf(String::isNotEmpty)
    return if (detail != null) "$base, $detail" else base
}

/** Label pembaca layar untuk chip mood & genre. */
internal fun moodChipAccessibilityLabel(chip: MoodChip): String = "Mood ${chip.title.trim()}"

/** Judul rak bersama yang dipakai oleh Baru diputar, Untuk kamu, rak YouTube Music, dan halaman Mood. */
@Composable
internal fun ShelfTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    emphasized: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val topPadding = if (emphasized) 8.dp else 4.dp
    val titleStyle = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
    val titleBlock: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            subtitle?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (trailing != null) {
        AdaptiveRow(
            modifier = modifier.fillMaxWidth().padding(top = topPadding),
            minPrimaryWidth = 160.dp,
            primary = titleBlock,
            trailing = trailing,
        )
    } else {
        Box(modifier = modifier.fillMaxWidth().padding(top = topPadding)) {
            titleBlock()
        }
    }
}

/** Kisi lagu QuickPicks yang digeser per halaman, mendukung menu ⋯ dan tetap lega pada skala huruf 200%. */
@Composable
internal fun QuickPicksShelf(
    tracks: List<Track>,
    library: LibraryState,
    actions: LibraryActions,
    onPlayTrack: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) return
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val spec = quickPicksGridSpec(availableWidthDp = maxWidth.value, fontScale = fontScale)
        val pages = paginateQuickPicks(tracks.withIndex().toList(), spec)
        if (pages.isEmpty()) return@BoxWithConstraints
        val pagerState = rememberPagerState(pageCount = { pages.size })
        // Intip halaman berikutnya saat huruf normal; pada huruf besar pakai lebar penuh agar tidak sempit.
        val peekEnd = if (pages.size > 1 && fontScale < 1.5f) 24.dp else 0.dp

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(end = peekEnd),
                pageSpacing = 12.dp,
                verticalAlignment = Alignment.Top,
            ) { pageIndex ->
                val columns = pages[pageIndex]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    columns.forEach { column ->
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            column.forEach { indexed ->
                                val track = indexed.value
                                TrackRow(
                                    track = track,
                                    favorite = track.id in library.favoriteIds,
                                    onClick = { onPlayTrack(indexed.index) },
                                ) {
                                    TrackMenu(track, library, actions)
                                }
                            }
                        }
                    }
                    repeat(spec.columnsPerPage - columns.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (pages.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pages.size) { dotIndex ->
                        val active = dotIndex == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (active) 18.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** Kartu kotak 152–160 dp bersama untuk album, playlist/mix, lagu baru diputar, dan rak kartu lainnya. */
@Composable
internal fun ShelfSquareCard(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    fallbackIcon: ImageVector,
    accessibilityLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: (@Composable BoxScope.() -> Unit)? = null,
) {
    val cardWidth = if (LocalDensity.current.fontScale >= 1.5f) 160.dp else 152.dp
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .width(cardWidth)
            .minTouchTarget()
            .semantics {
                contentDescription = accessibilityLabel
                role = Role.Button
            },
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                HomeCover(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(MaterialTheme.shapes.medium),
                    icon = fallbackIcon,
                    artworkUrl = artworkUrl,
                )
                badge?.invoke(this)
            }
            Column(
                modifier = Modifier.padding(12.dp).clearAndSetSemantics {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Kartu artis bulat bersama untuk bagian pribadi "Untuk kamu" maupun rak artis YouTube Music. */
@Composable
internal fun ShelfArtistCard(
    artist: ArtistSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardWidth = if (LocalDensity.current.fontScale >= 1.5f) 156.dp else 136.dp
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .width(cardWidth)
            .minTouchTarget()
            .semantics {
                contentDescription = artistCardAccessibilityLabel(artist)
                role = Role.Button
            },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HomeCover(
                modifier = Modifier.size(104.dp).clip(CircleShape),
                icon = SenadaIcons.Person,
                artworkUrl = artist.artworkUrl,
                iconSize = 28.dp,
            )
            Column(
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                artist.subtitle?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Deretan mendatar chip mood & genre yang membuka halaman Mood. */
@Composable
internal fun MoodChipsRow(
    chips: List<MoodChip>,
    onOpenMood: (MoodChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = chips,
            key = { index, chip -> "mood-$index-${chip.browseId}-${chip.params.orEmpty()}" },
        ) { _, chip ->
            Surface(
                onClick = { onOpenMood(chip) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.minTouchTarget().semantics {
                    contentDescription = moodChipAccessibilityLabel(chip)
                    role = Role.Button
                },
            ) {
                Box(
                    modifier = Modifier.minTouchTarget().padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = chip.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}

/** Kartu tenang saat Hemat data aktif di jaringan seluler agar YouTube Music tidak dimuat otomatis. */
@Composable
internal fun HomeDataSaverCard(onLoad: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Hemat data aktif di jaringan ini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            OutlinedButton(
                onClick = onLoad,
                shape = CircleShape,
                modifier = Modifier.minTouchTarget(),
            ) {
                Text("Muat rekomendasi YouTube Music")
            }
        }
    }
}

/** Placeholder kartu statis (hemat baterai, tanpa animasi terus-menerus) saat rak sedang dimuat. */
@Composable
internal fun ShelfSkeletonRow(
    statusLabel: String,
    cardCount: Int = 3,
    modifier: Modifier = Modifier,
) {
    val blockColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    Column(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = statusLabel
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .width(164.dp)
                .height(20.dp)
                .clip(CircleShape)
                .background(blockColor),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
        ) {
            items(cardCount) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = cardColor,
                    modifier = Modifier.width(152.dp),
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(MaterialTheme.shapes.medium)
                                .background(blockColor),
                        )
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(14.dp)
                                    .clip(CircleShape)
                                    .background(blockColor),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .height(12.dp)
                                    .clip(CircleShape)
                                    .background(blockColor),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Merender satu [HomeShelf] ke dalam [LazyListScope] sesuai [HomeShelf.layout]. */
internal fun LazyListScope.homeShelfItems(
    keyPrefix: String,
    shelf: HomeShelf,
    library: LibraryState,
    actions: LibraryActions,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenPlaylist: (HomeItem.Playlist) -> Unit,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    if (!shelf.hasRenderableItems()) return
    val songs = shelf.items.filterIsInstance<HomeItem.Song>().map { it.track }
    val artists = shelf.items.filterIsInstance<HomeItem.Artist>().map { it.artist }

    item(key = "$keyPrefix-title") {
        ShelfTitle(
            title = shelf.title,
            trailing = if (onRefresh != null) {
                {
                    TextButton(
                        onClick = onRefresh,
                        enabled = !refreshing,
                        modifier = Modifier.minTouchTarget().semantics {
                            contentDescription = "Segarkan rekomendasi YouTube Music"
                        },
                    ) {
                        Text(if (refreshing) "Memuat…" else "Segarkan")
                    }
                }
            } else {
                null
            },
        )
    }

    when (shelf.layout) {
        HomeShelfLayout.QuickPicks -> {
            item(key = "$keyPrefix-quickpicks") {
                QuickPicksShelf(
                    tracks = songs,
                    library = library,
                    actions = actions,
                    onPlayTrack = { index -> actions.playAll(songs, startIndex = index) },
                )
            }
        }
        HomeShelfLayout.SongList -> {
            val topSongs = songs.take(SONG_LIST_MAX_ITEMS)
            itemsIndexed(
                items = topSongs,
                key = { index, track -> "$keyPrefix-song-$index-${track.id}" },
            ) { index, track ->
                TrackRow(
                    track = track,
                    favorite = track.id in library.favoriteIds,
                    trackNumber = index + 1,
                    showArtworkWithNumber = true,
                    onClick = { actions.playAll(topSongs, startIndex = index) },
                ) {
                    TrackMenu(track, library, actions)
                }
            }
        }
        HomeShelfLayout.Cards -> {
            item(key = "$keyPrefix-cards") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(
                        items = shelf.items,
                        key = { index, item -> "$keyPrefix-card-$index-${item.stableKey()}" },
                    ) { _, item ->
                        when (item) {
                            is HomeItem.Album -> {
                                val detail = item.album.subtitle?.takeIf { it.isNotBlank() } ?: item.album.year
                                ShelfSquareCard(
                                    title = item.album.title,
                                    subtitle = detail,
                                    artworkUrl = item.album.artworkUrl,
                                    fallbackIcon = SenadaIcons.Album,
                                    accessibilityLabel = albumCardAccessibilityLabel(item.album),
                                    onClick = { onOpenAlbum(item.album) },
                                )
                            }
                            is HomeItem.Playlist -> {
                                ShelfSquareCard(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    artworkUrl = item.artworkUrl,
                                    fallbackIcon = SenadaIcons.Library,
                                    accessibilityLabel = playlistCardAccessibilityLabel(item),
                                    onClick = { onOpenPlaylist(item) },
                                )
                            }
                            is HomeItem.Song -> {
                                val track = item.track
                                val favorite = track.id in library.favoriteIds
                                val subtitle = track.artists
                                    .filter { it.isNotBlank() }
                                    .joinToString()
                                    .ifEmpty { "Lagu" }
                                ShelfSquareCard(
                                    title = track.title,
                                    subtitle = subtitle,
                                    artworkUrl = track.artworkUrl,
                                    fallbackIcon = SenadaIcons.MusicNote,
                                    accessibilityLabel = "Putar ${trackAccessibilityLabel(track, favorite)}",
                                    onClick = { actions.playAll(listOf(track)) },
                                )
                            }
                            is HomeItem.Artist -> {
                                ShelfArtistCard(
                                    artist = item.artist,
                                    onClick = { onOpenArtist(item.artist) },
                                )
                            }
                        }
                    }
                }
            }
        }
        HomeShelfLayout.Artists -> {
            item(key = "$keyPrefix-artists") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(
                        items = artists,
                        key = { index, artist -> "$keyPrefix-artist-$index-${artist.id}" },
                    ) { _, artist ->
                        ShelfArtistCard(
                            artist = artist,
                            onClick = { onOpenArtist(artist) },
                        )
                    }
                }
            }
        }
    }
}

private fun HomeItem.stableKey(): String = when (this) {
    is HomeItem.Song -> "song-${track.id}"
    is HomeItem.Album -> "album-${album.id}"
    is HomeItem.Artist -> "artist-${artist.id}"
    is HomeItem.Playlist -> "playlist-$id"
}
