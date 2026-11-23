package senada.app.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import senada.app.AdaptiveRow
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.minTouchTarget
import senada.app.shell.AppDestination
import senada.app.shell.SenadaIcons
import senada.catalog.AlbumSummary
import senada.catalog.ArtistSummary
import senada.catalog.CatalogException
import senada.catalog.youtube.DiscoverResult
import senada.catalog.youtube.DiscoverSeeds
import senada.catalog.youtube.DiscoverSource
import senada.catalog.youtube.buildDiscoverRecommendations
import senada.epochMillis

internal const val DISCOVER_TTL_MS = 10L * 60 * 1_000

/** Menentukan apakah rak "Untuk kamu" sedang terlihat (di Beranda atau Cari kosong) sehingga boleh disegarkan. */
internal fun shouldRefreshDiscover(
    screenVisible: Boolean,
    destination: AppDestination,
    hasCatalogRoute: Boolean,
    mostPlayedOpen: Boolean,
    listeningStatsOpen: Boolean,
    searchQueryBlank: Boolean,
): Boolean {
    if (!screenVisible || hasCatalogRoute) return false
    return when (destination) {
        AppDestination.Home -> !mostPlayedOpen && !listeningStatsOpen
        AppDestination.Search -> searchQueryBlank
        AppDestination.Library, AppDestination.Profile -> false
    }
}

/** Status tampilan rak rekomendasi "Untuk kamu" di Beranda dan tab Cari. */
internal data class DiscoverUiState(
    val result: DiscoverResult? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val deferredByDataSaver: Boolean = false,
)

/**
 * Menyimpan hasil rekomendasi selama sesi, membatasi penyegaran otomatis lewat [ttlMs],
 * dan menghormati mode hemat data seluler tanpa menghalangi kolom pencarian.
 */
internal class DiscoverController(
    private val source: DiscoverSource,
    private val scope: CoroutineScope,
    private val now: () -> Long = ::epochMillis,
    private val ttlMs: Long = DISCOVER_TTL_MS,
    private val workContext: CoroutineContext = Dispatchers.Default,
) {
    var state by mutableStateOf(DiscoverUiState())
        private set

    private var job: Job? = null
    private var lastLoadedAtMs: Long = 0L
    private var lastSeeds: DiscoverSeeds? = null
    private var lastSaveData: Boolean = false

    fun refreshIfNeeded(seeds: DiscoverSeeds, saveData: Boolean, force: Boolean = false) {
        if (state.loading) return
        val existing = state.result
        if (saveData && !force && existing == null) {
            state = state.copy(deferredByDataSaver = true, error = null)
            return
        }
        val fresh = existing != null && !existing.isEmpty && (now() - lastLoadedAtMs) < ttlMs
        val unchanged = existing != null && !existing.isEmpty && seeds == lastSeeds && saveData == lastSaveData
        if (!force && (fresh || unchanged)) {
            if (state.deferredByDataSaver) state = state.copy(deferredByDataSaver = false)
            return
        }

        job?.cancel()
        state = state.copy(loading = true, error = null, deferredByDataSaver = false)
        job = scope.launch {
            try {
                // Parsing respons Innertube berjalan di luar thread UI agar guliran dan input tetap mulus.
                val loaded = withContext(workContext) {
                    buildDiscoverRecommendations(seeds = seeds, source = source, saveData = saveData)
                }
                lastLoadedAtMs = now()
                lastSeeds = seeds
                lastSaveData = saveData
                state = DiscoverUiState(result = loaded, loading = false, error = null, deferredByDataSaver = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = (e as? CatalogException)?.userMessage
                    ?: "Rekomendasi sedang tidak tersedia. Periksa koneksi internet."
                state = state.copy(loading = false, error = message)
            }
        }
    }
}

/** Rak rekomendasi "Untuk kamu" yang dipakai bersama di Beranda dan tab Cari saat kolom cari kosong. */
internal fun LazyListScope.discoverSection(
    state: DiscoverUiState,
    library: LibraryState,
    actions: LibraryActions,
    onRefresh: () -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    horizontalPadding: Dp = 0.dp,
    sectionTopSpacing: Dp = 0.dp,
    itemSpacing: Dp = 0.dp,
) {
    val result = state.result
    val personal = result?.personal == true

    fun paddedModifier(top: Dp): Modifier = if (horizontalPadding > 0.dp) {
        Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(top = top, start = horizontalPadding, end = horizontalPadding)
    } else {
        Modifier
    }

    fun rowModifier(top: Dp): Modifier = if (horizontalPadding > 0.dp) {
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(top = top)
    } else {
        Modifier.fillMaxWidth()
    }

    item(key = "discover-header") {
        ShelfTitle(
            title = "Untuk kamu",
            modifier = paddedModifier(sectionTopSpacing),
            subtitle = if (personal) {
                "Berdasarkan lagu yang kamu putar dan sukai."
            } else {
                "Populer saat ini di YouTube Music."
            },
            trailing = if (!state.deferredByDataSaver) {
                {
                    TextButton(
                        onClick = onRefresh,
                        enabled = !state.loading,
                        modifier = Modifier.minTouchTarget().semantics {
                            contentDescription = "Muat rekomendasi baru"
                        },
                    ) {
                        Text(if (state.loading) "Memuat…" else "Segarkan")
                    }
                }
            } else {
                null
            },
        )
    }

    if (state.deferredByDataSaver && result == null) {
        item(key = "discover-data-saver") {
            Box(modifier = paddedModifier(itemSpacing)) {
                QuietNoticeCard(
                    message = "Hemat data aktif. Rekomendasi tidak dimuat otomatis di jaringan seluler.",
                    actionLabel = "Muat",
                    actionDescription = "Muat rekomendasi sekarang",
                    onAction = onRefresh,
                )
            }
        }
        return
    }

    if (state.loading && result == null) {
        item(key = "discover-loading") {
            ShelfSkeletonRow(
                statusLabel = "Memuat rekomendasi…",
                horizontalPadding = horizontalPadding,
                modifier = if (horizontalPadding > 0.dp) {
                    Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(top = itemSpacing)
                } else {
                    Modifier
                },
            )
        }
    }

    state.error?.let { message ->
        item(key = "discover-error") {
            Box(modifier = paddedModifier(itemSpacing)) {
                QuietNoticeCard(
                    message = message,
                    actionLabel = "Coba lagi",
                    actionDescription = "Coba muat ulang rekomendasi",
                    onAction = onRefresh,
                )
            }
        }
        if (result == null || result.isEmpty) return
    }

    if (result == null) return

    if (result.tracks.isNotEmpty()) {
        item(key = "discover-tracks-title") {
            ShelfTitle(
                title = if (personal) "Lagu untuk kamu" else "Lagu populer",
                emphasized = false,
                modifier = paddedModifier(itemSpacing),
            )
        }
        item(key = "discover-tracks-quickpicks") {
            QuickPicksShelf(
                tracks = result.tracks,
                library = library,
                actions = actions,
                onPlayTrack = { index -> actions.playAll(result.tracks, startIndex = index) },
                modifier = paddedModifier(itemSpacing),
            )
        }
    }

    if (result.artists.isNotEmpty()) {
        item(key = "discover-artists-title") {
            ShelfTitle(
                title = if (personal) "Artis yang mungkin kamu suka" else "Artis populer",
                emphasized = false,
                modifier = paddedModifier(itemSpacing),
            )
        }
        item(key = "discover-artists-row") {
            LazyRow(
                modifier = rowModifier(itemSpacing),
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    items = result.artists,
                    key = { index, artist -> "discover-artist-$index-${artist.id}" },
                ) { _, artist ->
                    ShelfArtistCard(artist = artist, onClick = { onOpenArtist(artist) })
                }
            }
        }
    }

    if (result.albums.isNotEmpty()) {
        item(key = "discover-albums-title") {
            ShelfTitle(
                title = if (personal) "Album untuk kamu" else "Album populer",
                emphasized = false,
                modifier = paddedModifier(itemSpacing),
            )
        }
        item(key = "discover-albums-row") {
            LazyRow(
                modifier = rowModifier(itemSpacing),
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    items = result.albums,
                    key = { index, album -> "discover-album-$index-${album.id}" },
                ) { _, album ->
                    val detail = album.subtitle?.takeIf { it.isNotBlank() } ?: album.year
                    ShelfSquareCard(
                        title = album.title,
                        subtitle = detail,
                        artworkUrl = album.artworkUrl,
                        fallbackIcon = SenadaIcons.Album,
                        accessibilityLabel = albumCardAccessibilityLabel(album),
                        onClick = { onOpenAlbum(album) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun QuietNoticeCard(
    message: String,
    actionLabel: String,
    actionDescription: String,
    onAction: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AdaptiveRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            minPrimaryWidth = 160.dp,
            primary = {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            },
            trailing = {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.minTouchTarget().semantics { contentDescription = actionDescription },
                ) { Text(actionLabel) }
            },
        )
    }
}
