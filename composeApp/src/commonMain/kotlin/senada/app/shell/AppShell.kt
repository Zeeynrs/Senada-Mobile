package senada.app.shell

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import senada.app.AppearanceSection
import senada.app.BackupSection
import senada.app.DataSection
import senada.app.DeveloperSection
import senada.app.StorageSection
import senada.app.versionName
import senada.app.AppearancePreferences
import senada.app.ArtistOpenBanner
import senada.app.ArtistOpenController
import senada.app.LIBRARY_PAGE_SIZE
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.SenadaSources
import senada.app.UserProfileController
import senada.app.audiolab.audioLabScreen
import senada.app.audiolab.equalizerScreen
import senada.app.audiolab.soundFxScreen
import senada.app.audiolab.soundFxSpeedItemIndex
import senada.app.dataSaverActive
import senada.app.meteredNetwork
import senada.app.profilePhotoModel
import senada.app.userProfileStore
import senada.app.nowplaying.NowPlayingScreen
import senada.app.nowplaying.PlaySourceState
import senada.app.tabs.CatalogDetailStore
import senada.app.tabs.CatalogPlaylistSaveOutcome
import senada.app.tabs.DiscoverController
import senada.app.tabs.HomeShelvesController
import senada.app.tabs.HomeTopScrim
import senada.app.tabs.LibraryFilter
import senada.app.tabs.PlaylistSaveStatus
import senada.app.tabs.catalogDetailScreen
import senada.app.tabs.homeTab
import senada.app.tabs.homeTopScrimProgress
import senada.app.tabs.libraryTab
import senada.app.tabs.listeningStatsScreen
import senada.app.tabs.mostPlayedScreen
import senada.app.tabs.rememberCatalogPageState
import senada.app.tabs.rememberSearchTabState
import senada.app.tabs.searchTab
import senada.app.tabs.ProfileTab
import senada.app.tabs.profileHeader
import senada.app.tabs.shouldRefreshDiscover
import senada.app.tabs.shouldRefreshHomeShelves
import senada.app.theme.LocalSenadaLook
import senada.audio.AudioEffects
import senada.audio.AudioEffectsController
import senada.catalog.youtube.DiscoverSeeds
import senada.catalog.ArtistResolveResult
import senada.catalog.canOpenArtist
import senada.library.HomeHero
import senada.library.LibraryRepository
import senada.library.ListenPeriod
import senada.library.Playlist
import senada.library.discoverSeeds
import senada.library.homeHero
import senada.library.listeningStats
import senada.library.mostPlayed
import senada.library.oldestListeningStartedAt
import senada.library.recentHomeItems
import senada.platform.importedAudioStorage
import senada.playback.PlaybackController
import senada.playback.PlaybackState
import senada.playback.SleepTimerState

/** Ruang kosong di atas navigasi dan mini player yang mengambang setelah baris terakhir isi tab. */
private val BelowContentGap = 16.dp

private fun PlaybackState.withoutPosition(): PlaybackState = copy(audio = audio.copy(positionMs = 0L))

internal data class BottomOverlayVisibility(
    val showNavBar: Boolean,
    val showMiniPlayer: Boolean,
) {
    val visible: Boolean get() = showNavBar || showMiniPlayer
}

internal fun bottomOverlayVisibility(
    imeVisible: Boolean,
    profileDetail: Boolean,
    miniPlayerVisible: Boolean,
): BottomOverlayVisibility = if (imeVisible) {
    BottomOverlayVisibility(showNavBar = false, showMiniPlayer = false)
} else {
    BottomOverlayVisibility(showNavBar = !profileDetail, showMiniPlayer = miniPlayerVisible)
}

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
    audioEffects: AudioEffectsController?,
) {
    val navigator = rememberAppNavigator()
    // Perubahan posisi berkala hanya dibaca oleh bilah posisi dan lirik tersinkron, bukan seluruh shell.
    val state by remember(playback) {
        playback.state.map { it.withoutPosition() }.distinctUntilChanged()
    }.collectAsState(playback.state.value.withoutPosition())
    LaunchedEffect(state.queue.entries.isEmpty()) {
        if (playback.state.value.queue.entries.isEmpty()) PlaySourceState.current.value = null
    }
    val uiScope = rememberCoroutineScope()
    val artistOpener = remember(navigator, uiScope) {
        ArtistOpenController(SenadaSources.artistResolver, navigator, uiScope)
    }
    val actions = remember(library, playback, uiScope, artistOpener) {
        LibraryActions(library, playback, uiScope, onOpenArtist = artistOpener::open)
    }
    val platformContext = LocalPlatformContext.current
    val profileController = remember(platformContext) { UserProfileController(userProfileStore(platformContext)) }
    val profile by profileController.state.collectAsState()
    val profilePhoto = remember(platformContext, profile.photoPath) {
        profile.photoPath?.let { profilePhotoModel(platformContext, it) }
    }
    val importedStorage = remember(platformContext) { importedAudioStorage(platformContext) }
    LaunchedEffect(actions, importedStorage) {
        actions.syncImportedFiles(importedStorage)
    }

    // Daftar panjang dimuat per halaman; menaikkan batas menambah halaman tanpa mengosongkan daftar.
    val favoritesLimit = remember { MutableStateFlow(LIBRARY_PAGE_SIZE) }
    val playlistLimit = remember { MutableStateFlow(LIBRARY_PAGE_SIZE) }
    val favorites by remember(library) { favoritesLimit.flatMapLatest(library::favoritesPage) }.collectAsState(emptyList())
    val favoriteTotal by remember(library) { library.favoriteTotal }.collectAsState(0)
    val favoriteDurationMs by remember(library) { library.favoriteDurationMs }.collectAsState(null)
    val favoriteIds by remember(library) { library.favoriteIds }.collectAsState(emptySet())
    val playlists by library.playlists.collectAsState(emptyList())
    val playlistCovers by remember(library) { library.playlistCovers }.collectAsState(emptyMap())
    val history by remember(library) { library.history() }.collectAsState(emptyList())
    val libraryState = remember(favorites, favoriteTotal, favoriteDurationMs, favoriteIds, playlists, playlistCovers, history) {
        LibraryState(favorites, favoriteTotal, favoriteIds, playlists, history, favoriteDurationMs, playlistCovers)
    }
    val hero = if (navigator.destination == AppDestination.Home) {
        remember(library, screenVisible) { library.homeHero() }.collectAsState(HomeHero.Welcome).value
    } else {
        HomeHero.Welcome
    }
    val recentHome = if (navigator.destination == AppDestination.Home) {
        remember(library) { library.recentHomeItems() }.collectAsState(emptyList()).value
    } else {
        emptyList()
    }
    val profileRoute = navigator.currentProfileRoute
    val mostPlayedOpen = profileRoute == ProfileRoute.MostPlayed
    val listeningStatsOpen = profileRoute == ProfileRoute.Stats
    var versionTaps by rememberSaveable { mutableStateOf(0) }
    val mostPlayed = if (mostPlayedOpen) {
        remember(library, screenVisible) { library.mostPlayed() }.collectAsState(emptyList()).value
    } else {
        emptyList()
    }
    var listenPeriod by rememberSaveable { mutableStateOf(ListenPeriod.Month) }
    val listeningSummary = if (listeningStatsOpen) {
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
    var reorderingPlaylist by remember(openedPlaylistId) { mutableStateOf(false) }
    var librarySection by rememberSaveable { mutableStateOf(LibraryFilter.Favorites) }

    val search = rememberSearchTabState()
    val catalogStore = remember { CatalogDetailStore() }
    val currentCatalogRoute = navigator.currentCatalogRoute
    val catalogPage = rememberCatalogPageState(currentCatalogRoute, catalogStore)
    val metered by remember(platformContext) { meteredNetwork(platformContext) }.collectAsState()
    val saveData = dataSaverActive(appearance.settings?.saveMobileData == true, metered)
    val profileRoot = navigator.destination == AppDestination.Profile && profileRoute == null && currentCatalogRoute == null
    val profileSummary = if (profileRoot) {
        remember(library, screenVisible) { library.listeningStats(ListenPeriod.Month) }.collectAsState(null).value
    } else null
    val oldestListening = if (profileRoot) {
        remember(library) { library.oldestListeningStartedAt() }.collectAsState(null).value
    } else null
    val topArtist = profileSummary?.topArtists?.firstOrNull()
    val profileArtist = if (topArtist != null && canOpenArtist(topArtist.track)) {
        val resolver = SenadaSources.artistResolver
        val known = resolver.knownArtist(topArtist.track, topArtist.name)
        key(topArtist.name, topArtist.track.id) {
            produceState(known, saveData, screenVisible) {
                value = known ?: if (!saveData && screenVisible) {
                    resolver.resolve(topArtist.track, topArtist.name) as? ArtistResolveResult.Found
                } else null
            }.value
        }
    } else null
    val discoverSeeds by remember(library) { library.discoverSeeds() }.collectAsState(null)
    val discoverController = remember(uiScope) { DiscoverController(SenadaSources.discover, uiScope) }
    val homeShelvesController = remember(uiScope) { HomeShelvesController(SenadaSources.homeCatalog, uiScope) }
    val refreshDiscover: () -> Unit = {
        discoverController.refreshIfNeeded(discoverSeeds ?: DiscoverSeeds(), saveData = saveData, force = true)
    }
    val refreshHomeShelves: () -> Unit = {
        homeShelvesController.refresh(saveData = saveData)
    }
    LaunchedEffect(
        navigator.destination,
        mostPlayedOpen,
        listeningStatsOpen,
        currentCatalogRoute,
        search.query.isBlank(),
        discoverSeeds,
        saveData,
        screenVisible,
    ) {
        val seeds = discoverSeeds
        if (
            seeds != null &&
            shouldRefreshDiscover(
                screenVisible = screenVisible,
                destination = navigator.destination,
                hasCatalogRoute = currentCatalogRoute != null,
                mostPlayedOpen = mostPlayedOpen,
                listeningStatsOpen = listeningStatsOpen,
                searchQueryBlank = search.query.isBlank(),
            )
        ) {
            discoverController.refreshIfNeeded(seeds, saveData = saveData)
        }
    }
    LaunchedEffect(
        navigator.destination,
        mostPlayedOpen,
        listeningStatsOpen,
        currentCatalogRoute,
        saveData,
        screenVisible,
    ) {
        if (
            shouldRefreshHomeShelves(
                screenVisible = screenVisible,
                destination = navigator.destination,
                hasCatalogRoute = currentCatalogRoute != null,
                mostPlayedOpen = mostPlayedOpen,
                listeningStatsOpen = listeningStatsOpen,
            )
        ) {
            homeShelvesController.refreshIfNeeded(saveData = saveData)
        }
    }
    val searchHistory by remember(library) { library.searchHistory() }.collectAsState(emptyList())
    // Dicatat saat pengguna menekan cari atau memakai hasilnya, bukan setiap ketikan yang sempat dicari.
    val recordSearch: () -> Unit = {
        val current = search.query
        if (current.isNotBlank()) uiScope.launch { library.recordSearch(current) }
    }

    PlatformBackHandler(enabled = navigator.canGoBack) { navigator.back() }

    val nowPlayingPanel = rememberNowPlayingPanel(navigator)
    val focusManager = LocalFocusManager.current
    // Hentikan kedipan kursor kolom input di tab belakang saat layar Sedang diputar menutup layar.
    LaunchedEffect(nowPlayingPanel.visible) {
        if (nowPlayingPanel.visible) focusManager.clearFocus()
    }
    val miniPlayerVisible = showsMiniPlayer(state)
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val imeVisible = imeInsets.getBottom(density) > 0
    // Tinggi bilah bawah diukur, bukan ditebak: dengan font besar mini player dan navigasi ikut membesar,
    // dan baris terakhir tetap harus bisa digulir ke atasnya.
    var overlayHeight by remember { mutableStateOf(0.dp) }
    var shellWidthPx by remember { mutableStateOf(0) }
    var shellHeightPx by remember { mutableStateOf(0) }
    val homePage = navigator.destination == AppDestination.Home && currentCatalogRoute == null && profileRoute == null
    val profilePage = navigator.destination == AppDestination.Profile && currentCatalogRoute == null
    val profileDetail = profilePage && profileRoute != null
    val audioLabRoute = profileRoute == ProfileRoute.AudioLab ||
        profileRoute == ProfileRoute.Equalizer ||
        profileRoute == ProfileRoute.SoundFx
    var focusSoundFxSpeed by rememberSaveable { mutableStateOf(false) }
    val effectsState = if (audioLabRoute) audioEffects?.effects?.collectAsState()?.value ?: AudioEffects() else AudioEffects()
    val sleepTimerState = if (profileRoute == ProfileRoute.AudioLab) {
        playback.sleepTimerState.collectAsState().value
    } else {
        SleepTimerState.Off
    }
    val safeTop = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() }
    val safeBottom = with(density) { WindowInsets.safeDrawing.getBottom(this).toDp() }
    val shellWidthDp = with(density) { shellWidthPx.toDp().value }
    val shellHeightDp = with(density) { shellHeightPx.toDp().value }
    val overlay = bottomOverlayVisibility(
        imeVisible = imeVisible,
        profileDetail = profileDetail,
        miniPlayerVisible = miniPlayerVisible,
    )
    val bottomPadding = when {
        imeVisible -> BelowContentGap
        !overlay.visible -> maxOf(40.dp, safeBottom)
        else -> overlayHeight + BelowContentGap
    }
    val listState = key(navigator.destination, currentCatalogRoute, profileRoute) {
        rememberLazyListState()
    }
    LaunchedEffect(profileRoute, focusSoundFxSpeed) {
        if (profileRoute == ProfileRoute.SoundFx && focusSoundFxSpeed) {
            listState.animateScrollToItem(soundFxSpeedItemIndex(audioEffects != null))
            focusSoundFxSpeed = false
        }
    }
    val horizontalPadding = when {
        homePage -> 0.dp
        profileRoute == ProfileRoute.AudioLab -> 12.dp
        profilePage -> 16.dp
        else -> 20.dp
    }
    val verticalSpacing = when {
        homePage -> 0.dp
        profileRoute == ProfileRoute.AudioLab -> 10.dp
        profileRoute == ProfileRoute.Equalizer -> 16.dp
        profilePage -> 14.dp
        else -> 12.dp
    }
    // Isi tab menjadi sumber blur untuk mini player dan navigasi kaca yang mengambang di atasnya.
    val glassState = remember { HazeState() }
    val look = LocalSenadaLook.current
    val pageKey = ShellPageKey(
        destination = navigator.destination,
        profileDepth = navigator.profileStack.size,
        profileRoute = profileRoute,
        catalogDepth = navigator.catalogStack.size,
        catalogId = currentCatalogRoute?.id,
        openedPlaylistId = openedPlaylistId,
    )
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    shellWidthPx = size.width
                    shellHeightPx = size.height
                    nowPlayingPanel.updateHeight(size.height)
                }
                .clipToBounds(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .shellPageMotion(pageKey, look)
                    .then(if (look.glass) Modifier.hazeSource(glassState) else Modifier)
                    .then(
                        if (homePage) Modifier
                        else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                    )
                    .windowInsetsPadding(imeInsets),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = when {
                        homePage -> 0.dp
                        profilePage -> ((if (profileDetail) 52.dp else 56.dp) - safeTop).coerceAtLeast(0.dp)
                        else -> 16.dp
                    },
                    bottom = bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            ) {
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
                        onOpenPlaylist = { playlist ->
                            navigator.openCatalogPlaylist(
                                playlist.id,
                                playlist.title,
                                playlist.subtitle,
                                playlist.artworkUrl,
                            )
                        },
                        onOpenArtist = { id, name ->
                            navigator.openArtist(id, name)
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
                if (profileRoute != null) {
                    val previousLabel = navigator.profileStack.dropLast(1).lastOrNull()?.label ?: AppDestination.Profile.label
                    if (!audioLabRoute) {
                        profileHeader(profileRoute, previousLabel) {
                            navigator.back()
                        }
                    }
                    when (profileRoute) {
                        ProfileRoute.Stats -> listeningStatsScreen(listeningSummary, listenPeriod, favoriteIds, actions) { listenPeriod = it }
                        ProfileRoute.MostPlayed -> mostPlayedScreen(mostPlayed, favoriteIds, actions)
                        ProfileRoute.Appearance -> item { AppearanceSection(appearance) }
                        ProfileRoute.Playback -> item { DataSection(appearance) }
                        ProfileRoute.Backup -> item { BackupSection(library) }
                        ProfileRoute.Storage -> item { StorageSection(playback, library) }
                        ProfileRoute.Developer -> item { DeveloperSection(playback, audioEffects) }
                        ProfileRoute.About -> item { Text("Senada " + versionName()) }
                        ProfileRoute.AudioLab -> audioLabScreen(
                            effects = effectsState,
                            effectsAvailable = audioEffects != null,
                            saveMobileData = appearance.settings?.saveMobileData == true,
                            sleepTimerState = sleepTimerState,
                            canStopAfterTrack = state.audio.canStopAfterTrack,
                            playback = playback,
                            previousLabel = previousLabel,
                            onBack = { navigator.back() },
                            onOpenEqualizer = { navigator.openProfileRoute(ProfileRoute.Equalizer) },
                            onOpenSoundFx = { focusSpeed ->
                                focusSoundFxSpeed = focusSpeed
                                navigator.openProfileRoute(ProfileRoute.SoundFx)
                            },
                            onOpenPlayback = { navigator.openProfileRoute(ProfileRoute.Playback) },
                        )
                        ProfileRoute.Equalizer -> equalizerScreen(
                            effects = effectsState,
                            audioEffects = audioEffects,
                            previousLabel = previousLabel,
                            onBack = { navigator.back() },
                        )
                        ProfileRoute.SoundFx -> soundFxScreen(
                            effects = effectsState,
                            audioEffects = audioEffects,
                            previousLabel = previousLabel,
                            onBack = { navigator.back() },
                        )
                    }
                    return@LazyColumn
                }
                if (!profilePage && navigator.destination != AppDestination.Home && navigator.openedPlaylistId == null) {
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
                        recentItems = recentHome,
                        discover = discoverController.state,
                        homeShelves = homeShelvesController.state,
                        screenVisible = screenVisible,
                        screenWidthDp = shellWidthDp,
                        screenHeightDp = shellHeightDp,
                        profileName = profile.name,
                        profilePhoto = profilePhoto,
                        onOpenProfile = { navigator.select(AppDestination.Profile) },
                        onOpenFavorites = {
                            librarySection = LibraryFilter.Favorites
                            navigator.select(AppDestination.Library)
                        },
                        onOpenHistory = {
                            librarySection = LibraryFilter.History
                            navigator.select(AppDestination.Library)
                        },
                        onSearch = { navigator.select(AppDestination.Search) },
                        onOpenPlaylist = { id ->
                            librarySection = LibraryFilter.Playlists
                            navigator.select(AppDestination.Library)
                            navigator.openPlaylist(id)
                        },
                        onOpenMostPlayed = { navigator.openProfileRoute(ProfileRoute.MostPlayed) },
                        onRefreshDiscover = refreshDiscover,
                        onRefreshHomeShelves = refreshHomeShelves,
                        onOpenArtist = { artist ->
                            navigator.openArtist(artist.id, artist.name, artist.artworkUrl)
                        },
                        onOpenAlbum = { album ->
                            navigator.openAlbum(album.id, album.title, album.subtitle, album.artworkUrl)
                        },
                        onOpenCatalogPlaylist = { playlist ->
                            navigator.openCatalogPlaylist(
                                playlist.id,
                                playlist.title,
                                playlist.subtitle,
                                playlist.artworkUrl,
                            )
                        },
                        onOpenMood = navigator::openMood,
                    )
                    AppDestination.Search -> searchTab(
                        tab = search,
                        searchHistory = searchHistory,
                        discover = discoverController.state,
                        library = libraryState,
                        actions = actions,
                        onRecordSearch = recordSearch,
                        onRemoveSearch = { uiScope.launch { library.removeSearch(it) } },
                        onClearSearches = { uiScope.launch { library.clearSearchHistory() } },
                        onRefreshDiscover = refreshDiscover,
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
                        onOpenSearch = { navigator.select(AppDestination.Search) },
                        reorderingPlaylist = reorderingPlaylist,
                        onToggleReorderPlaylist = { reorderingPlaylist = !reorderingPlaylist },
                    )
                    AppDestination.Profile -> item(key = "profile") {
                        ProfileTab(
                            controller = profileController,
                            photo = profilePhoto,
                            summary = profileSummary,
                            oldestListening = oldestListening,
                            artist = profileArtist,
                            onOpen = navigator::openProfileRoute,
                            onOpenArtist = { navigator.openArtist(it.id, it.name, it.artworkUrl) },
                            developerUnlocked = versionTaps == 7,
                            onVersionTap = { if (versionTaps < 7) versionTaps++ },
                        )
                    }
                }
            }

            if (homePage) {
                HomeTopScrim(
                    progress = {
                        homeTopScrimProgress(
                            firstVisibleItemIndex = listState.firstVisibleItemIndex,
                            firstVisibleItemScrollOffsetDp = listState.firstVisibleItemScrollOffset / density.density,
                        )
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            CompositionLocalProvider(LocalGlassState provides glassState) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } }
                        .graphicsLayer { alpha = 1f - nowPlayingPanel.progress }
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = if (overlay.visible) maxOf(24.dp, safeBottom) else 0.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnimatedVisibility(
                        visible = overlay.showMiniPlayer,
                        enter = shellOverlayEnter(look),
                        exit = shellOverlayExit(look),
                    ) {
                        MiniPlayer(
                            state = state,
                            onOpen = navigator::openNowPlaying,
                            onPlayPause = { if (state.audio.isActive) playback.pause() else playback.resume() },
                            onNext = if (profilePage) null else playback::next,
                            modifier = Modifier.nowPlayingSwipe(nowPlayingPanel, opening = true),
                        )
                    }
                    AnimatedVisibility(
                        visible = overlay.showNavBar,
                        enter = shellOverlayEnter(look),
                        exit = shellOverlayExit(look),
                    ) {
                        FloatingNavBar(
                            current = navigator.destination,
                            onSelect = navigator::select,
                            profileName = profile.name,
                            profilePhoto = profilePhoto,
                        )
                    }
                }
            }

            if (nowPlayingPanel.visible) {
                NowPlayingScreen(
                    state = state,
                    playback = playback,
                    library = libraryState,
                    actions = actions,
                    screenVisible = screenVisible,
                    onClose = navigator::closeNowPlaying,
                    onOpenAudioLab = {
                        navigator.closeNowPlaying()
                        navigator.openProfileRoute(ProfileRoute.AudioLab)
                    },
                    panel = nowPlayingPanel,
                    immersive = navigator.nowPlayingOpen,
                    backdropFromCover = LocalSenadaLook.current.playerBackdropFromCover,
                    modifier = Modifier.graphicsLayer { translationY = (1f - nowPlayingPanel.progress) * size.height },
                )
            }

            artistOpener.message?.let { notice ->
                ArtistOpenBanner(
                    message = notice,
                    onDismiss = artistOpener::dismissMessage,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}
