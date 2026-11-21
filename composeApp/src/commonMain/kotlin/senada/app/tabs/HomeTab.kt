package senada.app.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.accessibleAccent
import senada.app.shell.SenadaIcons
import senada.app.theme.SenadaPalette
import senada.app.trackAccessibilityLabel
import senada.catalog.youtube.resizeGoogleArtwork
import senada.library.HomeHero

internal fun LazyListScope.homeTab(
    library: LibraryState,
    actions: LibraryActions,
    hero: HomeHero,
    screenVisible: Boolean,
    onSearch: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenMostPlayed: () -> Unit,
) {
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
    item(key = "home-recent-title") { HomeSectionTitle("Baru diputar") }
    item(key = "home-recent") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "home-favorites") {
                val count = "${library.favoriteTotal} lagu"
                val accent = SenadaPalette.Favorite
                val background = accent.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
                HomeTile("Favorit", count, "Favorit, $count", onOpenFavorites) {
                    HomeCover(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        icon = SenadaIcons.HeartFilled,
                        containerColor = background,
                        iconColor = accessibleAccent(accent, background),
                    )
                }
            }
            items(library.history, key = { it.id }) { track ->
                val favorite = track.id in library.favoriteIds
                HomeTile(
                    title = track.title,
                    detail = track.artists.joinToString().takeIf { it.isNotBlank() },
                    accessibilityLabel = trackAccessibilityLabel(track, favorite),
                    onClick = { actions.playAll(listOf(track)) },
                ) {
                    Box {
                        HomeCover(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            icon = SenadaIcons.Queue,
                            artworkUrl = track.artworkUrl,
                        )
                        if (favorite) {
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
                    }
                }
            }
        }
    }
    item(key = "home-playlists-title") { HomeSectionTitle("Playlist kamu") }
    item(key = "home-playlists") {
        if (library.playlists.isEmpty()) {
            Text("Belum ada playlist.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(library.playlists, key = { it.id }) { playlist ->
                    val count = "${playlist.trackCount} lagu"
                    HomeTile(playlist.name, count, "Playlist ${playlist.name}, $count", { onOpenPlaylist(playlist.id) }) {
                        HomeCover(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            icon = SenadaIcons.Library,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun HomeTile(
    title: String,
    detail: String?,
    accessibilityLabel: String,
    onClick: () -> Unit,
    cover: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(150.dp).semantics {
            contentDescription = accessibilityLabel
            role = Role.Button
        },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column {
            cover()
            Column(
                modifier = Modifier.padding(12.dp).clearAndSetSemantics {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                detail?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
internal fun HomeCover(
    modifier: Modifier,
    icon: ImageVector,
    artworkUrl: String? = null,
    imageSize: Int = 320,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(modifier.background(containerColor), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(44.dp))
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
