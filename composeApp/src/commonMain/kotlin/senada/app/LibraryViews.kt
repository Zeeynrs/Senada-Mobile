package senada.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import senada.app.nowplaying.PlaySource
import senada.app.nowplaying.PlaySourceState
import senada.app.shell.SenadaIcons
import senada.app.tabs.CatalogPlaylistSaveOutcome
import senada.app.tabs.saveCatalogPlaylist
import senada.app.theme.SenadaPalette
import senada.catalog.artistChoicesFor
import senada.catalog.canOpenArtist
import senada.library.LibraryRepository
import senada.library.Playlist
import senada.library.PlaylistImporter
import senada.library.PlaylistSummary
import senada.library.recordPlaylistPlay
import senada.local.ImportedAudioStorage
import senada.local.syncImportedAudio
import senada.model.Track
import senada.playback.PlaybackController

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
    val favoriteDurationMs: Long? = null,
)

/** Perintah library dan antrean dari layar. Penulisan database berjalan di latar dan tidak menahan UI. */
internal class LibraryActions(
    private val library: LibraryRepository,
    private val playback: PlaybackController,
    private val scope: CoroutineScope,
    private val onOpenArtist: ((Track, String) -> Unit)? = null,
) {
    val canOpenArtists: Boolean get() = onOpenArtist != null

    fun openArtist(track: Track, artistName: String) {
        onOpenArtist?.invoke(track, artistName)
    }
    fun localTracks(): Flow<List<Track>> = library.localTracks()

    suspend fun addImportedTrack(track: Track) {
        try {
            library.recordImported(track)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Kegagalan mencatat ke library tidak menghalangi pemutaran di antrean.
        }
        enqueue(track)
    }

    /** Menyelaraskan daftar file impor dengan file yang benar-benar masih ada di penyimpanan. */
    suspend fun syncImportedFiles(storage: ImportedAudioStorage) {
        try {
            syncImportedAudio(
                storage = storage,
                library = library,
                queueTracks = { playback.state.value.queue.entries.map { it.track } },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Kegagalan menyinkronkan catatan tidak boleh mengganggu tampilan Library.
        }
    }
    fun toggleFavorite(track: Track) = write { library.toggleFavorite(track) }
    fun addToPlaylist(playlistId: String, track: Track) = write { library.addToPlaylist(playlistId, listOf(track)) }
    fun createPlaylist(name: String, tracks: List<Track> = emptyList()) = write { library.createPlaylist(name, tracks) }
    fun renamePlaylist(id: String, name: String) = write { library.renamePlaylist(id, name) }
    fun deletePlaylist(id: String) = write { library.deletePlaylist(id) }
    fun moveInPlaylist(id: String, from: Int, to: Int) = write { library.movePlaylistItem(id, from, to) }
    fun removeFromPlaylist(id: String, itemId: Long) = write { library.removeFromPlaylist(id, itemId) }

    fun enqueue(track: Track) = playback.add(track)

    fun enqueueAll(tracks: List<Track>) {
        tracks.forEach(playback::add)
    }

    /** Menambahkan semua favorit ke antrean tanpa memutarnya. */
    fun enqueueAllFavorites() {
        scope.launch { enqueueAll(library.allFavorites()) }
    }

    /**
     * Memutar seluruh favorit, bukan hanya halaman yang sudah dimuat. Tanpa [startTrackId] (tombol Putar)
     * mode acak dimatikan; mengetuk lagu memulai dari lagu itu dan mempertahankan mode acak.
     */
    fun playAllFavorites(startTrackId: String? = null) {
        scope.launch {
            val tracks = library.allFavorites()
            val startIndex = if (startTrackId == null) 0 else tracks.indexOfFirst { it.id == startTrackId }
            if (startIndex >= 0) {
                playAll(tracks, PlaySource.Favorites, startIndex = startIndex, resetShuffle = startTrackId == null)
            }
        }
    }

    /** Memutar seluruh favorit dalam urutan acak. */
    fun shuffleFavorites() {
        scope.launch { playShuffled(library.allFavorites(), PlaySource.Favorites) }
    }

    /** Seluruh isi playlist, bukan hanya halaman yang sudah dimuat. */
    fun playAllOf(playlistId: String) {
        scope.launch {
            val playlist = library.playlist(playlistId).first() ?: return@launch
            val tracks = playlist.entries.map { it.track }
            if (tracks.isEmpty()) return@launch
            playAll(tracks, PlaySource.Playlist(playlist.name), resetShuffle = true)
            runCatching { library.recordPlaylistPlay(playlistId) }
        }
    }

    /** Memutar seluruh isi playlist dalam urutan acak. */
    fun shufflePlaylist(playlistId: String) {
        scope.launch {
            val playlist = library.playlist(playlistId).first() ?: return@launch
            val tracks = playlist.entries.map { it.track }
            if (tracks.isEmpty()) return@launch
            playShuffled(tracks, PlaySource.Playlist(playlist.name))
            runCatching { library.recordPlaylistPlay(playlistId) }
        }
    }

    fun playAllMostPlayed(tracks: List<Track>) {
        playAll(tracks, PlaySource.MostPlayed)
    }

    /** Menambahkan semua lagu ke akhir antrean lalu memutar mulai dari indeks [startIndex]. */
    fun playAll(
        tracks: List<Track>,
        source: PlaySource? = null,
        startIndex: Int = 0,
        resetShuffle: Boolean = false,
    ) {
        if (tracks.isEmpty()) return
        if (resetShuffle) playback.setShuffleEnabled(false)
        val firstNew = playback.state.value.queue.entries.size
        tracks.forEach(playback::add)
        PlaySourceState.current.value = source
        val target = firstNew + startIndex.coerceIn(0, tracks.lastIndex)
        playback.state.value.queue.entries.getOrNull(target)?.let { playback.playEntry(it.id) }
    }

    /**
     * Menambahkan semua lagu ke antrean lalu memutar acak menggunakan mode acak [PlaybackController]
     * tanpa mengubah implementasi controller.
     */
    fun playShuffled(tracks: List<Track>, source: PlaySource? = null, random: Random = Random.Default) {
        if (tracks.isEmpty()) return
        val queueBefore = playback.state.value.queue
        if (queueBefore.entries.isEmpty()) {
            playback.setShuffleEnabled(true)
            tracks.forEach(playback::add)
            val first = playback.state.value.queue.playOrder.firstOrNull() ?: return
            PlaySourceState.current.value = source
            playback.playEntry(first.id)
            return
        }
        val firstNew = queueBefore.entries.size
        tracks.forEach(playback::add)
        val newEntries = playback.state.value.queue.entries.drop(firstNew)
        val picked = newEntries.randomOrNull(random) ?: return
        PlaySourceState.current.value = source
        playback.playEntry(picked.id)
        if (playback.state.value.queue.shuffleEnabled) {
            playback.setShuffleEnabled(false)
        }
        playback.setShuffleEnabled(true)
    }

    fun createPlaylistImporter(): PlaylistImporter = PlaylistImporter(
        library = library,
        youTubePlaylists = SenadaSources.youTubePlaylists,
        spotifyPlaylists = SenadaSources.spotifyPlaylists,
        candidateSearcher = SenadaSources.candidateSearcher,
    )

    /** Menyimpan daftar lagu ke playlist baru di library dengan nama unik bila sudah ada. */
    suspend fun savePlaylistToLibrary(playlistId: String, name: String, tracks: List<Track>): CatalogPlaylistSaveOutcome =
        saveCatalogPlaylist(library, playlistId, name, tracks)

    private fun write(block: suspend () -> Unit) {
        scope.launch { runCatching { block() } }
    }
}

/** Menu ⋯ untuk antrean, favorit, artis, playlist, dan aksi khusus daftar lewat [extra]. */
@Composable
internal fun TrackMenu(
    track: Track,
    library: LibraryState,
    actions: LibraryActions,
    onEnqueue: () -> Unit = { actions.enqueue(track) },
    extra: @Composable ColumnScope.(close: () -> Unit) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var pickingArtist by remember { mutableStateOf(false) }
    val favorite = track.id in library.favoriteIds
    val artistChoices = remember(track, actions.canOpenArtists) {
        if (actions.canOpenArtists && canOpenArtist(track)) artistChoicesFor(track) else emptyList()
    }
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
            DropdownMenuItem(
                text = { Text("Tambah ke antrean") },
                onClick = {
                    open = false
                    onEnqueue()
                },
                modifier = Modifier.minTouchTarget(),
            )
            DropdownMenuItem(text = { Text(if (favorite) "Hapus dari favorit" else "Tambah ke favorit") }, onClick = {
                open = false
                actions.toggleFavorite(track)
            })
            if (artistChoices.isNotEmpty()) {
                DropdownMenuItem(text = { Text("Buka artis") }, onClick = {
                    open = false
                    if (artistChoices.size == 1) {
                        actions.openArtist(track, artistChoices.first())
                    } else {
                        pickingArtist = true
                    }
                })
            }
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
    if (pickingArtist) {
        ArtistPickerDialog(
            artists = artistChoices,
            onPick = { chosen ->
                pickingArtist = false
                actions.openArtist(track, chosen)
            },
            onDismiss = { pickingArtist = false },
        )
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

/** Satu daftar lagu library dengan menu ⋯. */
private fun LazyListScope.trackList(
    keyPrefix: String,
    tracks: List<Track>,
    library: LibraryState,
    actions: LibraryActions,
    onPlay: (index: Int) -> Unit,
) {
    itemsIndexed(tracks, key = { _, track -> "$keyPrefix-${track.id}" }) { index, track ->
        TrackRow(
            track = track,
            favorite = track.id in library.favoriteIds,
            onClick = { onPlay(index) },
        ) {
            TrackMenu(track, library, actions)
        }
    }
}

internal fun LazyListScope.favoritesTab(library: LibraryState, actions: LibraryActions, onLoadMore: () -> Unit) {
    val favorites = library.favorites
    item(key = "favorites-header") {
        val accent = SenadaPalette.Favorite
        val containerColor = accent.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
        val totalDurationMs = library.favoriteDurationMs
            ?: sumKnownDurationMs(favorites).takeIf { favorites.size == library.favoriteTotal }
        val meta = favoritesCollectionMeta(trackCount = library.favoriteTotal, totalDurationMs = totalDurationMs)
        val menuItems = remember(library.favoriteTotal) {
            collectionMenuItems(CollectionKind.Favorites, hasTracks = library.favoriteTotal > 0)
        }
        CollectionHeader(
            title = "Favorit",
            meta = meta,
            cover = CollectionCover(
                fallbackIcon = SenadaIcons.HeartFilled,
                containerColor = containerColor,
                iconColor = accessibleAccent(accent, containerColor),
            ),
            menuItems = menuItems,
            onMenuAction = { action ->
                if (action == CollectionMenuAction.EnqueueAll) actions.enqueueAllFavorites()
            },
            canPlay = library.favoriteTotal > 0,
            onPlay = { actions.playAllFavorites() },
            onShuffle = { actions.shuffleFavorites() },
        )
    }
    if (library.favoriteTotal == 0) {
        item {
            Text(
                "Belum ada favorit. Buka menu ⋯ pada lagu untuk menambahkannya.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    trackList("favorite", favorites, library, actions) { index ->
        actions.playAllFavorites(startTrackId = favorites[index].id)
    }
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
    trackList("history", library.history, library, actions) { index ->
        actions.playAll(library.history, startIndex = index)
    }
}

/** Daftar playlist, atau isi satu playlist bila [opened] tidak null. */
internal fun LazyListScope.playlistsTab(
    library: LibraryState,
    opened: Playlist?,
    actions: LibraryActions,
    onOpen: (String?) -> Unit,
    onLoadMore: () -> Unit,
    reordering: Boolean = false,
    onToggleReorder: () -> Unit = {},
) {
    if (opened == null) {
        item { PlaylistListHeader(actions, onOpen = { id -> onOpen(id) }) }
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

    item(key = "playlist-header-${opened.id}") {
        PlaylistHeader(
            playlist = opened,
            actions = actions,
            reordering = reordering,
            onToggleReorder = onToggleReorder,
            onClose = { onOpen(null) },
        )
    }
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
        TrackRow(
            track = entry.track,
            favorite = entry.track.id in library.favoriteIds,
            onClick = {
                actions.playAll(opened.entries.map { it.track }, PlaySource.Playlist(opened.name), startIndex = index)
            },
        ) {
            if (reordering) {
                IconButton(
                    onClick = { actions.moveInPlaylist(opened.id, index, index - 1) },
                    enabled = index > 0,
                    modifier = Modifier.minTouchTarget().semantics {
                        contentDescription = "Pindah ${entry.track.title} ke atas"
                    },
                ) {
                    Icon(imageVector = SenadaIcons.ChevronUp, contentDescription = null)
                }
                IconButton(
                    onClick = { actions.moveInPlaylist(opened.id, index, index + 1) },
                    enabled = index < opened.entryCount - 1,
                    modifier = Modifier.minTouchTarget().semantics {
                        contentDescription = "Pindah ${entry.track.title} ke bawah"
                    },
                ) {
                    Icon(imageVector = SenadaIcons.ChevronDown, contentDescription = null)
                }
            } else {
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
    }
    loadMoreItem("playlist-more-${opened.id}", loaded = opened.entries.size, total = opened.entryCount, onLoadMore)
}

@Composable
private fun PlaylistListHeader(actions: LibraryActions, onOpen: (String) -> Unit) {
    var naming by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    AdaptiveRow(
        modifier = Modifier.fillMaxWidth(),
        minPrimaryWidth = 140.dp,
        primary = {
            Text("Playlist", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        },
        trailing = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { importing = true }, modifier = Modifier.minTouchTarget()) {
                    Text("Impor playlist")
                }
                TextButton(onClick = { naming = true }, modifier = Modifier.minTouchTarget()) {
                    Text("Playlist baru")
                }
            }
        },
    )
    if (naming) {
        NameDialog(title = "Playlist baru", initial = "", confirmLabel = "Buat", onDismiss = { naming = false }) { name ->
            naming = false
            actions.createPlaylist(name)
        }
    }
    if (importing) {
        PlaylistImportDialog(
            importer = remember(actions) { actions.createPlaylistImporter() },
            onDismiss = { importing = false },
            onOpenPlaylist = { playlistId ->
                importing = false
                onOpen(playlistId)
            },
        )
    }
}

@Composable
private fun PlaylistHeader(
    playlist: Playlist,
    actions: LibraryActions,
    reordering: Boolean,
    onToggleReorder: () -> Unit,
    onClose: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(playlist.id, playlist.entryCount <= 1) {
        if (reordering && playlist.entryCount <= 1) onToggleReorder()
    }
    val menuItems = remember(playlist.entryCount) {
        collectionMenuItems(CollectionKind.LibraryPlaylist, hasTracks = playlist.entryCount > 0)
    }
    val coverArtwork = playlist.entries.firstOrNull()?.track?.artworkUrl?.takeIf { it.isNotBlank() }
    CollectionHeader(
        title = playlist.name,
        meta = playlistCollectionMeta(trackCount = playlist.entryCount, totalDurationMs = playlist.totalDurationMs),
        cover = CollectionCover(
            artworkUrl = coverArtwork,
            fallbackIcon = SenadaIcons.Library,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        menuItems = menuItems,
        onMenuAction = { action ->
            when (action) {
                CollectionMenuAction.RenamePlaylist -> renaming = true
                CollectionMenuAction.DeletePlaylist -> deleting = true
                else -> Unit
            }
        },
        canPlay = playlist.entryCount > 0,
        onPlay = { actions.playAllOf(playlist.id) },
        onShuffle = { actions.shufflePlaylist(playlist.id) },
        backLabel = "semua playlist",
        onBack = onClose,
        chips = {
            FilterChip(
                selected = reordering,
                onClick = onToggleReorder,
                enabled = playlist.entryCount > 1,
                label = { Text("Urutkan") },
                modifier = Modifier.minTouchTarget().semantics {
                    stateDescription = if (reordering) "Sedang mengurutkan" else "Tidak aktif"
                },
            )
        },
    )
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
