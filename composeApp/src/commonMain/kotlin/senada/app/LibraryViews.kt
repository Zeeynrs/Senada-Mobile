package senada.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
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
import senada.app.theme.LocalSenadaLook
import senada.app.theme.SenadaPalette
import senada.catalog.artistChoicesFor
import senada.catalog.canOpenArtist
import senada.catalog.youtube.resizeGoogleArtwork
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
    val playlistCovers: Map<String, List<String>> = emptyMap(),
)

/** Pilihan sampul 56 dp untuk satu baris playlist di daftar. */
internal sealed interface PlaylistCover {
    data object Placeholder : PlaylistCover
    data class Single(val artworkUrl: String) : PlaylistCover
    data class Mosaic(val artworkUrls: List<String>) : PlaylistCover
}

/** Menyusun subjudul baris playlist dengan format seragam "Playlist · N lagu". */
internal fun playlistRowSubtitle(trackCount: Int): String =
    "Playlist · ${trackCount.coerceAtLeast(0)} lagu"

/** Memilih mozaik 2x2 bila ada 4+ sampul berbeda, satu sampul bila 1–3, atau placeholder bila kosong. */
internal fun selectPlaylistCover(artworkUrls: List<String?>): PlaylistCover {
    val distinct = artworkUrls.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct()
    return when {
        distinct.size >= 4 -> PlaylistCover.Mosaic(distinct.take(4))
        distinct.isNotEmpty() -> PlaylistCover.Single(distinct.first())
        else -> PlaylistCover.Placeholder
    }
}

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

    /** Memutar riwayat dari yang terbaru atau mulai dari indeks lagu yang diketuk. */
    fun playHistory(tracks: List<Track>, startIndex: Int? = null) {
        playAll(
            tracks = tracks,
            source = PlaySource.History,
            startIndex = startIndex ?: 0,
            resetShuffle = startIndex == null,
        )
    }

    /** Memutar seluruh daftar riwayat dalam urutan acak. */
    fun shuffleHistory(tracks: List<Track>) {
        playShuffled(tracks, PlaySource.History)
    }

    /** Memutar semua file impor atau mulai dari indeks lagu yang diketuk. */
    fun playImportedTracks(tracks: List<Track>, startIndex: Int? = null) {
        playAll(
            tracks = tracks,
            source = PlaySource.Imported,
            startIndex = startIndex ?: 0,
            resetShuffle = startIndex == null,
        )
    }

    /** Memutar seluruh file impor dalam urutan acak. */
    fun shuffleImportedTracks(tracks: List<Track>) {
        playShuffled(tracks, PlaySource.Imported)
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
    onRemoveFromQueue: (() -> Unit)? = null,
    trigger: (@Composable (open: () -> Unit) -> Unit)? = null,
    leadingItems: @Composable (close: () -> Unit) -> Unit = {},
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
        if (trigger != null) {
            trigger { open = true }
        } else {
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
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            leadingItems { open = false }
            if (onRemoveFromQueue != null) {
                DropdownMenuItem(
                    text = { Text("Hapus dari antrean") },
                    onClick = {
                        open = false
                        onRemoveFromQueue()
                    },
                    modifier = Modifier.minTouchTarget().semantics { contentDescription = "Hapus ${track.title} dari antrean" },
                )
            }
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

/** Tampilan kosong seragam bergaya kartu v2 untuk tiap chip di tab Library: kalimat singkat + aksi relevan. */
@Composable
internal fun LibraryEmptyState(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
) {
    BrowseNoticeCard(
        eyebrow = "Belum ada isi",
        message = message,
        polite = false,
        actionLabel = actionLabel,
        actionEnabled = actionEnabled,
        primaryAction = true,
        onAction = onAction,
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

internal fun LazyListScope.favoritesTab(
    library: LibraryState,
    actions: LibraryActions,
    onLoadMore: () -> Unit,
    onOpenSearch: () -> Unit = {},
) {
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
            eyebrow = "Koleksi favorit",
        )
    }
    if (library.favoriteTotal == 0) {
        item(key = "favorites-empty") {
            LibraryEmptyState(
                message = "Belum ada lagu favorit.",
                actionLabel = "Cari lagu",
                onAction = onOpenSearch,
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

internal fun LazyListScope.historyTab(
    library: LibraryState,
    actions: LibraryActions,
    onOpenSearch: () -> Unit = {},
) {
    item(key = "history-header") {
        val meta = remember(library.history) {
            collectionMeta(
                trackCount = library.history.size,
                totalDurationMs = sumKnownDurationMs(library.history),
            )
        }
        CompactCollectionHeader(
            title = "Riwayat",
            meta = meta,
            eyebrow = "Baru diputar",
        ) {
            CollectionPlayButtons(
                title = "Riwayat",
                canPlay = library.history.isNotEmpty(),
                onPlay = { actions.playHistory(library.history) },
                onShuffle = { actions.shuffleHistory(library.history) },
            )
        }
    }
    if (library.history.isEmpty()) {
        item(key = "history-empty") {
            LibraryEmptyState(
                message = "Belum ada riwayat pemutaran.",
                actionLabel = "Cari lagu",
                onAction = onOpenSearch,
            )
        }
    }
    trackList("history", library.history, library, actions) { index ->
        actions.playHistory(library.history, startIndex = index)
    }
}

/** Daftar playlist, atau isi satu playlist bila [opened] tidak null. */
internal fun LazyListScope.playlistsTab(
    library: LibraryState,
    opened: Playlist?,
    actions: LibraryActions,
    onOpen: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onOpenSearch: () -> Unit = {},
    reordering: Boolean = false,
    onToggleReorder: () -> Unit = {},
) {
    if (opened == null) {
        item(key = "playlist-list-header") {
            PlaylistListHeader(
                playlistCount = library.playlists.size,
                actions = actions,
                onOpen = { id -> onOpen(id) },
            )
        }
        if (library.playlists.isEmpty()) {
            item(key = "playlist-list-empty") {
                var naming by remember { mutableStateOf(false) }
                LibraryEmptyState(
                    message = "Belum ada playlist.",
                    actionLabel = "Playlist baru",
                    onAction = { naming = true },
                )
                if (naming) {
                    NewPlaylistDialog(actions = actions, onDismiss = { naming = false })
                }
            }
        }
        items(library.playlists, key = { "playlist-${it.id}" }) { playlist ->
            PlaylistRow(
                playlist = playlist,
                cover = selectPlaylistCover(library.playlistCovers[playlist.id].orEmpty()),
                actions = actions,
                onOpen = { onOpen(playlist.id) },
            )
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
        item(key = "playlist-empty-${opened.id}") {
            LibraryEmptyState(
                message = "Playlist ini masih kosong.",
                actionLabel = "Cari lagu",
                onAction = onOpenSearch,
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
private fun PlaylistRow(
    playlist: PlaylistSummary,
    cover: PlaylistCover,
    actions: LibraryActions,
    onOpen: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    SenadaCard {
        AdaptiveRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(1.dp)
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            minPrimaryWidth = 180.dp,
            horizontalSpacing = 12.dp,
            primary = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minTouchTarget()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClickLabel = "Buka", role = Role.Button, onClick = onOpen)
                        .semantics { contentDescription = "Buka playlist ${playlist.name}" }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PlaylistRowCover(cover)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = playlist.name,
                            style = browseRowTitleStyle(),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = playlistRowSubtitle(playlist.trackCount),
                            style = browseRowSubtitleStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            trailing = {
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.minTouchTarget().semantics {
                            contentDescription = "Buka menu playlist ${playlist.name}"
                        },
                    ) {
                        Icon(
                            imageVector = SenadaIcons.More,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Ganti nama") },
                            onClick = {
                                menuOpen = false
                                renaming = true
                            },
                            modifier = Modifier.minTouchTarget(),
                        )
                        DropdownMenuItem(
                            text = { Text("Hapus playlist") },
                            onClick = {
                                menuOpen = false
                                deleting = true
                            },
                            modifier = Modifier.minTouchTarget(),
                        )
                    }
                }
            },
        )
    }
    if (renaming) {
        RenamePlaylistDialog(
            currentName = playlist.name,
            onDismiss = { renaming = false },
            onRename = { name ->
                renaming = false
                actions.renamePlaylist(playlist.id, name)
            },
        )
    }
    if (deleting) {
        DeletePlaylistDialog(
            playlistName = playlist.name,
            onDismiss = { deleting = false },
            onDelete = {
                deleting = false
                actions.deletePlaylist(playlist.id)
            },
        )
    }
}

@Composable
private fun PlaylistRowCover(cover: PlaylistCover) {
    val coverShape = RoundedCornerShape(LocalSenadaLook.current.listCoverRadius)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(coverShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), coverShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = SenadaIcons.Library,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp),
        )
        when (cover) {
            PlaylistCover.Placeholder -> Unit
            is PlaylistCover.Single -> {
                CoverArtworkImage(
                    url = cover.artworkUrl,
                    modifier = Modifier.matchParentSize(),
                )
            }
            is PlaylistCover.Mosaic -> {
                Column(modifier = Modifier.matchParentSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        CoverArtworkImage(cover.artworkUrls[0], Modifier.weight(1f).fillMaxHeight())
                        CoverArtworkImage(cover.artworkUrls[1], Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        CoverArtworkImage(cover.artworkUrls[2], Modifier.weight(1f).fillMaxHeight())
                        CoverArtworkImage(cover.artworkUrls[3], Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverArtworkImage(url: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = remember(url) { resizeGoogleArtwork(url, 120) },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
private fun CompactActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (emphasized) colors.onSurface else colors.surfaceContainerHigh,
        contentColor = if (emphasized) colors.background else colors.onSurface,
        border = if (emphasized) null else BorderStroke(1.dp, colors.outline),
        modifier = Modifier.minTouchTarget().semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlaylistListHeader(playlistCount: Int, actions: LibraryActions, onOpen: (String) -> Unit) {
    var naming by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    CompactCollectionHeader(
        title = "Playlist",
        meta = playlistListMeta(playlistCount),
        eyebrow = "Daftar putar",
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactActionChip(
                label = "Playlist baru",
                icon = SenadaIcons.Add,
                onClick = { naming = true },
                emphasized = true,
            )
            CompactActionChip(
                label = "Impor playlist",
                icon = SenadaIcons.Import,
                onClick = { importing = true },
            )
        }
    }
    if (naming) {
        NewPlaylistDialog(actions = actions, onDismiss = { naming = false })
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
private fun NewPlaylistDialog(actions: LibraryActions, onDismiss: () -> Unit) {
    NameDialog(title = "Playlist baru", initial = "", confirmLabel = "Buat", onDismiss = onDismiss) { name ->
        onDismiss()
        actions.createPlaylist(name)
    }
}

@Composable
private fun RenamePlaylistDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    NameDialog(
        title = "Ganti nama",
        initial = currentName,
        confirmLabel = "Simpan",
        onDismiss = onDismiss,
        onConfirm = onRename,
    )
}

@Composable
private fun DeletePlaylistDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus playlist?", modifier = Modifier.semantics { heading() }) },
        text = { Text("\"$playlistName\" dan urutan lagunya akan dihapus. Lagu di favorit dan riwayat tetap ada.") },
        confirmButton = {
            TextButton(
                onClick = onDelete,
                modifier = Modifier.minTouchTarget(),
            ) { Text("Hapus playlist") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minTouchTarget()) { Text("Batal") }
        },
    )
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
            SenadaFilterPill(
                label = "Urutkan",
                selected = reordering,
                enabled = playlist.entryCount > 1,
                role = Role.Button,
                stateLabel = if (reordering) "Sedang mengurutkan" else "Tidak aktif",
                onClick = onToggleReorder,
            )
        },
        eyebrow = "Playlist",
    )
    if (renaming) {
        RenamePlaylistDialog(
            currentName = playlist.name,
            onDismiss = { renaming = false },
            onRename = { name ->
                renaming = false
                actions.renamePlaylist(playlist.id, name)
            },
        )
    }
    if (deleting) {
        DeletePlaylistDialog(
            playlistName = playlist.name,
            onDismiss = { deleting = false },
            onDelete = {
                deleting = false
                onClose()
                actions.deletePlaylist(playlist.id)
            },
        )
    }
}
