package senada.app.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import senada.app.AdaptiveRow
import senada.app.ImportAudioButton
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.TrackMenu
import senada.app.TrackRow
import senada.app.favoritesTab
import senada.app.historyTab
import senada.app.minTouchTarget
import senada.app.playlistsTab
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
) {
    item(key = "library-filters") {
        LibraryFilterChips(
            selected = section,
            onSelect = { next ->
                if (openedPlaylist != null && next != LibraryFilter.Playlists) onOpenPlaylist(null)
                onSection(next)
            },
        )
    }
    when (section) {
        LibraryFilter.Favorites -> favoritesTab(library, actions, onLoadMoreFavorites)
        LibraryFilter.Playlists -> playlistsTab(
            library = library,
            opened = openedPlaylist,
            actions = actions,
            onOpen = onOpenPlaylist,
            onLoadMore = onLoadMorePlaylist,
        )
        LibraryFilter.History -> historyTab(library, actions)
        LibraryFilter.Imported -> importedTracksTab(library, actions)
    }
}

/** Deret chip filter pil yang dapat digeser mendatar agar tetap rapi pada skala font besar. */
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
            val active = filter == selected
            Surface(
                shape = CircleShape,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Box(
                    modifier = Modifier
                        .minTouchTarget()
                        .selectable(selected = active, role = Role.Tab, onClick = { onSelect(filter) })
                        .semantics { stateDescription = if (active) "Dipilih" else "Tidak dipilih" }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        filter.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
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
    val title = if (tracks.isNotEmpty()) "File impor (${tracks.size})" else "File impor"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdaptiveRow(
            modifier = Modifier.fillMaxWidth(),
            minPrimaryWidth = 140.dp,
            primary = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
            },
            trailing = {
                if (tracks.isNotEmpty()) {
                    TextButton(
                        onClick = { actions.playAll(tracks) },
                        modifier = Modifier.minTouchTarget(),
                    ) { Text("Putar semua") }
                }
            },
        )
        ImportAudioButton(onImported = actions::addImportedTrack)
        if (tracks.isEmpty()) {
            Text(
                "Belum ada file audio. Impor lagu dari penyimpanan ponsel.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            for ((index, track) in tracks.withIndex()) {
                TrackRow(
                    track = track,
                    favorite = track.id in library.favoriteIds,
                    onClick = { actions.playAll(tracks, startIndex = index) },
                ) {
                    TrackMenu(track, library, actions)
                }
            }
        }
    }
}
