package senada.app.tabs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import senada.app.playlistsTab
import senada.app.rememberAudioImportController
import senada.app.sumKnownDurationMs
import senada.library.Playlist
import senada.model.Track
import senada.platform.importedAudioStorage

/** Pilihan filter di dalam satu tab Library. */
internal enum class LibraryFilter(val label: String) {
    Favorites("Favorit"),
    Playlists("Playlist"),
    History("Riwayat"),
    Imported("File impor"),
}

/** Tab Library: satu halaman dengan chip filter Favorit, Playlist, Riwayat, dan File impor. */
internal fun LazyListScope.libraryTab(
    section: LibraryFilter,
    onSection: (LibraryFilter) -> Unit,
    library: LibraryState,
    openedPlaylist: Playlist?,
    actions: LibraryActions,
    onOpenPlaylist: (String?) -> Unit,
    onLoadMoreFavorites: () -> Unit,
    onLoadMorePlaylist: () -> Unit,
    onOpenSearch: () -> Unit = {},
    reorderingPlaylist: Boolean = false,
    onToggleReorderPlaylist: () -> Unit = {},
) {
    if (openedPlaylist == null) {
        item(key = "library-filters") {
            LibraryFilterChips(
                selected = section,
                onSelect = { next ->
                    onSection(next)
                },
            )
        }
    }
    when (section) {
        LibraryFilter.Favorites -> favoritesTab(library, actions, onLoadMoreFavorites, onOpenSearch)
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

/** Deret chip filter pil v2 yang dapat digeser mendatar agar tetap rapi pada skala font besar. */
@Composable
private fun LibraryFilterChips(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (filter in LibraryFilter.entries) {
            SenadaFilterPill(
                label = filter.label,
                selected = filter == selected,
                onClick = { onSelect(filter) },
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
