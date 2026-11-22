package senada.app.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.minTouchTarget
import senada.app.shell.SenadaIcons
import senada.app.theme.SenadaPalette
import senada.app.trackAccessibilityLabel
import senada.catalog.AlbumSummary
import senada.catalog.ArtistSummary
import senada.catalog.HomeItem
import senada.catalog.MoodChip
import senada.catalog.youtube.resizeGoogleArtwork
import senada.library.HomeHero
import senada.library.RecentHomeItem

/**
 * Tab Beranda: hero ringkas, pintasan statistik & teratas, Baru diputar,
 * rekomendasi pribadi "Untuk kamu", chip mood & genre, lalu rak YouTube Music.
 */
internal fun LazyListScope.homeTab(
    library: LibraryState,
    actions: LibraryActions,
    hero: HomeHero,
    recentItems: List<RecentHomeItem>,
    discover: DiscoverUiState,
    homeShelves: HomeShelvesUiState,
    screenVisible: Boolean,
    onSearch: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenMostPlayed: () -> Unit,
    onOpenListeningStats: () -> Unit,
    onRefreshDiscover: () -> Unit,
    onRefreshHomeShelves: () -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenCatalogPlaylist: (HomeItem.Playlist) -> Unit,
    onOpenMood: (MoodChip) -> Unit,
) {
    val sections = buildHomeSections(
        recentItems = recentItems,
        discover = discover,
        homeShelves = homeShelves,
    )

    for (section in sections) {
        when (section) {
            HomeSection.Hero -> {
                item(key = "home-hero") {
                    HomeHeroCard(
                        hero = hero,
                        screenVisible = screenVisible,
                        onPrimary = {
                            when (hero) {
                                is HomeHero.FromPlaylist -> actions.playAllOf(hero.summary.id)
                                is HomeHero.FromTrack -> actions.playAll(listOf(hero.track))
                                HomeHero.Welcome -> onSearch()
                            }
                        },
                        onSecondary = {
                            when (hero) {
                                is HomeHero.FromPlaylist -> onOpenPlaylist(hero.summary.id)
                                is HomeHero.FromTrack -> onOpenMostPlayed()
                                HomeHero.Welcome -> Unit
                            }
                        },
                    )
                }
            }
            HomeSection.Shortcuts -> {
                item(key = "home-shortcuts") {
                    HomeQuickLinksCard(
                        onOpenListeningStats = onOpenListeningStats,
                        onOpenMostPlayed = onOpenMostPlayed,
                    )
                }
            }
            is HomeSection.Recent -> {
                item(key = "home-recent-title") { ShelfTitle("Baru diputar") }
                item(key = "home-recent") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(section.items, key = { it.key }) { item ->
                            when (item) {
                                is RecentHomeItem.Song -> {
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
                                        badge = if (favorite) {
                                            {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    shape = CircleShape,
                                                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                                                ) {
                                                    Icon(
                                                        SenadaIcons.HeartFilled,
                                                        contentDescription = null,
                                                        tint = SenadaPalette.Favorite,
                                                        modifier = Modifier.padding(6.dp).size(16.dp),
                                                    )
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                    )
                                }
                                is RecentHomeItem.Playlist -> {
                                    val summary = item.summary
                                    val count = "${summary.trackCount} lagu"
                                    ShelfSquareCard(
                                        title = summary.name,
                                        subtitle = "Playlist · $count",
                                        artworkUrl = item.artworkUrl,
                                        fallbackIcon = SenadaIcons.Library,
                                        accessibilityLabel = "Playlist ${summary.name}, $count",
                                        onClick = { onOpenPlaylist(summary.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is HomeSection.Personal -> {
                discoverSection(
                    state = section.state,
                    library = library,
                    actions = actions,
                    onRefresh = onRefreshDiscover,
                    onOpenArtist = onOpenArtist,
                    onOpenAlbum = onOpenAlbum,
                )
            }
            is HomeSection.Moods -> {
                item(key = "home-moods-title") {
                    ShelfTitle(
                        title = "Mood & genre",
                        trailing = if (section.showRefresh) {
                            {
                                TextButton(
                                    onClick = onRefreshHomeShelves,
                                    enabled = !homeShelves.loading,
                                    modifier = Modifier.minTouchTarget().semantics {
                                        contentDescription = "Segarkan rekomendasi YouTube Music"
                                    },
                                ) {
                                    Text(if (homeShelves.loading) "Memuat…" else "Segarkan")
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
                item(key = "home-moods-row") {
                    MoodChipsRow(chips = section.chips, onOpenMood = onOpenMood)
                }
            }
            HomeSection.YouTubeDataSaver -> {
                item(key = "home-yt-data-saver") {
                    HomeDataSaverCard(
                        onLoad = {
                            onRefreshHomeShelves()
                            if (discover.deferredByDataSaver && discover.result == null) {
                                onRefreshDiscover()
                            }
                        },
                    )
                }
            }
            HomeSection.YouTubeLoading -> {
                item(key = "home-yt-loading-1") {
                    ShelfSkeletonRow(statusLabel = "Memuat rekomendasi YouTube Music…")
                }
                item(key = "home-yt-loading-2") {
                    ShelfSkeletonRow(statusLabel = "Memuat rak lainnya…")
                }
            }
            is HomeSection.YouTubeError -> {
                item(key = "home-yt-error") {
                    QuietNoticeCard(
                        message = section.message,
                        actionLabel = "Coba lagi",
                        actionDescription = "Coba muat ulang rekomendasi YouTube Music",
                        onAction = onRefreshHomeShelves,
                    )
                }
            }
            is HomeSection.YouTubeShelf -> {
                homeShelfItems(
                    keyPrefix = "home-yt-shelf-${section.index}",
                    shelf = section.shelf,
                    library = library,
                    actions = actions,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                    onOpenPlaylist = onOpenCatalogPlaylist,
                    refreshing = homeShelves.loading,
                    onRefresh = if (section.showRefresh) onRefreshHomeShelves else null,
                )
            }
        }
    }
}

/** Kartu berisi pintu masuk Statistik dengar dan Paling sering diputar dengan gaya baris seragam. */
@Composable
private fun HomeQuickLinksCard(
    onOpenListeningStats: () -> Unit,
    onOpenMostPlayed: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HomeQuickLinkRow(
                icon = SenadaIcons.Clock,
                title = "Statistik dengar",
                onClick = onOpenListeningStats,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            HomeQuickLinkRow(
                icon = SenadaIcons.MusicNote,
                title = "Paling sering diputar",
                onClick = onOpenMostPlayed,
            )
        }
    }
}

@Composable
private fun HomeQuickLinkRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minTouchTarget()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = SenadaIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun HomeCover(
    modifier: Modifier,
    icon: ImageVector,
    artworkUrl: String? = null,
    imageSize: Int = 320,
    iconSize: Dp = 32.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
) {
    Box(modifier.background(containerColor), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(iconSize))
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = remember(artworkUrl, imageSize) { resizeGoogleArtwork(artworkUrl, imageSize) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
