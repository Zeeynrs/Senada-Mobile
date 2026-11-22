package senada.app.tabs

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import senada.app.AdaptiveRow
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.SenadaSources
import senada.app.TrackMenu
import senada.app.TrackRow
import senada.app.minTouchTarget
import senada.catalog.CatalogException
import senada.catalog.CatalogRepository
import senada.catalog.DemoCatalog
import senada.model.Track

/** Hasil pencarian YouTube Music, atau katalog contoh saat kolom cari kosong. */
internal fun LazyListScope.searchResults(
    search: SearchState,
    library: LibraryState,
    actions: LibraryActions,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onResultUsed: () -> Unit,
) {
    item(key = "search-results-heading") {
        val heading = if (search.query.isEmpty()) "Katalog contoh" else "YouTube Music"
        Text(
            heading,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
    }
    if (search.loading && search.tracks.isEmpty()) {
        item(key = "search-loading") {
            Text(
                "Mencari…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    search.error?.let { message ->
        item(key = "search-error") {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    minPrimaryWidth = 160.dp,
                    primary = {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    },
                    trailing = {
                        TextButton(onClick = onRetry, modifier = Modifier.minTouchTarget()) { Text("Coba lagi") }
                    },
                )
            }
        }
    }
    if (!search.loading && search.error == null && search.tracks.isEmpty()) {
        item(key = "search-empty") {
            Text(
                "Lagu tidak ditemukan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    items(search.tracks, key = { "result-${it.id}" }) { track ->
        TrackRow(track, favorite = track.id in library.favoriteIds) {
            FilledTonalButton(
                onClick = {
                    onResultUsed()
                    actions.enqueue(track)
                },
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.minTouchTarget().semantics {
                    contentDescription = "Tambah ${track.title} ke antrean"
                },
            ) { Text("Tambah") }
            TrackMenu(track, library, actions)
        }
    }
    if (search.nextPageToken != null && search.error == null) {
        item(key = "search-load-more") {
            OutlinedButton(
                onClick = onLoadMore,
                enabled = !search.loading,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().minTouchTarget(),
            ) {
                Text(if (search.loading) "Memuat…" else "Muat lebih banyak")
            }
        }
    }
}

internal const val SEARCH_DEBOUNCE_MS = 350L

/** Hasil pencarian yang tampil; kueri kosong menampilkan katalog contoh. */
internal data class SearchState(
    val query: String = "",
    val tracks: List<Track> = DemoCatalog.tracks,
    val nextPageToken: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Memuat halaman berikutnya (atau pertama); saat gagal, hasil dan token tetap ada untuk dicoba lagi. */
internal suspend fun loadSearchPage(
    current: SearchState,
    catalog: CatalogRepository = SenadaSources.youTubeMusic,
): SearchState = try {
    val page = catalog.search(current.query, current.nextPageToken)
    current.copy(
        tracks = (current.tracks + page.items).distinctBy { it.id },
        nextPageToken = page.nextPageToken,
        loading = false,
        error = null,
    )
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    current.copy(loading = false, error = (e as? CatalogException)?.userMessage ?: "Pencarian gagal. Coba lagi.")
}
