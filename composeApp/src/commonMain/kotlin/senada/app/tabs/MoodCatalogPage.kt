package senada.app.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import senada.app.CollectionBackButton
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.shell.CatalogRoute
import senada.catalog.AlbumSummary
import senada.catalog.HomeItem
import senada.catalog.HomeShelf
import senada.catalog.PlaylistSummary

/** Halaman detail satu mood & genre yang menampilkan rak playlist dari [senada.catalog.HomeCatalog.mood]. */
internal fun LazyListScope.moodDetailScreen(
    route: CatalogRoute.Mood,
    state: CatalogLoadState<List<HomeShelf>>,
    previousLabel: String,
    library: LibraryState,
    actions: LibraryActions,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onOpenArtist: (id: String, name: String) -> Unit,
) {
    val routeKey = route.cacheKey()
    val shelves = state.data.orEmpty().filter { it.title.isNotBlank() && it.hasRenderableItems() }

    item(key = "mood-header-$routeKey") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CollectionBackButton(
                previousLabel = previousLabel,
                onBack = onBack,
            )
            Text(
                text = "Mood & genre",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = route.label,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (shelves.isNotEmpty()) {
                val totalItems = shelves.sumOf { it.items.size }
                Text(
                    text = "${shelves.size} rak · $totalItems pilihan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (state.loading && state.data == null) {
        item(key = "mood-loading-$routeKey") {
            ShelfSkeletonRow(statusLabel = "Memuat rak ${route.label}…")
        }
    }

    state.error?.let { message ->
        item(key = "mood-error-$routeKey") {
            CatalogErrorCard(message = message, onRetry = onRetry)
        }
    }

    shelves.forEachIndexed { index, shelf ->
        homeShelfItems(
            keyPrefix = "mood-$routeKey-shelf-$index",
            shelf = shelf,
            library = library,
            actions = actions,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = { artist -> onOpenArtist(artist.id, artist.name) },
            onOpenPlaylist = { playlist: HomeItem.Playlist ->
                onOpenPlaylist(
                    PlaylistSummary(
                        id = playlist.id,
                        title = playlist.title,
                        subtitle = playlist.subtitle,
                        artworkUrl = playlist.artworkUrl,
                    ),
                )
            },
        )
    }

    if (!state.loading && state.error == null && state.data != null && shelves.isEmpty()) {
        item(key = "mood-empty-$routeKey") {
            PoliteStatusText("Belum ada playlist untuk kategori ini.")
        }
    }
}
