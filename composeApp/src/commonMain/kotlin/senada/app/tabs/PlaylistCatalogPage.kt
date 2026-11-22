package senada.app.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import senada.app.AdaptiveRow
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.minTouchTarget
import senada.app.nowplaying.PlaySource
import senada.app.shell.CatalogRoute
import senada.app.shell.SenadaIcons
import senada.catalog.PlaylistDetail
import senada.catalog.PlaylistSummary
import senada.catalog.isYouTubeMixPlaylist
import senada.library.LibraryRepository
import senada.library.SavedLocalPlaylist
import senada.library.savePlaylistWithUniqueName
import senada.model.Track

internal const val MIX_SAVE_DISABLED_MESSAGE =
    "Mix YouTube tidak dapat disimpan ke Library karena daftar lagunya berubah otomatis."

/** Status penyimpanan halaman playlist YouTube Music ke Library lokal. */
internal sealed interface PlaylistSaveStatus {
    data object Saving : PlaylistSaveStatus
    data class Saved(val playlist: SavedLocalPlaylist) : PlaylistSaveStatus
    data class Failed(val message: String) : PlaylistSaveStatus
}

/** Hasil penyimpanan playlist katalog ke Library lokal. */
internal sealed interface CatalogPlaylistSaveOutcome {
    data class Saved(val playlist: SavedLocalPlaylist) : CatalogPlaylistSaveOutcome
    data class Rejected(val message: String) : CatalogPlaylistSaveOutcome
}

/**
 * Menyimpan playlist dari halaman katalog YouTube Music ke Library lokal dengan akhiran nama unik,
 * serta menolak playlist mix/radio (`RD…` selain `RDCLAK…`).
 */
internal suspend fun saveCatalogPlaylist(
    library: LibraryRepository,
    playlistId: String,
    title: String,
    tracks: List<Track>,
): CatalogPlaylistSaveOutcome {
    if (isYouTubeMixPlaylist(playlistId)) {
        return CatalogPlaylistSaveOutcome.Rejected(MIX_SAVE_DISABLED_MESSAGE)
    }
    if (tracks.isEmpty()) {
        return CatalogPlaylistSaveOutcome.Rejected("Playlist belum memiliki lagu untuk disimpan.")
    }
    val saved = savePlaylistWithUniqueName(library, title, tracks)
    return CatalogPlaylistSaveOutcome.Saved(saved)
}

/** Daftar hasil pencarian playlist di tab Cari. */
internal fun LazyListScope.playlistSearchResults(
    state: PlaylistSearchState,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onResultUsed: () -> Unit,
) {
    if (state.query.isEmpty()) {
        item(key = "playlist-search-prompt") {
            Text(
                "Ketik nama atau topik playlist untuk mencari di YouTube Music.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    item(key = "playlist-search-heading") {
        Text(
            "Playlist di YouTube Music",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
    }
    if (state.loading && state.items.isEmpty()) {
        item(key = "playlist-search-loading") {
            PoliteStatusText("Mencari playlist…")
        }
    }
    state.error?.let { message ->
        item(key = "playlist-search-error") {
            CatalogErrorCard(message, onRetry)
        }
    }
    if (!state.loading && state.error == null && state.items.isEmpty()) {
        item(key = "playlist-search-empty") {
            PoliteStatusText("Playlist tidak ditemukan.")
        }
    }
    items(state.items, key = { "playlist-result-${it.id}" }) { playlist ->
        val label = listOfNotNull("Buka playlist ${playlist.title}", playlist.subtitle).joinToString(", ")
        CatalogSummaryRow(
            title = playlist.title,
            subtitle = playlist.subtitle,
            artworkUrl = playlist.artworkUrl,
            artworkShape = MaterialTheme.shapes.extraSmall,
            fallbackIcon = SenadaIcons.Library,
            accessibilityLabel = label,
            onClick = {
                onResultUsed()
                onOpenPlaylist(playlist)
            },
        )
    }
    if (state.nextPageToken != null && state.error == null) {
        item(key = "playlist-search-more") {
            OutlinedButton(
                onClick = onLoadMore,
                enabled = !state.loading,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().minTouchTarget(),
            ) {
                Text(if (state.loading) "Memuat…" else "Muat lebih banyak")
            }
        }
    }
}

/** Merender halaman detail playlist YouTube Music dengan gaya yang sama dengan halaman album. */
internal fun LazyListScope.playlistDetailScreen(
    route: CatalogRoute.Playlist,
    state: CatalogLoadState<PlaylistDetail>,
    saveStatus: PlaylistSaveStatus?,
    onSave: (title: String, tracks: List<Track>) -> Unit,
    onOpenSavedPlaylist: (String) -> Unit,
    previousLabel: String,
    library: LibraryState,
    actions: LibraryActions,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val detail = state.data
    val displayTitle = detail?.title?.takeIf { it.isNotBlank() } ?: route.label
    val artworkUrl = detail?.artworkUrl ?: route.artworkUrl
    val tracks = detail?.tracks.orEmpty()
    val isMix = isYouTubeMixPlaylist(route.id)
    val metadata = if (detail != null) {
        listOfNotNull(
            detail.owner?.takeIf { it.isNotBlank() },
            detail.songCountText?.takeIf { it.isNotBlank() }
                ?: "${tracks.size} lagu".takeIf { tracks.isNotEmpty() },
        ).joinToString(" · ")
    } else {
        route.subtitle.orEmpty()
    }

    item(key = "playlist-back-${route.id}") {
        BackLink(previousLabel, onBack)
    }
    item(key = "playlist-header-${route.id}") {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CatalogArtwork(
                    artworkUrl = artworkUrl,
                    shape = MaterialTheme.shapes.medium,
                    fallbackIcon = SenadaIcons.Library,
                    imageSize = 544,
                    modifier = Modifier.size(160.dp),
                )
                Text(
                    if (isMix) "Mix / Radio" else "Playlist",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    displayTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                if (metadata.isNotEmpty()) {
                    Text(
                        metadata,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { actions.playAll(tracks, PlaySource.Playlist(displayTitle)) },
                        enabled = tracks.isNotEmpty(),
                        shape = CircleShape,
                        modifier = Modifier.minTouchTarget().semantics {
                            contentDescription = "Putar playlist $displayTitle"
                        },
                    ) {
                        Icon(
                            imageVector = SenadaIcons.Play,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Putar", modifier = Modifier.clearAndSetSemantics {})
                    }
                    FilledTonalButton(
                        onClick = { actions.enqueueAll(tracks) },
                        enabled = tracks.isNotEmpty(),
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.minTouchTarget().semantics {
                            contentDescription = "Tambah semua lagu $displayTitle ke antrean"
                        },
                    ) {
                        Text("Tambah semua", modifier = Modifier.clearAndSetSemantics {})
                    }
                    if (!isMix) {
                        val isSaving = saveStatus is PlaylistSaveStatus.Saving
                        val isSaved = saveStatus is PlaylistSaveStatus.Saved
                        OutlinedButton(
                            onClick = { onSave(displayTitle, tracks) },
                            enabled = tracks.isNotEmpty() && !isSaving && !isSaved,
                            shape = CircleShape,
                            modifier = Modifier.minTouchTarget().semantics {
                                contentDescription = "Simpan playlist $displayTitle ke Library"
                            },
                        ) {
                            Text(
                                when {
                                    isSaving -> "Menyimpan…"
                                    isSaved -> "Tersimpan"
                                    else -> "Simpan ke Library"
                                },
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                    }
                }
                if (isMix) {
                    Text(
                        MIX_SAVE_DISABLED_MESSAGE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                } else {
                    when (saveStatus) {
                        is PlaylistSaveStatus.Saved -> {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                AdaptiveRow(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                    minPrimaryWidth = 160.dp,
                                    primary = {
                                        Text(
                                            "Tersimpan sebagai \"${saveStatus.playlist.name}\" " +
                                                "(${saveStatus.playlist.trackCount} lagu).",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                        )
                                    },
                                    trailing = {
                                        TextButton(
                                            onClick = { onOpenSavedPlaylist(saveStatus.playlist.id) },
                                            modifier = Modifier.minTouchTarget(),
                                        ) {
                                            Text("Buka di Library")
                                        }
                                    },
                                )
                            }
                        }
                        is PlaylistSaveStatus.Failed -> {
                            Text(
                                saveStatus.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        PlaylistSaveStatus.Saving, null -> Unit
                    }
                }
                if (detail?.truncated == true && tracks.isNotEmpty()) {
                    Text(
                        "Menampilkan ${tracks.size} lagu pertama.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (state.loading && detail == null) {
        item(key = "playlist-loading-${route.id}") {
            PoliteStatusText("Memuat halaman playlist…")
        }
    }
    state.error?.let { message ->
        item(key = "playlist-error-${route.id}") {
            CatalogErrorCard(message, onRetry)
        }
    }
    if (tracks.isNotEmpty()) {
        item(key = "playlist-tracks-heading-${route.id}") {
            Text(
                "Daftar lagu",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
            )
        }
        itemsIndexed(tracks, key = { index, track -> "playlist-track-${route.id}-$index-${track.id}" }) { index, track ->
            CatalogTrackRow(
                track = track,
                favorite = track.id in library.favoriteIds,
                trackNumber = index + 1,
                library = library,
                actions = actions,
                onPlay = { actions.playAll(tracks, PlaySource.Playlist(displayTitle), startIndex = index) },
            )
        }
    }
    if (!state.loading && state.error == null && detail != null && tracks.isEmpty()) {
        item(key = "playlist-empty-${route.id}") {
            PoliteStatusText("Belum ada lagu di playlist ini.")
        }
    }
}
