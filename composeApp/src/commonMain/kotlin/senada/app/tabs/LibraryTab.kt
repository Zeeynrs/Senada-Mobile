package senada.app.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import senada.app.CollectionPlayButtons
import senada.app.CompactCollectionHeader
import senada.app.ImportAudioButton
import senada.app.ImportAudioStatusMessage
import senada.app.LibraryActions
import senada.app.LibraryEmptyState
import senada.app.LibraryState
import senada.app.SenadaFilterPill
import senada.app.TrackMenu
import senada.app.TrackRow
import senada.app.collectionMeta
import senada.app.favoritesTab
import senada.app.historyTab
import senada.app.offlineSongsTab
import senada.app.playlistsTab
import senada.app.rememberAudioImportController
import senada.app.sumKnownDurationMs
import senada.library.Playlist
import senada.library.LibraryArtist
import senada.model.Track
import senada.offline.OfflineState
import senada.platform.importedAudioStorage

/** Pilihan filter di dalam satu tab Library. */
internal enum class LibraryFilter(val label: String) {
    All("Semua"),
    Playlists("Playlist"),
    Artists("Artis"),
    Favorites("Favorit"),
    Offline("Offline"),
    History("Riwayat"),
    Imported("File impor"),
}

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.libraryTab(
    section: LibraryFilter,
    onSection: (LibraryFilter) -> Unit,
    library: LibraryState,
    openedPlaylist: Playlist?,
    artists: List<LibraryArtist>?,
    downloadState: OfflineState?,
    profileName: String,
    profilePhoto: String?,
    onOpenProfile: () -> Unit,
    actions: LibraryActions,
    onOpenPlaylist: (String?) -> Unit,
    onLoadMoreFavorites: () -> Unit,
    onLoadMorePlaylist: () -> Unit,
    onOpenSearch: () -> Unit = {},
    reorderingPlaylist: Boolean = false,
    onToggleReorderPlaylist: () -> Unit = {},
) {
    if (openedPlaylist != null) {
        playlistsTab(
            library, openedPlaylist, actions, onOpenPlaylist, onLoadMorePlaylist, onOpenSearch,
            reordering = reorderingPlaylist, onToggleReorder = onToggleReorderPlaylist,
        )
        return
    }

    item(key = "library-toolbar") {
        LibraryToolbar(profileName, profilePhoto, onOpenProfile, actions) { onOpenPlaylist(it) }
    }
    stickyHeader(key = "library-filters") {
        LibraryFilterChips(section, onSection)
    }
    when (section) {
        LibraryFilter.All -> libraryOverviewRows(library, artists.orEmpty(), actions, onSection) { onOpenPlaylist(it) }
        LibraryFilter.Favorites -> favoritesTab(library, actions, onLoadMoreFavorites, onOpenSearch)
        LibraryFilter.Offline -> offlineSongsTab(downloadState ?: OfflineState(), library, actions, onOpenSearch)
        LibraryFilter.Artists -> when {
            artists == null -> item(key = "library-artists-loading") {
                Text("Memuat artis…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            artists.isEmpty() -> item(key = "library-artists-empty") {
                LibraryEmptyState("Simpan lagu ke favorit atau playlist untuk melihat artisnya.", "Cari lagu", onAction = onOpenSearch)
            }
            else -> libraryArtistRows(artists, actions)
        }
        LibraryFilter.Playlists -> playlistsTab(
            library = library,
            opened = openedPlaylist,
            actions = actions,
            onOpen = onOpenPlaylist,
            onLoadMore = onLoadMorePlaylist,
            onOpenSearch = onOpenSearch,
            reordering = reorderingPlaylist,
            onToggleReorder = onToggleReorderPlaylist,
        )
        LibraryFilter.History -> historyTab(library, actions, onOpenSearch)
        LibraryFilter.Imported -> importedTracksTab(library, actions)
    }
}

@Composable
private fun LibraryFilterChips(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 2.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (filter in LibraryFilter.entries) {
            SenadaFilterPill(
                label = filter.label,
                selected = filter == selected,
                onClick = { onSelect(filter) },
                modifier = Modifier.semantics { contentDescription = "Filter Library ${filter.label}" },
            )
        }
    }
}

/** Daftar file audio yang diimpor pengguna ke library. */
private fun LazyListScope.importedTracksTab(library: LibraryState, actions: LibraryActions) {
    item(key = "library-imported") {
        val context = LocalPlatformContext.current
        val storage = remember(context) { importedAudioStorage(context) }
        LaunchedEffect(actions, storage) { actions.syncImportedFiles(storage) }
        val tracks by remember(actions) { actions.localTracks() }.collectAsState(emptyList())
        ImportedTracksContent(tracks, library, actions)
    }
}

@Composable
private fun ImportedTracksContent(
    tracks: List<Track>,
    library: LibraryState,
    actions: LibraryActions,
) {
    val importController = rememberAudioImportController(onImported = actions::addImportedTrack)
    val meta = remember(tracks) {
        collectionMeta(trackCount = tracks.size, totalDurationMs = sumKnownDurationMs(tracks))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CompactCollectionHeader(
            title = "File impor",
            meta = meta,
            eyebrow = "Penyimpanan lokal",
        ) {
            CollectionPlayButtons(
                title = "File impor",
                canPlay = tracks.isNotEmpty(),
                onPlay = { actions.playImportedTracks(tracks) },
                onShuffle = { actions.shuffleImportedTracks(tracks) },
            )
        }
        if (tracks.isEmpty()) {
            LibraryEmptyState(
                message = "Belum ada file audio impor.",
                actionLabel = if (importController.busy) "Menyalin file…" else "Impor file audio",
                actionEnabled = !importController.busy,
                onAction = importController::launchImport,
            )
            importController.message?.let { ImportAudioStatusMessage(it) }
        } else {
            ImportAudioButton(controller = importController)
            for ((index, track) in tracks.withIndex()) {
                TrackRow(
                    track = track,
                    favorite = track.id in library.favoriteIds,
                    onClick = { actions.playImportedTracks(tracks, startIndex = index) },
                ) {
                    TrackMenu(track, library, actions)
                }
            }
        }
    }
}
