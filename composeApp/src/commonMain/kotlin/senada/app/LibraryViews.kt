package senada.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import senada.app.nowplaying.PlaySource
import senada.app.nowplaying.PlaySourceState
import senada.app.shell.SenadaIcons
import senada.app.tabs.LibraryFilter
import senada.library.LibraryRepository
import senada.library.Playlist
import senada.library.PlaylistSummary
import senada.library.recordPlaylistPlay
import senada.model.Track
import senada.playback.PlaybackController

/** Alias sementara agar AppShell.kt tetap tidak berubah saat integrasi paralel Fase 4–5. */
internal typealias AppTab = LibraryFilter

/** Ukuran halaman daftar library; daftar bertambah sebanyak ini saat ujungnya terlihat. */
internal const val LIBRARY_PAGE_SIZE = 50

/**
 * Isi library yang sedang tampil, dikumpulkan dari [LibraryRepository]. [favorites] hanya halaman yang
 * sudah dimuat; [favoriteIds] berisi semua ID favorit untuk penanda hati.
 */
internal data class LibraryState(
    val favorites: List<Track> = emptyList(),
    val favoriteTotal: Int = 0,
    val favoriteIds: Set<String> = emptySet(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val history: List<Track> = emptyList(),
)

/** Perintah library dan antrean dari layar. Penulisan database berjalan di latar dan tidak menahan UI. */
internal class LibraryActions(
    private val library: LibraryRepository,
    private val playback: PlaybackController,
    private val scope: CoroutineScope,
) {
    fun localTracks(): Flow<List<Track>> = library.localTracks()
    fun toggleFavorite(track: Track) = write { library.toggleFavorite(track) }
    fun addToPlaylist(playlistId: String, track: Track) = write { library.addToPlaylist(playlistId, listOf(track)) }
    fun createPlaylist(name: String, tracks: List<Track> = emptyList()) = write { library.createPlaylist(name, tracks) }
    fun renamePlaylist(id: String, name: String) = write { library.renamePlaylist(id, name) }
    fun deletePlaylist(id: String) = write { library.deletePlaylist(id) }
    fun moveInPlaylist(id: String, from: Int, to: Int) = write { library.movePlaylistItem(id, from, to) }
    fun removeFromPlaylist(id: String, itemId: Long) = write { library.removeFromPlaylist(id, itemId) }

    fun enqueue(track: Track) = playback.add(track)

    /** Seluruh favorit, bukan hanya halaman yang sudah dimuat. */
    fun playAllFavorites() {
        scope.launch { playAll(library.allFavorites(), PlaySource.Favorites) }
    }

    /** Seluruh isi playlist, bukan hanya halaman yang sudah dimuat. */
    fun playAllOf(playlistId: String) {
        scope.launch {
            val playlist = library.playlist(playlistId).first() ?: return@launch
            val tracks = playlist.entries.map { it.track }
            if (tracks.isEmpty()) return@launch
            playAll(tracks, PlaySource.Playlist(playlist.name))
            runCatching { library.recordPlaylistPlay(playlistId) }
        }
    }

    fun playAllMostPlayed(tracks: List<Track>) {
        playAll(tracks, PlaySource.MostPlayed)
    }

    /** Menambahkan semua lagu ke akhir antrean lalu memutar yang pertama. */
    fun playAll(tracks: List<Track>, source: PlaySource? = null) {
        if (tracks.isEmpty()) return
        val firstNew = playback.state.value.queue.entries.size
        tracks.forEach(playback::add)
        PlaySourceState.current.value = source
        playback.state.value.queue.entries.getOrNull(firstNew)?.let { playback.playEntry(it.id) }
    }

    private fun write(block: suspend () -> Unit) {
        scope.launch { runCatching { block() } }
    }
}

/** Menu ⋯ pada lagu: favorit dan tambah ke playlist, ditambah aksi khusus daftar lewat [extra]. */
@Composable
internal fun TrackMenu(
    track: Track,
    library: LibraryState,
    actions: LibraryActions,
    extra: @Composable ColumnScope.(close: () -> Unit) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val favorite = track.id in library.favoriteIds
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.minTouchTarget().semantics { contentDescription = "Buka menu ${track.title}" },
        ) {
            Icon(
                imageVector = SenadaIcons.More,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(if (favorite) "Hapus dari favorit" else "Tambah ke favorit") }, onClick = {
                open = false
                actions.toggleFavorite(track)
            })
            library.playlists.forEach { playlist ->
                DropdownMenuItem(text = { Text("Tambah ke ${playlist.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = {
                    open = false
                    actions.addToPlaylist(playlist.id, track)
                })
            }
            DropdownMenuItem(text = { Text("Playlist baru…") }, onClick = {
                open = false
                naming = true
            })
            extra { open = false }
        }
    }
    if (naming) {
        NameDialog(title = "Playlist baru", initial = "", confirmLabel = "Buat", onDismiss = { naming = false }) { name ->
            naming = false
            actions.createPlaylist(name, listOf(track))
        }
    }
}

@Composable
internal fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Nama playlist", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.minTouchTarget(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minTouchTarget()) { Text("Batal") }
        },
    )
}

@Composable
private fun SectionHeader(title: String, onPlayAll: (() -> Unit)?) {
    AdaptiveRow(
        modifier = Modifier.fillMaxWidth(),
        minPrimaryWidth = 140.dp,
        primary = {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        },
        trailing = {
            if (onPlayAll != null) {
                TextButton(onClick = onPlayAll, modifier = Modifier.minTouchTarget()) { Text("Putar semua") }
            }
        },
    )
}

/** Satu daftar lagu library dengan tombol tambah ke antrean dan menu ⋯. */
private fun LazyListScope.trackList(
    keyPrefix: String,
    tracks: List<Track>,
    library: LibraryState,
    actions: LibraryActions,
) {
    items(tracks, key = { "$keyPrefix-${it.id}" }) { track ->
        TrackRow(track, favorite = track.id in library.favoriteIds) {
            FilledTonalButton(
                onClick = { actions.enqueue(track) },
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
}

internal fun LazyListScope.favoritesTab(library: LibraryState, actions: LibraryActions, onLoadMore: () -> Unit) {
    val favorites = library.favorites
    val title = if (library.favoriteTotal > 0) "Favorit (${library.favoriteTotal})" else "Favorit"
    item { SectionHeader(title, onPlayAll = actions::playAllFavorites.takeIf { library.favoriteTotal > 0 }) }
    if (library.favoriteTotal == 0) {
        item {
            Text(
                "Belum ada favorit. Buka menu ⋯ pada lagu untuk menambahkannya.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    trackList("favorite", favorites, library, actions)
    loadMoreItem("favorite-more", loaded = favorites.size, total = library.favoriteTotal, onLoadMore)
}

/** Saat baris ini mulai terlihat di ujung daftar, halaman berikutnya diminta. */
private fun LazyListScope.loadMoreItem(key: String, loaded: Int, total: Int, onLoadMore: () -> Unit) {
    if (loaded >= total) return
    item(key = key) {
        LaunchedEffect(loaded) { onLoadMore() }
        Text("Memuat…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun LazyListScope.historyTab(library: LibraryState, actions: LibraryActions) {
    item { SectionHeader("Riwayat", onPlayAll = null) }
    if (library.history.isEmpty()) {
        item {
            Text(
                "Riwayat masih kosong. Lagu yang diputar dari antrean muncul di sini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    trackList("history", library.history, library, actions)
}

/** Daftar playlist, atau isi satu playlist bila [opened] tidak null. */
internal fun LazyListScope.playlistsTab(
    library: LibraryState,
    opened: Playlist?,
    actions: LibraryActions,
    onOpen: (String?) -> Unit,
    onLoadMore: () -> Unit,
) {
    if (opened == null) {
        item { PlaylistListHeader(actions) }
        if (library.playlists.isEmpty()) {
            item {
                Text(
                    "Belum ada playlist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(library.playlists, key = { "playlist-${it.id}" }) { playlist ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                    minPrimaryWidth = 160.dp,
                    primary = {
                        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                            Text(
                                playlist.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${playlist.trackCount} lagu",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailing = {
                        TextButton(
                            onClick = { onOpen(playlist.id) },
                            modifier = Modifier.minTouchTarget().semantics {
                                contentDescription = "Buka playlist ${playlist.name}"
                            },
                        ) { Text("Buka") }
                    },
                )
            }
        }
        return
    }

    item(key = "playlist-header-${opened.id}") { PlaylistHeader(opened, actions, onClose = { onOpen(null) }) }
    if (opened.entryCount == 0) {
        item {
            Text(
                "Playlist ini masih kosong. Tambahkan lagu lewat menu ⋯.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    itemsIndexed(opened.entries, key = { _, entry -> "playlist-item-${entry.itemId}" }) { index, entry ->
        TrackRow(entry.track, favorite = entry.track.id in library.favoriteIds) {
            FilledTonalButton(
                onClick = { actions.enqueue(entry.track) },
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.minTouchTarget().semantics {
                    contentDescription = "Tambah ${entry.track.title} ke antrean"
                },
            ) { Text("Tambah") }
            TrackMenu(entry.track, library, actions) { close ->
                if (index > 0) {
                    DropdownMenuItem(text = { Text("Pindah ke atas") }, onClick = {
                        close()
                        actions.moveInPlaylist(opened.id, index, index - 1)
                    })
                }
                // Urutan memakai indeks di seluruh playlist; halaman yang dimuat selalu awal playlist.
                if (index < opened.entryCount - 1) {
                    DropdownMenuItem(text = { Text("Pindah ke bawah") }, onClick = {
                        close()
                        actions.moveInPlaylist(opened.id, index, index + 1)
                    })
                }
                DropdownMenuItem(text = { Text("Hapus dari playlist") }, onClick = {
                    close()
                    actions.removeFromPlaylist(opened.id, entry.itemId)
                })
            }
        }
    }
    loadMoreItem("playlist-more-${opened.id}", loaded = opened.entries.size, total = opened.entryCount, onLoadMore)
}

@Composable
private fun PlaylistListHeader(actions: LibraryActions) {
    var naming by remember { mutableStateOf(false) }
    AdaptiveRow(
        modifier = Modifier.fillMaxWidth(),
        minPrimaryWidth = 140.dp,
        primary = {
            Text("Playlist", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        },
        trailing = {
            TextButton(onClick = { naming = true }, modifier = Modifier.minTouchTarget()) { Text("Playlist baru") }
        },
    )
    if (naming) {
        NameDialog(title = "Playlist baru", initial = "", confirmLabel = "Buat", onDismiss = { naming = false }) { name ->
            naming = false
            actions.createPlaylist(name)
        }
    }
}

@Composable
private fun PlaylistHeader(playlist: Playlist, actions: LibraryActions, onClose: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
            onClick = onClose,
            modifier = Modifier.minTouchTarget().semantics { contentDescription = "Kembali ke semua playlist" },
        ) { Text("‹ Semua playlist", modifier = Modifier.clearAndSetSemantics {}) }
        Text(
            playlist.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Text("${playlist.entryCount} lagu", style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                onClick = { actions.playAllOf(playlist.id) },
                enabled = playlist.entryCount > 0,
                modifier = Modifier.minTouchTarget(),
            ) { Text("Putar semua") }
            TextButton(onClick = { renaming = true }, modifier = Modifier.minTouchTarget()) { Text("Ganti nama") }
            TextButton(onClick = { deleting = true }, modifier = Modifier.minTouchTarget()) { Text("Hapus playlist") }
        }
    }
    if (renaming) {
        NameDialog(title = "Ganti nama", initial = playlist.name, confirmLabel = "Simpan", onDismiss = { renaming = false }) { name ->
            renaming = false
            actions.renamePlaylist(playlist.id, name)
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Hapus playlist?", modifier = Modifier.semantics { heading() }) },
            text = { Text("\"${playlist.name}\" dan urutan lagunya akan dihapus. Lagu di favorit dan riwayat tetap ada.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = false
                        onClose()
                        actions.deletePlaylist(playlist.id)
                    },
                    modifier = Modifier.minTouchTarget(),
                ) { Text("Hapus playlist") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }, modifier = Modifier.minTouchTarget()) { Text("Batal") }
            },
        )
    }
}
