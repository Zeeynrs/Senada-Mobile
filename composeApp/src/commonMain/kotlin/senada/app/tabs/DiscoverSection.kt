package senada.app.tabs

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.AsyncImage
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
import senada.app.TrackMenu
import senada.app.TrackRow
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
import senada.catalog.youtube.resizeGoogleArtwork
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
        AppDestination.Library, AppDestination.Settings -> false
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
) {
    val result = state.result
    val personal = result?.personal == true

    item(key = "discover-header") {
        AdaptiveRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            minPrimaryWidth = 160.dp,
            primary = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Untuk kamu",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        if (personal) {
                            "Berdasarkan lagu yang kamu putar dan sukai."
                        } else {
                            "Populer saat ini di YouTube Music."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailing = {
                if (!state.deferredByDataSaver) {
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
            },
        )
    }

    if (state.deferredByDataSaver && result == null) {
        item(key = "discover-data-saver") {
            QuietNoticeCard(
                message = "Hemat data aktif. Rekomendasi tidak dimuat otomatis di jaringan seluler.",
                actionLabel = "Muat",
                actionDescription = "Muat rekomendasi sekarang",
                onAction = onRefresh,
            )
        }
        return
    }

    if (state.loading && result == null) {
        item(key = "discover-loading") {
            Text(
                "Memuat rekomendasi…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    state.error?.let { message ->
        item(key = "discover-error") {
            QuietNoticeCard(
                message = message,
                actionLabel = "Coba lagi",
                actionDescription = "Coba muat ulang rekomendasi",
                onAction = onRefresh,
            )
        }
        if (result == null || result.isEmpty) return
    }

    if (result == null) return

    if (result.tracks.isNotEmpty()) {
        item(key = "discover-tracks-title") {
            DiscoverShelfTitle(if (personal) "Lagu untuk kamu" else "Lagu populer")
        }
        itemsIndexed(result.tracks, key = { _, track -> "discover-track-${track.id}" }) { index, track ->
            TrackRow(
                track = track,
                favorite = track.id in library.favoriteIds,
                onClick = { actions.playAll(result.tracks, startIndex = index) },
            ) {
                TrackMenu(track, library, actions)
            }
        }
    }

    if (result.artists.isNotEmpty()) {
        item(key = "discover-artists-title") {
            DiscoverShelfTitle(if (personal) "Artis yang mungkin kamu suka" else "Artis populer")
        }
        item(key = "discover-artists-row") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(result.artists, key = { "discover-artist-${it.id}" }) { artist ->
                    DiscoverArtistCard(artist, onClick = { onOpenArtist(artist) })
                }
            }
        }
    }

    if (result.albums.isNotEmpty()) {
        item(key = "discover-albums-title") {
            DiscoverShelfTitle(if (personal) "Album untuk kamu" else "Album populer")
        }
        item(key = "discover-albums-row") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(result.albums, key = { "discover-album-${it.id}" }) { album ->
                    DiscoverAlbumCard(album, onClick = { onOpenAlbum(album) })
                }
            }
        }
    }
}

@Composable
private fun DiscoverShelfTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp).semantics { heading() },
    )
}

@Composable
private fun DiscoverArtistCard(artist: ArtistSummary, onClick: () -> Unit) {
    val label = listOfNotNull("Buka artis ${artist.name}", artist.subtitle).joinToString(", ")
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.width(148.dp).minTouchTarget().semantics {
            contentDescription = label
            role = Role.Button
        },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DiscoverArtwork(
                artworkUrl = artist.artworkUrl,
                shape = CircleShape,
                fallbackIcon = SenadaIcons.Person,
                modifier = Modifier.size(104.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                artist.subtitle?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        detail,
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

@Composable
private fun DiscoverAlbumCard(album: AlbumSummary, onClick: () -> Unit) {
    val detail = album.subtitle?.takeIf { it.isNotBlank() } ?: album.year
    val label = listOfNotNull("Buka album ${album.title}", detail).joinToString(", ")
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.width(156.dp).minTouchTarget().semantics {
            contentDescription = label
            role = Role.Button
        },
    ) {
        Column {
            DiscoverArtwork(
                artworkUrl = album.artworkUrl,
                shape = MaterialTheme.shapes.medium,
                fallbackIcon = SenadaIcons.Album,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Column(
                modifier = Modifier.padding(12.dp).clearAndSetSemantics {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    album.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                detail?.let { text ->
                    Text(
                        text,
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

@Composable
private fun DiscoverArtwork(
    artworkUrl: String?,
    shape: Shape,
    fallbackIcon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.size(28.dp),
        )
        artworkUrl?.takeIf { it.isNotBlank() }?.let { url ->
            AsyncImage(
                model = remember(url) { resizeGoogleArtwork(url, 320) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun QuietNoticeCard(
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
