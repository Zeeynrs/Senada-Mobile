package senada.app.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import senada.app.BrowseLoadMoreButton
import senada.app.BrowseMonoHeading
import senada.app.BrowseNoticeCard
import senada.app.BrowseRowSkeletonCard
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.SenadaCard
import senada.app.SenadaSources
import senada.app.TrackMenu
import senada.app.TrackRow
import senada.app.nowplaying.PlaySource
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
        BrowseMonoHeading(heading)
    }
    if (search.loading && search.tracks.isEmpty()) {
        item(key = "search-loading") {
            BrowseRowSkeletonCard(statusLabel = "Mencari…", rowCount = 3)
        }
    }
    search.error?.let { message ->
        item(key = "search-error") {
            CatalogErrorCard(message, onRetry)
        }
    }
    if (!search.loading && search.error == null && search.tracks.isEmpty()) {
        item(key = "search-empty") {
            BrowseNoticeCard(
                eyebrow = "Hasil kosong",
                message = "Lagu tidak ditemukan.",
            )
        }
    }
    if (search.query.isEmpty() && search.tracks.isNotEmpty()) {
        item(key = "search-demo-tracks") {
            SenadaCard {
                Column(Modifier.fillMaxWidth().padding(1.dp)) {
                    search.tracks.forEachIndexed { index, track ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 74.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        Box(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            TrackRow(
                                track = track,
                                favorite = track.id in library.favoriteIds,
                                onClick = {
                                    onResultUsed()
                                    actions.playAll(listOf(track), PlaySource.Search)
                                },
                            ) {
                                TrackMenu(
                                    track = track,
                                    library = library,
                                    actions = actions,
                                    onEnqueue = {
                                        onResultUsed()
                                        actions.enqueue(track)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        items(search.tracks, key = { "result-${it.id}" }) { track ->
            TrackRow(
                track = track,
                favorite = track.id in library.favoriteIds,
                onClick = {
                    onResultUsed()
                    actions.playAll(listOf(track), PlaySource.Search)
                },
            ) {
                TrackMenu(
                    track = track,
                    library = library,
                    actions = actions,
                    onEnqueue = {
                        onResultUsed()
                        actions.enqueue(track)
                    },
                )
            }
        }
    }
    if (search.nextPageToken != null && search.error == null) {
        item(key = "search-load-more") {
            BrowseLoadMoreButton(loading = search.loading, onClick = onLoadMore)
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
