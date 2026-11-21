package senada.app.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import senada.app.AppTab
import senada.app.AppearancePreferences
import senada.app.LIBRARY_PAGE_SIZE
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.nowplaying.NowPlayingScreen
import senada.app.tabs.homeTab
import senada.app.tabs.libraryTab
import senada.app.tabs.rememberSearchTabState
import senada.app.tabs.searchTab
import senada.app.tabs.settingsTab
import senada.library.LibraryRepository
import senada.library.Playlist
import senada.playback.PlaybackController

/** Jarak bawah isi tab supaya baris terakhir tidak tertutup navigasi dan mini player yang mengambang. */
private val WithMiniPlayer = 168.dp
private val WithoutMiniPlayer = 96.dp

/**
 * Kerangka aplikasi: isi tab yang aktif, mini player dan navigasi yang mengambang di bawah, serta
 * Sedang diputar sebagai layar penuh di atasnya.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
internal fun AppShell(
    playback: PlaybackController,
    library: LibraryRepository,
    appearance: AppearancePreferences,
    screenVisible: Boolean,
) {
    val navigator = rememberAppNavigator()
    val state by playback.state.collectAsState()
    val uiScope = rememberCoroutineScope()
    val actions = remember(library, playback, uiScope) { LibraryActions(library, playback, uiScope) }

    // Daftar panjang dimuat per halaman; menaikkan batas menambah halaman tanpa mengosongkan daftar.
    val favoritesLimit = remember { MutableStateFlow(LIBRARY_PAGE_SIZE) }
    val playlistLimit = remember { MutableStateFlow(LIBRARY_PAGE_SIZE) }
    val favorites by remember(library) { favoritesLimit.flatMapLatest(library::favoritesPage) }.collectAsState(emptyList())
    val favoriteTotal by remember(library) { library.favoriteTotal }.collectAsState(0)
    val favoriteIds by remember(library) { library.favoriteIds }.collectAsState(emptySet())
    val playlists by library.playlists.collectAsState(emptyList())
    val history by remember(library) { library.history() }.collectAsState(emptyList())
    val libraryState = remember(favorites, favoriteTotal, favoriteIds, playlists, history) {
        LibraryState(favorites, favoriteTotal, favoriteIds, playlists, history)
    }
    val openedPlaylistId = navigator.openedPlaylistId
    val openedPlaylist by remember(openedPlaylistId) {
        playlistLimit.value = LIBRARY_PAGE_SIZE
        val id = openedPlaylistId ?: return@remember flowOf<Playlist?>(null)
        playlistLimit.flatMapLatest { limit -> library.playlist(id, limit) }
    }.collectAsState(null)
    var librarySection by rememberSaveable { mutableStateOf(AppTab.Favorites) }

    val search = rememberSearchTabState()
    val searchHistory by remember(library) { library.searchHistory() }.collectAsState(emptyList())
    // Dicatat saat pengguna menekan cari atau memakai hasilnya, bukan setiap ketikan yang sempat dicari.
    val recordSearch: () -> Unit = {
        val current = search.query
        if (current.isNotBlank()) uiScope.launch { library.recordSearch(current) }
    }

    PlatformBackHandler(enabled = navigator.canGoBack) { navigator.back() }

    val miniPlayerVisible = showsMiniPlayer(state)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = if (miniPlayerVisible) WithMiniPlayer else WithoutMiniPlayer,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "title-${navigator.destination}") {
                    Text(
                        navigator.destination.label,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                when (navigator.destination) {
                    AppDestination.Home -> homeTab(libraryState, actions) { favoritesLimit.value += LIBRARY_PAGE_SIZE }
                    AppDestination.Search -> searchTab(
                        tab = search,
                        searchHistory = searchHistory,
                        library = libraryState,
                        actions = actions,
                        onImported = playback::add,
                        onRecordSearch = recordSearch,
                        onRemoveSearch = { uiScope.launch { library.removeSearch(it) } },
                        onClearSearches = { uiScope.launch { library.clearSearchHistory() } },
                    )
                    AppDestination.Library -> libraryTab(
                        section = librarySection,
                        onSection = { librarySection = it },
                        library = libraryState,
                        openedPlaylist = openedPlaylist,
                        actions = actions,
                        onOpenPlaylist = { id -> if (id == null) navigator.back() else navigator.openPlaylist(id) },
                        onLoadMoreFavorites = { favoritesLimit.value += LIBRARY_PAGE_SIZE },
                        onLoadMorePlaylist = { playlistLimit.value += LIBRARY_PAGE_SIZE },
                    )
                    AppDestination.Settings -> settingsTab(appearance, library, playback)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (miniPlayerVisible) {
                    MiniPlayer(
                        state = state,
                        onOpen = navigator::openNowPlaying,
                        onPlayPause = { if (state.audio.isActive) playback.pause() else playback.resume() },
                        onNext = playback::next,
                    )
                }
                FloatingNavBar(current = navigator.destination, onSelect = navigator::select)
            }

            if (navigator.nowPlayingOpen) {
                NowPlayingScreen(
                    state = state,
                    playback = playback,
                    library = libraryState,
                    actions = actions,
                    screenVisible = screenVisible,
                    onClose = navigator::closeNowPlaying,
                )
            }
        }
    }
}
