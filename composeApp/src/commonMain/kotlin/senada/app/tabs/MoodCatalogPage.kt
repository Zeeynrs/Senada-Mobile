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
import senada.app.LocalAppLanguage
import senada.app.appPlural
import senada.app.appString
import senada.app.shell.CatalogRoute
import senada.catalog.AlbumSummary
import senada.catalog.HomeItem
import senada.catalog.HomeShelf
import senada.catalog.PlaylistSummary
import senada.resources.Res
import senada.resources.catalog_mood_empty
import senada.resources.catalog_mood_loading
import senada.resources.catalog_mood_stats
import senada.resources.catalog_pick_count
import senada.resources.catalog_shelf_count
import senada.resources.catalog_type_mood

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
                text = appString(Res.string.catalog_type_mood),
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
                val shelfCountText = appPlural(Res.plurals.catalog_shelf_count, shelves.size, shelves.size)
                val pickCountText = appPlural(Res.plurals.catalog_pick_count, totalItems, totalItems)
                Text(
                    text = appString(Res.string.catalog_mood_stats, shelfCountText, pickCountText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (state.loading && state.data == null) {
        item(key = "mood-loading-$routeKey") {
            ShelfSkeletonRow(statusLabel = appString(Res.string.catalog_mood_loading, route.label))
        }
    }

    state.errorText?.let { errorText ->
        item(key = "mood-error-$routeKey") {
            val language = LocalAppLanguage.current
            CatalogErrorCard(message = errorText.resolve(language), onRetry = onRetry)
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

    if (!state.loading && state.errorText == null && state.data != null && shelves.isEmpty()) {
        item(key = "mood-empty-$routeKey") {
            PoliteStatusText(appString(Res.string.catalog_mood_empty))
        }
    }
}
