package senada.app.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.minTouchTarget
import senada.app.shell.SenadaIcons
import senada.app.theme.LocalSenadaLook
import senada.app.theme.SenadaPalette
import senada.app.trackAccessibilityLabel
import senada.catalog.AlbumSummary
import senada.catalog.ArtistSummary
import senada.catalog.HomeItem
import senada.catalog.MoodChip
import senada.catalog.youtube.resizeGoogleArtwork
import senada.library.HomeHero
import senada.library.RecentHomeItem

private val HomeContentPadding = 20.dp
private val HomeFavoritesBackground = Color(0xFF21413A)

/** Ikon hati garis 44 dp persis SVG mockup Main.dc.html (stroke 1.8). */
private val HomeFavoritesHeartIcon: ImageVector =
    ImageVector.Builder("HomeFavoritesHeart", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = addPathNodes("M12 20c0 0-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

/**
 * Tab Beranda v2: hero penuh lebar sampai tepi atas, "Baru diputar ›" dengan kartu Favorit
 * di depan, rekomendasi pribadi "Untuk kamu", chip mood & genre, lalu rak YouTube Music.
 */
internal fun LazyListScope.homeTab(
    library: LibraryState,
    actions: LibraryActions,
    hero: HomeHero,
    recentItems: List<RecentHomeItem>,
    discover: DiscoverUiState,
    homeShelves: HomeShelvesUiState,
    screenVisible: Boolean,
    screenWidthDp: Float,
    screenHeightDp: Float,
    onOpenProfile: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onSearch: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenMostPlayed: () -> Unit,
    onRefreshDiscover: () -> Unit,
    onRefreshHomeShelves: () -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenCatalogPlaylist: (HomeItem.Playlist) -> Unit,
    onOpenMood: (MoodChip) -> Unit,
    profileName: String = "",
    profilePhoto: String? = null,
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
                        screenWidthDp = screenWidthDp,
                        screenHeightDp = screenHeightDp,
                        profileName = profileName,
                        profilePhoto = profilePhoto,
                        onOpenProfile = onOpenProfile,
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
            is HomeSection.Recent -> {
                item(key = "home-recent-title") {
                    // 26 dp + 12 dp margin sentuh atas dalam target 48 dp = 38 dp dari bawah hero (top 478 dp).
                    HomeRecentHeader(
                        onClick = onOpenHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(top = 26.dp, start = HomeContentPadding, end = HomeContentPadding),
                    )
                }
                item(key = "home-recent") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                        contentPadding = PaddingValues(horizontal = HomeContentPadding),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "recent-favorites") {
                            HomeFavoritesTile(onClick = onOpenFavorites)
                        }
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
                    horizontalPadding = HomeContentPadding,
                    sectionTopSpacing = 16.dp,
                    itemSpacing = 12.dp,
                )
            }
            is HomeSection.Moods -> {
                item(key = "home-moods-title") {
                    ShelfTitle(
                        title = "Mood & genre",
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(top = 16.dp, start = HomeContentPadding, end = HomeContentPadding),
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
                    MoodChipsRow(
                        chips = section.chips,
                        onOpenMood = onOpenMood,
                        contentPadding = PaddingValues(horizontal = HomeContentPadding),
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(top = 12.dp),
                    )
                }
            }
            HomeSection.YouTubeDataSaver -> {
                item(key = "home-yt-data-saver") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(top = 20.dp, start = HomeContentPadding, end = HomeContentPadding),
                    ) {
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
            }
            HomeSection.YouTubeLoading -> {
                item(key = "home-yt-loading-1") {
                    ShelfSkeletonRow(
                        statusLabel = "Memuat rekomendasi YouTube Music…",
                        horizontalPadding = HomeContentPadding,
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(top = 20.dp),
                    )
                }
                item(key = "home-yt-loading-2") {
                    ShelfSkeletonRow(
                        statusLabel = "Memuat rak lainnya…",
                        horizontalPadding = HomeContentPadding,
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(top = 20.dp),
                    )
                }
            }
            is HomeSection.YouTubeError -> {
                item(key = "home-yt-error") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(top = 20.dp, start = HomeContentPadding, end = HomeContentPadding),
                    ) {
                        QuietNoticeCard(
                            message = section.message,
                            actionLabel = "Coba lagi",
                            actionDescription = "Coba muat ulang rekomendasi YouTube Music",
                            onAction = onRefreshHomeShelves,
                        )
                    }
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
                    horizontalPadding = HomeContentPadding,
                    sectionTopSpacing = 16.dp,
                    itemSpacing = 12.dp,
                )
            }
        }
    }
}

@Composable
private fun HomeRecentHeader(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .minTouchTarget()
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Baru diputar",
                style = homeSectionTitleStyle(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Icon(
                imageVector = SenadaIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun HomeFavoritesTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    val tileWidth = if (largeFont) 148.dp else 132.dp
    Column(
        modifier = modifier
            .width(tileWidth)
            .minTouchTarget()
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(LocalSenadaLook.current.listCoverRadius))
                .background(HomeFavoritesBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = HomeFavoritesHeartIcon,
                contentDescription = null,
                tint = SenadaPalette.AccentDark,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Favorit",
            style = homeTileTitleStyle(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (largeFont) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Daftar favoritmu",
            style = homeTileSubtitleStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (largeFont) 2 else 1,
            overflow = TextOverflow.Ellipsis,
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
