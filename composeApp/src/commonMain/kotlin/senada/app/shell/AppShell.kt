package senada.app.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import senada.app.AppearancePreferences
import senada.app.LIBRARY_PAGE_SIZE
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.nowplaying.NowPlayingScreen
import senada.app.nowplaying.PlaySourceState
import senada.app.tabs.CatalogDetailStore
import senada.app.tabs.CatalogPlaylistSaveOutcome
import senada.app.tabs.LibraryFilter
import senada.app.tabs.PlaylistSaveStatus
import senada.app.tabs.catalogDetailScreen
import senada.app.tabs.homeTab
import senada.app.tabs.libraryTab
import senada.app.tabs.listeningStatsScreen
import senada.app.tabs.mostPlayedScreen
import senada.app.tabs.rememberCatalogPageState
import senada.app.tabs.rememberSearchTabState
import senada.app.tabs.searchTab
import senada.app.tabs.settingsTab
import senada.library.HomeHero
import senada.library.LibraryRepository
import senada.library.ListenPeriod
import senada.library.Playlist
import senada.library.homeHero
import senada.library.listeningStats
import senada.library.mostPlayed
import senada.platform.importedAudioStorage
import senada.playback.PlaybackController

/** Ruang kosong di atas navigasi dan mini player yang mengambang setelah baris terakhir isi tab. */
private val BelowContentGap = 16.dp

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
    LaunchedEffect(state.queue.entries.isEmpty()) {
        if (playback.state.value.queue.entries.isEmpty()) PlaySourceState.current.value = null
    }
    val uiScope = rememberCoroutineScope()
    val actions = remember(library, playback, uiScope) { LibraryActions(library, playback, uiScope) }
    val platformContext = LocalPlatformContext.current
    val importedStorage = remember(platformContext) { importedAudioStorage(platformContext) }
    LaunchedEffect(actions, importedStorage) {
        actions.syncImportedFiles(importedStorage)
    }

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
    val hero = if (navigator.destination == AppDestination.Home) {
        remember(library, screenVisible) { library.homeHero() }.collectAsState(HomeHero.Welcome).value
    } else {
        HomeHero.Welcome
    }
    val mostPlayed = if (navigator.mostPlayedOpen) {
        remember(library, screenVisible) { library.mostPlayed() }.collectAsState(emptyList()).value
    } else {
        emptyList()
    }
    var listenPeriod by rememberSaveable { mutableStateOf(ListenPeriod.Month) }
    val listeningSummary = if (navigator.listeningStatsOpen) {
        key(listenPeriod) {
            remember(library, screenVisible) { library.listeningStats(listenPeriod) }.collectAsState(null).value
        }
    } else null
    val openedPlaylistId = navigator.openedPlaylistId
    val openedPlaylist by remember(openedPlaylistId) {
        playlistLimit.value = LIBRARY_PAGE_SIZE
        val id = openedPlaylistId ?: return@remember flowOf<Playlist?>(null)
        playlistLimit.flatMapLatest { limit -> library.playlist(id, limit) }
    }.collectAsState(null)
    var librarySection by rememberSaveable { mutableStateOf(LibraryFilter.Favorites) }

    val search = rememberSearchTabState()
    val catalogStore = remember { CatalogDetailStore() }
    val currentCatalogRoute = navigator.currentCatalogRoute
    val catalogPage = rememberCatalogPageState(currentCatalogRoute, catalogStore)
    val searchHistory by remember(library) { library.searchHistory() }.collectAsState(emptyList())
    // Dicatat saat pengguna menekan cari atau memakai hasilnya, bukan setiap ketikan yang sempat dicari.
    val recordSearch: () -> Unit = {
        val current = search.query
        if (current.isNotBlank()) uiScope.launch { library.recordSearch(current) }
    }

    PlatformBackHandler(enabled = navigator.canGoBack) { navigator.back() }

    val miniPlayerVisible = showsMiniPlayer(state)
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val imeVisible = imeInsets.getBottom(density) > 0
    // Tinggi bilah bawah diukur, bukan ditebak: dengan font besar mini player dan navigasi ikut membesar,
    // dan baris terakhir tetap harus bisa digulir ke atasnya.
    var overlayHeight by remember { mutableStateOf(0.dp) }
    val bottomPadding = if (imeVisible) BelowContentGap else overlayHeight + BelowContentGap
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = key(navigator.destination, navigator.mostPlayedOpen, currentCatalogRoute, navigator.listeningStatsOpen) {
                    rememberLazyListState()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .windowInsetsPadding(imeInsets),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (navigator.mostPlayedOpen) {
                    mostPlayedScreen(mostPlayed, favoriteIds, actions, onClose = { navigator.back() })
                    return@LazyColumn
                }
                if (currentCatalogRoute != null) {
                    catalogDetailScreen(
                        route = currentCatalogRoute,
                        state = catalogPage,
                        store = catalogStore,
                        previousLabel = navigator.previousCatalogLabel(),
                        library = libraryState,
                        actions = actions,
                        onBack = { navigator.back() },
                        onOpenAlbum = { album ->
                            navigator.openAlbum(album.id, album.title, album.subtitle, album.artworkUrl)
                        },
                        onSavePlaylist = { routeId, title, tracks ->
                            if (catalogPage.playlistSaves[routeId] !is PlaylistSaveStatus.Saving) {
                                catalogPage.playlistSaves =
                                    catalogPage.playlistSaves + (routeId to PlaylistSaveStatus.Saving)
                                uiScope.launch {
                                    val nextStatus = try {
                                        when (val result = actions.savePlaylistToLibrary(routeId, title, tracks)) {
                                            is CatalogPlaylistSaveOutcome.Saved -> PlaylistSaveStatus.Saved(result.playlist)
                                            is CatalogPlaylistSaveOutcome.Rejected -> PlaylistSaveStatus.Failed(result.message)
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        PlaylistSaveStatus.Failed("Playlist gagal disimpan ke Library. Coba lagi.")
                                    }
                                    catalogPage.playlistSaves =
                                        catalogPage.playlistSaves + (routeId to nextStatus)
                                }
                            }
                        },
                        onOpenSavedPlaylist = { id ->
                            librarySection = LibraryFilter.Playlists
                            navigator.select(AppDestination.Library)
                            navigator.openPlaylist(id)
                        },
                    )
                    return@LazyColumn
                }
                if (navigator.listeningStatsOpen) {
                    listeningStatsScreen(listeningSummary, listenPeriod, favoriteIds, actions, { listenPeriod = it }) {
                        navigator.back()
                    }
                    return@LazyColumn
                }
                if (navigator.destination != AppDestination.Home) {
                    item(key = "title-${navigator.destination}") {
                        Text(
                            navigator.destination.label,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                }
                when (navigator.destination) {
                    AppDestination.Home -> homeTab(
                        library = libraryState,
                        actions = actions,
                        hero = hero,
                        screenVisible = screenVisible,
                        onSearch = { navigator.select(AppDestination.Search) },
                        onOpenFavorites = {
                            librarySection = LibraryFilter.Favorites
                            navigator.select(AppDestination.Library)
                        },
                        onOpenPlaylist = { id ->
                            librarySection = LibraryFilter.Playlists
                            navigator.select(AppDestination.Library)
                            navigator.openPlaylist(id)
                        },
                        onOpenMostPlayed = navigator::openMostPlayed,
                        onOpenListeningStats = navigator::openListeningStats,
                    )
                    AppDestination.Search -> searchTab(
                        tab = search,
                        searchHistory = searchHistory,
                        library = libraryState,
                        actions = actions,
                        onImported = { track ->
                            try {
                                library.recordImported(track)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                // Kegagalan mencatat ke library tidak menghalangi pemutaran di antrean.
                            }
                            playback.add(track)
                        },
                        onRecordSearch = recordSearch,
                        onRemoveSearch = { uiScope.launch { library.removeSearch(it) } },
                        onClearSearches = { uiScope.launch { library.clearSearchHistory() } },
                        onOpenArtist = { artist ->
                            navigator.openArtist(artist.id, artist.name, artist.artworkUrl)
                        },
                        onOpenAlbum = { album ->
                            navigator.openAlbum(album.id, album.title, album.subtitle, album.artworkUrl)
                        },
                        onOpenPlaylist = { playlist ->
                            navigator.openCatalogPlaylist(
                                playlist.id,
                                playlist.title,
                                playlist.subtitle,
                                playlist.artworkUrl,
                            )
                        },
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

            if (!imeVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } }
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
