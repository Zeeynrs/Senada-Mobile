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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
import senada.library.DEFAULT_ARTIST_SOURCE
import senada.library.FollowedArtist
import senada.library.LibraryRepository
import senada.library.Playlist
import senada.library.PlaylistImporter
import senada.library.PlaylistSummary
import senada.library.followedArtistKey
import senada.library.recordPlaylistPlay
import senada.local.ImportedAudioStorage
import senada.local.syncImportedAudio
import senada.model.Track
import senada.offline.OfflineDownloads
import senada.playback.PlaybackController
import senada.playback.SampleAudioStatus
import senada.resources.Res
import senada.resources.a11y_move_track_down
import senada.resources.a11y_move_track_up
import senada.resources.a11y_open_menu_named
import senada.resources.a11y_open_playlist_menu
import senada.resources.a11y_open_playlist_named
import senada.resources.a11y_remove_from_queue
import senada.resources.action_add_to_queue
import senada.resources.action_cancel
import senada.resources.action_create
import senada.resources.action_go_to_artist
import senada.resources.action_loading
import senada.resources.action_open
import senada.resources.action_remove_from_playlist
import senada.resources.action_remove_from_queue
import senada.resources.action_rename
import senada.resources.action_save
import senada.resources.catalog_type_playlist
import senada.resources.label_playlist_count_subtitle
import senada.resources.library_add_to_library
import senada.resources.library_empty_eyebrow
import senada.resources.library_favorites_empty_short
import senada.resources.library_favorites_eyebrow
import senada.resources.library_history_empty
import senada.resources.library_history_eyebrow
import senada.resources.library_new_playlist
import senada.resources.library_playlist_new_ellipsis
import senada.resources.library_playlists_empty_short
import senada.resources.library_playlists_eyebrow
import senada.resources.library_shortcut_favorites
import senada.resources.library_shortcut_history
import senada.resources.offline_find_songs
import senada.resources.player_like
import senada.resources.player_unlike
import senada.resources.playlist_add_to_named
import senada.resources.playlist_back_all
import senada.resources.playlist_delete_confirm
import senada.resources.playlist_delete_title
import senada.resources.playlist_empty
import senada.resources.playlist_import_title
import senada.resources.playlist_name_hint
import senada.resources.playlist_reorder_active
import senada.resources.playlist_reorder_button
import senada.resources.playlist_reorder_inactive
import senada.resources.queue_move_down
import senada.resources.queue_move_up
import senada.resources.song_count

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
    val followedArtists: List<FollowedArtist> = emptyList(),
    val followedArtistIds: Set<String> = emptySet(),
) {
    fun isArtistFollowed(catalogId: String, source: String = DEFAULT_ARTIST_SOURCE): Boolean {
        val key = followedArtistKey(catalogId, source)
        return key.isNotEmpty() && key in followedArtistIds
    }
}

/** Pilihan sampul 56 dp untuk satu baris playlist di daftar. */
internal sealed interface PlaylistCover {
    data object Placeholder : PlaylistCover
    data class Single(val artworkUrl: String) : PlaylistCover
    data class Mosaic(val artworkUrls: List<String>) : PlaylistCover
}

/** Menyusun subjudul baris playlist dengan format seragam "Playlist · N lagu". */
internal fun playlistRowSubtitle(
    trackCount: Int,
    language: AppLanguage = AppStrings.currentLanguage,
): String {
    val count = AppStrings.plural(Res.plurals.song_count, trackCount.coerceAtLeast(0), language, trackCount.coerceAtLeast(0))
    return AppStrings.get(Res.string.label_playlist_count_subtitle, language, count)
}

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
    val downloads: OfflineDownloads? = null,
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
    fun toggleFollowArtist(
        catalogId: String,
        name: String,
        artworkUrl: String? = null,
        source: String = DEFAULT_ARTIST_SOURCE,
    ) = write { library.toggleFollowArtist(catalogId, name, artworkUrl, source) }

    fun setArtistFollowed(
        catalogId: String,
        name: String,
        artworkUrl: String? = null,
        followed: Boolean,
        source: String = DEFAULT_ARTIST_SOURCE,
    ) = write { library.setArtistFollowed(catalogId, name, artworkUrl, followed, source) }

    fun refreshFollowedArtistMetadata(
        catalogId: String,
        name: String,
        artworkUrl: String? = null,
        source: String = DEFAULT_ARTIST_SOURCE,
    ) = write { library.refreshFollowedArtistMetadata(catalogId, name, artworkUrl, source) }
    fun addToPlaylist(playlistId: String, track: Track) = write { library.addToPlaylist(playlistId, listOf(track)) }
    fun createPlaylist(name: String, tracks: List<Track> = emptyList()) = write { library.createPlaylist(name, tracks) }
    fun renamePlaylist(id: String, name: String) = write { library.renamePlaylist(id, name) }
    fun deletePlaylist(id: String) = write { library.deletePlaylist(id) }
    fun moveInPlaylist(id: String, from: Int, to: Int) = write { library.movePlaylistItem(id, from, to) }
    fun removeFromPlaylist(id: String, itemId: Long) = write { library.removeFromPlaylist(id, itemId) }

    fun enqueue(track: Track) = playback.add(track)

    fun enqueueAll(tracks: List<Track>) {
        playback.addAll(tracks)
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
            playAll(tracks, PlaySource.Playlist.fromLibrary(playlist.id, playlist.name), resetShuffle = true)
            runCatching { library.recordPlaylistPlay(playlistId) }
        }
    }

    /** Memutar seluruh isi playlist dalam urutan acak. */
    fun shufflePlaylist(playlistId: String) {
        scope.launch {
            val playlist = library.playlist(playlistId).first() ?: return@launch
            val tracks = playlist.entries.map { it.track }
            if (tracks.isEmpty()) return@launch
            playShuffled(tracks, PlaySource.Playlist.fromLibrary(playlist.id, playlist.name))
            runCatching { library.recordPlaylistPlay(playlistId) }
        }
    }

    fun playlistPlaying(playlistKey: String): Flow<Boolean> = playback.state
        .map { state -> state.audio.isActive && state.currentEntry?.playlistKey == playlistKey }
        .distinctUntilChanged()

    fun togglePlaylistPlayback(playlistKey: String, onStart: () -> Unit) {
        val state = playback.state.value
        when {
            state.currentEntry?.playlistKey != playlistKey -> onStart()
            state.audio.isActive -> playback.pause()
            state.audio.status == SampleAudioStatus.Paused -> playback.resume()
            else -> onStart()
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
        playback.addAll(tracks, (source as? PlaySource.Playlist)?.key)
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
        val playlistKey = (source as? PlaySource.Playlist)?.key
        if (queueBefore.entries.isEmpty()) {
            playback.setShuffleEnabled(true)
            playback.addAll(tracks, playlistKey)
            val first = playback.state.value.queue.playOrder.firstOrNull() ?: return
            PlaySourceState.current.value = source
            playback.playEntry(first.id)
            return
        }
        val firstNew = queueBefore.entries.size
        playback.addAll(tracks, playlistKey)
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
    leadingItems: @Composable (select: (() -> Unit) -> Unit) -> Unit = {},
    extra: @Composable ColumnScope.(select: (() -> Unit) -> Unit) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var pickingArtist by remember { mutableStateOf(false) }
    val favorite = track.id in library.favoriteIds
    val artistChoices = remember(track, actions.canOpenArtists) {
        if (actions.canOpenArtists && canOpenArtist(track)) artistChoicesFor(track) else emptyList()
    }
    val language = LocalAppLanguage.current
    Box {
        if (trigger != null) {
            trigger { open = true }
        } else {
            val openMenuDesc = appString(Res.string.a11y_open_menu_named, track.title)
            IconButton(
                onClick = { open = true },
                modifier = Modifier.minTouchTarget().semantics { contentDescription = openMenuDesc },
            ) {
                Icon(
                    imageVector = SenadaIcons.More,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (open) {
            OverflowActionSheet(
                title = track.title,
                subtitle = track.artists.joinToString(),
                artwork = {
                    OverflowActionArtwork(
                        CollectionCover(artworkUrl = track.artworkUrl, fallbackIcon = SenadaIcons.MusicNote),
                    )
                },
                onDismiss = { open = false },
            ) { select ->
                leadingItems(select)
                OfflineTrackMenuItem(track, actions.downloads, select)
                if (onRemoveFromQueue != null) {
                    val removeQueueDesc = appString(Res.string.a11y_remove_from_queue, track.title)
                    OverflowActionItem(
                        label = appString(Res.string.action_remove_from_queue),
                        icon = SenadaIcons.Close,
                        modifier = Modifier.semantics { contentDescription = removeQueueDesc },
                    ) { select(onRemoveFromQueue) }
                }
                OverflowActionItem(appString(Res.string.action_add_to_queue), SenadaIcons.Queue) { select(onEnqueue) }
                OverflowActionItem(
                    label = appString(if (favorite) Res.string.player_unlike else Res.string.player_like),
                    icon = if (favorite) SenadaIcons.HeartFilled else SenadaIcons.Heart,
                ) { select { actions.toggleFavorite(track) } }
                if (artistChoices.isNotEmpty()) {
                    OverflowActionItem(appString(Res.string.action_go_to_artist), SenadaIcons.Person) {
                        select {
                            if (artistChoices.size == 1) {
                                actions.openArtist(track, artistChoices.first())
                            } else {
                                pickingArtist = true
                            }
                        }
                    }
                }
                library.playlists.forEach { playlist ->
                    OverflowActionItem(appString(Res.string.playlist_add_to_named, playlist.name), SenadaIcons.Library) {
                        select { actions.addToPlaylist(playlist.id, track) }
                    }
                }
                OverflowActionItem(appString(Res.string.library_playlist_new_ellipsis), SenadaIcons.Add) {
                    select { naming = true }
                }
                extra(select)
            }
        }
    }
    if (naming) {
        NameDialog(
            title = appString(Res.string.library_new_playlist),
            initial = "",
            confirmLabel = appString(Res.string.action_create),
            onDismiss = { naming = false },
        ) { name ->
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
                label = { Text(appString(Res.string.playlist_name_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
            TextButton(onClick = onDismiss, modifier = Modifier.minTouchTarget()) { Text(appString(Res.string.action_cancel)) }
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
        eyebrow = appString(Res.string.library_empty_eyebrow),
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
        val language = LocalAppLanguage.current
        val meta = favoritesCollectionMeta(
            trackCount = library.favoriteTotal,
            totalDurationMs = totalDurationMs,
            language = language,
        )
        val menuItems = remember(library.favoriteTotal, language) {
            collectionMenuItems(CollectionKind.Favorites, hasTracks = library.favoriteTotal > 0, language = language)
        }
        CollectionHeader(
            title = appString(Res.string.library_shortcut_favorites),
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
            eyebrow = appString(Res.string.library_favorites_eyebrow),
        )
    }
    if (library.favoriteTotal == 0) {
        item(key = "favorites-empty") {
            LibraryEmptyState(
                message = appString(Res.string.library_favorites_empty_short),
                actionLabel = appString(Res.string.offline_find_songs),
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
        Text(appString(Res.string.action_loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun LazyListScope.historyTab(
    library: LibraryState,
    actions: LibraryActions,
    onOpenSearch: () -> Unit = {},
) {
    item(key = "history-header") {
        val language = LocalAppLanguage.current
        val meta = remember(library.history, language) {
            collectionMeta(
                trackCount = library.history.size,
                totalDurationMs = sumKnownDurationMs(library.history),
                language = language,
            )
        }
        val historyTitle = appString(Res.string.library_shortcut_history)
        CompactCollectionHeader(
            title = historyTitle,
            meta = meta,
            eyebrow = appString(Res.string.library_history_eyebrow),
        ) {
            CollectionPlayButtons(
                title = historyTitle,
                canPlay = library.history.isNotEmpty(),
                onPlay = { actions.playHistory(library.history) },
                onShuffle = { actions.shuffleHistory(library.history) },
            )
        }
    }
    if (library.history.isEmpty()) {
        item(key = "history-empty") {
            LibraryEmptyState(
                message = appString(Res.string.library_history_empty),
                actionLabel = appString(Res.string.offline_find_songs),
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
                    message = appString(Res.string.library_playlists_empty_short),
                    actionLabel = appString(Res.string.library_new_playlist),
                    onAction = { naming = true },
                )
                if (naming) {
                    NewPlaylistDialog(actions = actions, onDismiss = { naming = false })
                }
            }
        }
        libraryPlaylistRows(library, actions) { id -> onOpen(id) }
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
                message = appString(Res.string.playlist_empty),
                actionLabel = appString(Res.string.offline_find_songs),
                onAction = onOpenSearch,
            )
        }
    }
    itemsIndexed(opened.entries, key = { _, entry -> "playlist-item-${entry.itemId}" }) { index, entry ->
        TrackRow(
            track = entry.track,
            favorite = entry.track.id in library.favoriteIds,
            onClick = {
                actions.playAll(
                    opened.entries.map { it.track },
                    PlaySource.Playlist.fromLibrary(opened.id, opened.name),
                    startIndex = index,
                )
            },
        ) {
            if (reordering) {
                val moveUpDesc = appString(Res.string.a11y_move_track_up, entry.track.title)
                val moveDownDesc = appString(Res.string.a11y_move_track_down, entry.track.title)
                IconButton(
                    onClick = { actions.moveInPlaylist(opened.id, index, index - 1) },
                    enabled = index > 0,
                    modifier = Modifier.minTouchTarget().semantics {
                        contentDescription = moveUpDesc
                    },
                ) {
                    Icon(imageVector = SenadaIcons.ChevronUp, contentDescription = null)
                }
                IconButton(
                    onClick = { actions.moveInPlaylist(opened.id, index, index + 1) },
                    enabled = index < opened.entryCount - 1,
                    modifier = Modifier.minTouchTarget().semantics {
                        contentDescription = moveDownDesc
                    },
                ) {
                    Icon(imageVector = SenadaIcons.ChevronDown, contentDescription = null)
                }
            } else {
                TrackMenu(entry.track, library, actions) { select ->
                    if (index > 0) {
                        OverflowActionItem(appString(Res.string.queue_move_up), SenadaIcons.ChevronUp) {
                            select { actions.moveInPlaylist(opened.id, index, index - 1) }
                        }
                    }
                    // Urutan memakai indeks di seluruh playlist; halaman yang dimuat selalu awal playlist.
                    if (index < opened.entryCount - 1) {
                        OverflowActionItem(appString(Res.string.queue_move_down), SenadaIcons.ChevronDown) {
                            select { actions.moveInPlaylist(opened.id, index, index + 1) }
                        }
                    }
                    OverflowActionItem(appString(Res.string.action_remove_from_playlist), SenadaIcons.Close) {
                        select { actions.removeFromPlaylist(opened.id, entry.itemId) }
                    }
                }
            }
        }
    }
    loadMoreItem("playlist-more-${opened.id}", loaded = opened.entries.size, total = opened.entryCount, onLoadMore)
}

internal fun LazyListScope.libraryPlaylistRows(library: LibraryState, actions: LibraryActions, onOpen: (String) -> Unit) {
    items(library.playlists, key = { "playlist-${it.id}" }) { playlist ->
        PlaylistRow(
            playlist = playlist,
            cover = selectPlaylistCover(library.playlistCovers[playlist.id].orEmpty()),
            actions = actions,
            onOpen = { onOpen(playlist.id) },
        )
    }
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
    val language = LocalAppLanguage.current
    val rowSubtitle = playlistRowSubtitle(playlist.trackCount, language)
    val openLabel = appString(Res.string.action_open)
    val openPlaylistDesc = appString(Res.string.a11y_open_playlist_named, playlist.name)
    val openPlaylistMenuDesc = appString(Res.string.a11y_open_playlist_menu, playlist.name)
    Surface(color = Color.Transparent) {
        AdaptiveRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(start = 4.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            minPrimaryWidth = 180.dp,
            horizontalSpacing = 12.dp,
            primary = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minTouchTarget()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
                        .semantics { contentDescription = openPlaylistDesc }
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
                            text = rowSubtitle,
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
                            contentDescription = openPlaylistMenuDesc
                        },
                    ) {
                        Icon(
                            imageVector = SenadaIcons.More,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (menuOpen) {
                        OverflowActionSheet(
                            title = playlist.name,
                            subtitle = rowSubtitle,
                            artwork = { PlaylistRowCover(cover, size = 48.dp) },
                            onDismiss = { menuOpen = false },
                        ) { select ->
                            OverflowActionItem(appString(Res.string.action_rename), SenadaIcons.Edit) { select { renaming = true } }
                            OverflowActionItem(
                                appString(Res.string.playlist_delete_title).removeSuffix("?"),
                                SenadaIcons.Trash,
                            ) { select { deleting = true } }
                        }
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
private fun PlaylistRowCover(cover: PlaylistCover, size: Dp = 56.dp) {
    val coverShape = RoundedCornerShape(LocalSenadaLook.current.listCoverRadius)
    Box(
        modifier = Modifier
            .size(size)
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
    val language = LocalAppLanguage.current
    WithPlaylistCreation(actions, onOpen) { onNew, onImport ->
        CompactCollectionHeader(
            title = appString(Res.string.catalog_type_playlist),
            meta = playlistListMeta(playlistCount, language),
            eyebrow = appString(Res.string.library_playlists_eyebrow),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactActionChip(appString(Res.string.library_new_playlist), SenadaIcons.Add, onNew, emphasized = true)
                CompactActionChip(appString(Res.string.playlist_import_title), SenadaIcons.Import, onImport)
            }
        }
    }
}

@Composable
internal fun LibraryAddPlaylistButton(actions: LibraryActions, onOpen: (String) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val addToLibraryDesc = appString(Res.string.library_add_to_library)
    WithPlaylistCreation(actions, onOpen) { onNew, onImport ->
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.minTouchTarget(),
            ) { Icon(SenadaIcons.Add, contentDescription = addToLibraryDesc) }
            if (menuOpen) {
                OverflowActionSheet(title = addToLibraryDesc, onDismiss = { menuOpen = false }) { select ->
                    OverflowActionItem(appString(Res.string.library_new_playlist), SenadaIcons.Add) { select(onNew) }
                    OverflowActionItem(appString(Res.string.playlist_import_title), SenadaIcons.Import) { select(onImport) }
                }
            }
        }
    }
}

@Composable
private fun WithPlaylistCreation(
    actions: LibraryActions,
    onOpen: (String) -> Unit,
    content: @Composable (onNew: () -> Unit, onImport: () -> Unit) -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    content({ naming = true }, { importing = true })
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
    NameDialog(
        title = appString(Res.string.library_new_playlist),
        initial = "",
        confirmLabel = appString(Res.string.action_create),
        onDismiss = onDismiss,
    ) { name ->
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
        title = appString(Res.string.action_rename),
        initial = currentName,
        confirmLabel = appString(Res.string.action_save),
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
    val deleteTitle = appString(Res.string.playlist_delete_title)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(deleteTitle, modifier = Modifier.semantics { heading() }) },
        text = { Text(appString(Res.string.playlist_delete_confirm, playlistName)) },
        confirmButton = {
            TextButton(
                onClick = onDelete,
                modifier = Modifier.minTouchTarget(),
            ) { Text(deleteTitle.removeSuffix("?")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minTouchTarget()) { Text(appString(Res.string.action_cancel)) }
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
    val language = LocalAppLanguage.current
    LaunchedEffect(playlist.id, playlist.entryCount <= 1) {
        if (reordering && playlist.entryCount <= 1) onToggleReorder()
    }
    val menuItems = remember(playlist.entryCount, language) {
        collectionMenuItems(CollectionKind.LibraryPlaylist, hasTracks = playlist.entryCount > 0, language = language)
    }
    val coverArtwork = playlist.entries.firstOrNull()?.track?.artworkUrl?.takeIf { it.isNotBlank() }
    val playlistKey = PlaySource.Playlist.fromLibrary(playlist.id, playlist.name).key
    val isPlaying by remember(actions, playlistKey) { actions.playlistPlaying(playlistKey) }.collectAsState(false)
    CollectionHeader(
        title = playlist.name,
        meta = playlistCollectionMeta(
            trackCount = playlist.entryCount,
            totalDurationMs = playlist.totalDurationMs,
            language = language,
        ),
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
        onPlay = { actions.togglePlaylistPlayback(playlistKey) { actions.playAllOf(playlist.id) } },
        onShuffle = { actions.shufflePlaylist(playlist.id) },
        backLabel = appString(Res.string.playlist_back_all),
        onBack = onClose,
        chips = {
            SenadaFilterPill(
                label = appString(Res.string.playlist_reorder_button),
                selected = reordering,
                enabled = playlist.entryCount > 1,
                role = Role.Button,
                stateLabel = appString(
                    if (reordering) Res.string.playlist_reorder_active else Res.string.playlist_reorder_inactive,
                ),
                onClick = onToggleReorder,
            )
        },
        eyebrow = appString(Res.string.catalog_type_playlist),
        isPlaying = isPlaying,
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
