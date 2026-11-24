package senada.app.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import senada.app.BrowseLoadMoreButton
import senada.app.BrowseMonoHeading
import senada.app.BrowseNoticeCard
import senada.app.BrowseRowSkeletonCard
import senada.app.BrowseSectionHeader
import senada.app.CollectionCover
import senada.app.CollectionHeader
import senada.app.CollectionKind
import senada.app.CollectionMenuAction
import senada.app.CollectionTopBar
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.SenadaCard
import senada.app.SenadaFilterPill
import senada.app.SenadaSources
import senada.app.TrackMenu
import senada.app.TrackRow
import senada.app.albumCollectionMeta
import senada.app.browseRowSubtitleStyle
import senada.app.browseRowTitleStyle
import senada.app.collectionMenuItems
import senada.app.minTouchTarget
import senada.app.nowplaying.PlaySource
import senada.app.shell.CatalogRoute
import senada.app.shell.SenadaIcons
import senada.app.textOnAccent
import senada.app.theme.LocalSenadaLook
import senada.app.theme.MonoLabel
import senada.catalog.AlbumDetail
import senada.catalog.AlbumSummary
import senada.catalog.ArtistDetail
import senada.catalog.ArtistSummary
import senada.catalog.CatalogBrowser
import senada.catalog.CatalogException
import senada.catalog.HomeCatalog
import senada.catalog.HomeShelf
import senada.catalog.MoodChip
import senada.catalog.PlaylistDetail
import senada.catalog.PlaylistImportException
import senada.catalog.PlaylistSummary
import senada.catalog.youtube.resizeGoogleArtwork
import senada.model.Track

/** Pilihan filter jenis hasil di tab Cari. */
internal enum class SearchFilter(val label: String) {
    Songs("Lagu"),
    Artists("Artis"),
    Albums("Album"),
    Playlists("Playlist"),
}

/** Hasil pencarian artis di YouTube Music. */
internal data class ArtistSearchState(
    val query: String = "",
    val items: List<ArtistSummary> = emptyList(),
    val nextPageToken: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Hasil pencarian album di YouTube Music. */
internal data class AlbumSearchState(
    val query: String = "",
    val items: List<AlbumSummary> = emptyList(),
    val nextPageToken: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Hasil pencarian playlist di YouTube Music. */
internal data class PlaylistSearchState(
    val query: String = "",
    val items: List<PlaylistSummary> = emptyList(),
    val nextPageToken: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Memuat halaman pencarian artis; saat gagal, hasil dan token tetap disimpan untuk coba lagi. */
internal suspend fun loadArtistSearchPage(
    current: ArtistSearchState,
    catalog: CatalogBrowser = SenadaSources.youTubeMusic,
): ArtistSearchState = try {
    val page = catalog.searchArtists(current.query, current.nextPageToken)
    current.copy(
        items = (current.items + page.items).distinctBy { it.id },
        nextPageToken = page.nextPageToken,
        loading = false,
        error = null,
    )
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    current.copy(loading = false, error = (e as? CatalogException)?.userMessage ?: "Pencarian artis gagal. Coba lagi.")
}

/** Memuat halaman pencarian album; saat gagal, hasil dan token tetap disimpan untuk coba lagi. */
internal suspend fun loadAlbumSearchPage(
    current: AlbumSearchState,
    catalog: CatalogBrowser = SenadaSources.youTubeMusic,
): AlbumSearchState = try {
    val page = catalog.searchAlbums(current.query, current.nextPageToken)
    current.copy(
        items = (current.items + page.items).distinctBy { it.id },
        nextPageToken = page.nextPageToken,
        loading = false,
        error = null,
    )
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    current.copy(loading = false, error = (e as? CatalogException)?.userMessage ?: "Pencarian album gagal. Coba lagi.")
}

/** Memuat halaman pencarian playlist; saat gagal, hasil dan token tetap disimpan untuk coba lagi. */
internal suspend fun loadPlaylistSearchPage(
    current: PlaylistSearchState,
    catalog: CatalogBrowser = SenadaSources.youTubeMusic,
): PlaylistSearchState = try {
    val page = catalog.searchPlaylists(current.query, current.nextPageToken)
    current.copy(
        items = (current.items + page.items).distinctBy { it.id },
        nextPageToken = page.nextPageToken,
        loading = false,
        error = null,
    )
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    current.copy(loading = false, error = (e as? CatalogException)?.userMessage ?: "Pencarian playlist gagal. Coba lagi.")
}

/** Deret chip filter pil v2 di tab Cari yang dapat digeser mendatar agar aman pada skala font besar. */
@Composable
internal fun SearchFilterChips(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (filter in SearchFilter.entries) {
            SenadaFilterPill(
                label = filter.label,
                selected = filter == selected,
                onClick = { onSelect(filter) },
            )
        }
    }
}

/** Daftar hasil pencarian artis di tab Cari. */
internal fun LazyListScope.artistSearchResults(
    state: ArtistSearchState,
    onOpenArtist: (ArtistSummary) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onResultUsed: () -> Unit,
) {
    if (state.query.isEmpty()) {
        item(key = "artist-search-prompt") {
            BrowseNoticeCard(
                eyebrow = "Cari artis",
                message = "Ketik nama artis untuk mencari di YouTube Music.",
                polite = false,
            )
        }
        return
    }
    item(key = "artist-search-heading") {
        BrowseMonoHeading("Artis di YouTube Music")
    }
    if (state.loading && state.items.isEmpty()) {
        item(key = "artist-search-loading") {
            BrowseRowSkeletonCard(
                statusLabel = "Mencari artis…",
                rowCount = 3,
                circularLeading = true,
            )
        }
    }
    state.error?.let { message ->
        item(key = "artist-search-error") {
            CatalogErrorCard(message, onRetry)
        }
    }
    if (!state.loading && state.error == null && state.items.isEmpty()) {
        item(key = "artist-search-empty") {
            BrowseNoticeCard(
                eyebrow = "Hasil kosong",
                message = "Artis tidak ditemukan.",
            )
        }
    }
    items(state.items, key = { "artist-result-${it.id}" }) { artist ->
        val label = listOfNotNull("Buka artis ${artist.name}", artist.subtitle).joinToString(", ")
        CatalogSummaryRow(
            title = artist.name,
            subtitle = artist.subtitle,
            artworkUrl = artist.artworkUrl,
            artworkShape = CircleShape,
            fallbackIcon = SenadaIcons.Person,
            accessibilityLabel = label,
            onClick = {
                onResultUsed()
                onOpenArtist(artist)
            },
        )
    }
    if (state.nextPageToken != null && state.error == null) {
        item(key = "artist-search-more") {
            BrowseLoadMoreButton(loading = state.loading, onClick = onLoadMore)
        }
    }
}

/** Daftar hasil pencarian album di tab Cari. */
internal fun LazyListScope.albumSearchResults(
    state: AlbumSearchState,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onResultUsed: () -> Unit,
) {
    if (state.query.isEmpty()) {
        item(key = "album-search-prompt") {
            BrowseNoticeCard(
                eyebrow = "Cari album",
                message = "Ketik judul album untuk mencari di YouTube Music.",
                polite = false,
            )
        }
        return
    }
    item(key = "album-search-heading") {
        BrowseMonoHeading("Album di YouTube Music")
    }
    if (state.loading && state.items.isEmpty()) {
        item(key = "album-search-loading") {
            BrowseRowSkeletonCard(statusLabel = "Mencari album…", rowCount = 3)
        }
    }
    state.error?.let { message ->
        item(key = "album-search-error") {
            CatalogErrorCard(message, onRetry)
        }
    }
    if (!state.loading && state.error == null && state.items.isEmpty()) {
        item(key = "album-search-empty") {
            BrowseNoticeCard(
                eyebrow = "Hasil kosong",
                message = "Album tidak ditemukan.",
            )
        }
    }
    items(state.items, key = { "album-result-${it.id}" }) { album ->
        val label = listOfNotNull("Buka album ${album.title}", album.subtitle).joinToString(", ")
        CatalogSummaryRow(
            title = album.title,
            subtitle = album.subtitle,
            artworkUrl = album.artworkUrl,
            artworkShape = RoundedCornerShape(LocalSenadaLook.current.listCoverRadius),
            fallbackIcon = SenadaIcons.Album,
            accessibilityLabel = label,
            onClick = {
                onResultUsed()
                onOpenAlbum(album)
            },
        )
    }
    if (state.nextPageToken != null && state.error == null) {
        item(key = "album-search-more") {
            BrowseLoadMoreButton(loading = state.loading, onClick = onLoadMore)
        }
    }
}

/** Status pemuatan satu halaman detail katalog (artis atau album). */
internal data class CatalogLoadState<T>(
    val routeId: String? = null,
    val data: T? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Menyimpan halaman artis, album, playlist, dan mood yang baru dibuka agar navigasi kembali tampil instan. */
internal class CatalogDetailStore(
    private val catalog: CatalogBrowser = SenadaSources.youTubeMusic,
    private val homeCatalog: HomeCatalog = SenadaSources.homeCatalog,
    private val maxEntries: Int = 20,
) {
    private val artists = mutableMapOf<String, ArtistDetail>()
    private val albums = mutableMapOf<String, AlbumDetail>()
    private val playlists = mutableMapOf<String, PlaylistDetail>()
    private val moods = mutableMapOf<String, List<HomeShelf>>()

    fun cachedArtist(id: String): ArtistDetail? = artists[id]
    fun cachedAlbum(id: String): AlbumDetail? = albums[id]
    fun cachedPlaylist(id: String): PlaylistDetail? = playlists[id]
    fun cachedMood(key: String): List<HomeShelf>? = moods[key]

    suspend fun loadArtist(id: String): CatalogLoadState<ArtistDetail> {
        artists[id]?.let { return CatalogLoadState(routeId = id, data = it) }
        return try {
            val detail = catalog.artist(id)
            putBounded(artists, id, detail)
            CatalogLoadState(routeId = id, data = detail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CatalogLoadState(
                routeId = id,
                error = (e as? CatalogException)?.userMessage ?: "Halaman artis gagal dimuat. Coba lagi.",
            )
        }
    }

    suspend fun loadAlbum(id: String): CatalogLoadState<AlbumDetail> {
        albums[id]?.let { return CatalogLoadState(routeId = id, data = it) }
        return try {
            val detail = catalog.album(id)
            putBounded(albums, id, detail)
            CatalogLoadState(routeId = id, data = detail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CatalogLoadState(
                routeId = id,
                error = (e as? CatalogException)?.userMessage ?: "Halaman album gagal dimuat. Coba lagi.",
            )
        }
    }

    suspend fun loadPlaylist(id: String): CatalogLoadState<PlaylistDetail> {
        playlists[id]?.let { return CatalogLoadState(routeId = id, data = it) }
        return try {
            val detail = catalog.playlist(id)
            putBounded(playlists, id, detail)
            CatalogLoadState(routeId = id, data = detail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CatalogLoadState(
                routeId = id,
                error = (e as? CatalogException)?.userMessage
                    ?: (e as? PlaylistImportException)?.userMessage
                    ?: "Halaman playlist gagal dimuat. Coba lagi.",
            )
        }
    }

    suspend fun loadMood(chip: MoodChip, key: String): CatalogLoadState<List<HomeShelf>> {
        moods[key]?.let { return CatalogLoadState(routeId = key, data = it) }
        return try {
            val shelves = homeCatalog.mood(chip)
            putBounded(moods, key, shelves)
            CatalogLoadState(routeId = key, data = shelves)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CatalogLoadState(
                routeId = key,
                error = (e as? CatalogException)?.userMessage ?: "Halaman mood gagal dimuat. Coba lagi.",
            )
        }
    }

    private fun <T> putBounded(map: MutableMap<String, T>, key: String, value: T) {
        map.remove(key)
        map[key] = value
        while (map.size > maxEntries) {
            val oldest = map.keys.firstOrNull() ?: break
            map.remove(oldest)
        }
    }
}

/** State Compose untuk halaman detail artis, album, playlist, atau mood yang sedang dibuka. */
internal class CatalogPageState {
    var artist by mutableStateOf(CatalogLoadState<ArtistDetail>())
        internal set
    var album by mutableStateOf(CatalogLoadState<AlbumDetail>())
        internal set
    var playlist by mutableStateOf(CatalogLoadState<PlaylistDetail>())
        internal set
    var mood by mutableStateOf(CatalogLoadState<List<HomeShelf>>())
        internal set
    var playlistSaves by mutableStateOf<Map<String, PlaylistSaveStatus>>(emptyMap())
        internal set
    var attempt by mutableStateOf(0)
        internal set

    fun retry() {
        attempt++
    }
}

@Composable
internal fun rememberCatalogPageState(route: CatalogRoute?, store: CatalogDetailStore): CatalogPageState {
    val state = remember { CatalogPageState() }
    LaunchedEffect(route, state.attempt) {
        when (route) {
            null -> {
                state.artist = CatalogLoadState()
                state.album = CatalogLoadState()
                state.playlist = CatalogLoadState()
                state.mood = CatalogLoadState()
                state.playlistSaves = emptyMap()
            }
            is CatalogRoute.Artist -> {
                state.album = CatalogLoadState()
                state.playlist = CatalogLoadState()
                state.mood = CatalogLoadState()
                val cached = store.cachedArtist(route.id)
                if (cached != null) {
                    state.artist = CatalogLoadState(routeId = route.id, data = cached)
                } else {
                    state.artist = CatalogLoadState(routeId = route.id, loading = true)
                    state.artist = store.loadArtist(route.id)
                }
            }
            is CatalogRoute.Album -> {
                state.artist = CatalogLoadState()
                state.playlist = CatalogLoadState()
                state.mood = CatalogLoadState()
                val cached = store.cachedAlbum(route.id)
                if (cached != null) {
                    state.album = CatalogLoadState(routeId = route.id, data = cached)
                } else {
                    state.album = CatalogLoadState(routeId = route.id, loading = true)
                    state.album = store.loadAlbum(route.id)
                }
            }
            is CatalogRoute.Playlist -> {
                state.artist = CatalogLoadState()
                state.album = CatalogLoadState()
                state.mood = CatalogLoadState()
                val cached = store.cachedPlaylist(route.id)
                if (cached != null) {
                    state.playlist = CatalogLoadState(routeId = route.id, data = cached)
                } else {
                    state.playlist = CatalogLoadState(routeId = route.id, loading = true)
                    state.playlist = store.loadPlaylist(route.id)
                }
            }
            is CatalogRoute.Mood -> {
                state.artist = CatalogLoadState()
                state.album = CatalogLoadState()
                state.playlist = CatalogLoadState()
                val key = route.cacheKey()
                val cached = store.cachedMood(key)
                if (cached != null) {
                    state.mood = CatalogLoadState(routeId = key, data = cached)
                } else {
                    state.mood = CatalogLoadState(routeId = key, loading = true)
                    state.mood = store.loadMood(route.toMoodChip(), key)
                }
            }
        }
    }
    return state
}

/** Merender halaman detail artis, album, atau playlist di dalam LazyColumn utama aplikasi. */
internal fun LazyListScope.catalogDetailScreen(
    route: CatalogRoute,
    state: CatalogPageState,
    store: CatalogDetailStore,
    previousLabel: String,
    library: LibraryState,
    actions: LibraryActions,
    onBack: () -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit = {},
    onOpenArtist: (id: String, name: String) -> Unit = { _, _ -> },
    onSavePlaylist: (routeId: String, title: String, tracks: List<Track>) -> Unit = { _, _, _ -> },
    onOpenSavedPlaylist: (String) -> Unit = {},
) {
    when (route) {
        is CatalogRoute.Artist -> {
            val current = if (state.artist.routeId == route.id) {
                state.artist
            } else {
                val cached = store.cachedArtist(route.id)
                CatalogLoadState(routeId = route.id, data = cached, loading = cached == null)
            }
            artistDetailScreen(
                route = route,
                state = current,
                previousLabel = previousLabel,
                library = library,
                actions = actions,
                onBack = onBack,
                onRetry = state::retry,
                onOpenAlbum = onOpenAlbum,
                onOpenPlaylist = onOpenPlaylist,
            )
        }
        is CatalogRoute.Album -> {
            val current = if (state.album.routeId == route.id) {
                state.album
            } else {
                val cached = store.cachedAlbum(route.id)
                CatalogLoadState(routeId = route.id, data = cached, loading = cached == null)
            }
            albumDetailScreen(
                route = route,
                state = current,
                previousLabel = previousLabel,
                library = library,
                actions = actions,
                onBack = onBack,
                onRetry = state::retry,
                onOpenArtist = onOpenArtist,
            )
        }
        is CatalogRoute.Playlist -> {
            val current = if (state.playlist.routeId == route.id) {
                state.playlist
            } else {
                val cached = store.cachedPlaylist(route.id)
                CatalogLoadState(routeId = route.id, data = cached, loading = cached == null)
            }
            playlistDetailScreen(
                route = route,
                state = current,
                saveStatus = state.playlistSaves[route.id],
                onSave = { title, tracks -> onSavePlaylist(route.id, title, tracks) },
                onOpenSavedPlaylist = onOpenSavedPlaylist,
                previousLabel = previousLabel,
                library = library,
                actions = actions,
                onBack = onBack,
                onRetry = state::retry,
            )
        }
        is CatalogRoute.Mood -> {
            val key = route.cacheKey()
            val current = if (state.mood.routeId == key) {
                state.mood
            } else {
                val cached = store.cachedMood(key)
                CatalogLoadState(routeId = key, data = cached, loading = cached == null)
            }
            moodDetailScreen(
                route = route,
                state = current,
                previousLabel = previousLabel,
                library = library,
                actions = actions,
                onBack = onBack,
                onRetry = state::retry,
                onOpenAlbum = onOpenAlbum,
                onOpenPlaylist = onOpenPlaylist,
                onOpenArtist = onOpenArtist,
            )
        }
    }
}

/** Jumlah maksimum lagu pada bagian Lagu terpopuler di halaman artis. */
internal const val MAX_ARTIST_POPULAR_SONGS = 5

/** Bagian yang ditampilkan pada halaman artis yang disederhanakan. */
internal sealed interface ArtistPageSection {
    val title: String

    data class PopularSongs(
        override val title: String = "Lagu terpopuler",
        val songs: List<Track>,
        val fullListPlaylistId: String? = null,
    ) : ArtistPageSection

    data class PopularAlbums(
        override val title: String = "Album terpopuler",
        val albums: List<AlbumSummary>,
    ) : ArtistPageSection

    data class NewestAlbums(
        override val title: String = "Album terbaru",
        val albums: List<AlbumSummary>,
    ) : ArtistPageSection
}

/** Menyusun tepat tiga bagian halaman artis: Lagu terpopuler, Album terpopuler, dan Album terbaru. */
internal fun artistPageSections(detail: ArtistDetail?): List<ArtistPageSection> {
    if (detail == null) return emptyList()
    return buildList {
        val songs = detail.topSongs.take(MAX_ARTIST_POPULAR_SONGS)
        if (songs.isNotEmpty()) {
            add(
                ArtistPageSection.PopularSongs(
                    songs = songs,
                    fullListPlaylistId = detail.topSongsPlaylistId?.takeIf { it.isNotBlank() },
                ),
            )
        }
        if (detail.popularAlbums.isNotEmpty()) {
            add(ArtistPageSection.PopularAlbums(albums = detail.popularAlbums))
        }
        if (detail.newestAlbums.isNotEmpty()) {
            add(ArtistPageSection.NewestAlbums(albums = detail.newestAlbums))
        }
    }
}

private fun LazyListScope.artistDetailScreen(
    route: CatalogRoute.Artist,
    state: CatalogLoadState<ArtistDetail>,
    previousLabel: String,
    library: LibraryState,
    actions: LibraryActions,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
) {
    val detail = state.data
    val displayName = detail?.name?.takeIf { it.isNotBlank() } ?: route.label
    val artworkUrl = detail?.artworkUrl ?: route.artworkUrl
    val sections = artistPageSections(detail)
    val popularSongs = sections
        .filterIsInstance<ArtistPageSection.PopularSongs>()
        .firstOrNull()
        ?.songs
        .orEmpty()

    item(key = "artist-back-${route.id}") {
        CollectionTopBar(previousLabel = previousLabel, onBack = onBack)
    }
    item(key = "artist-header-${route.id}") {
        ArtistHeroCard(
            displayName = displayName,
            artworkUrl = artworkUrl,
            monthlyListeners = detail?.monthlyListeners,
            canPlay = popularSongs.isNotEmpty(),
            onPlay = { actions.playAll(popularSongs, PlaySource.Artist(displayName), resetShuffle = true) },
            onShuffle = { actions.playShuffled(popularSongs, PlaySource.Artist(displayName)) },
        )
    }

    if (state.loading && detail == null) {
        item(key = "artist-loading-${route.id}") {
            BrowseRowSkeletonCard(statusLabel = "Memuat halaman artis…", rowCount = 4)
        }
    }
    state.error?.let { message ->
        item(key = "artist-error-${route.id}") {
            CatalogErrorCard(message, onRetry)
        }
    }

    for (section in sections) {
        when (section) {
            is ArtistPageSection.PopularSongs -> {
                item(key = "artist-songs-heading-${route.id}") {
                    BrowseSectionHeader(
                        title = section.title,
                        monoLabel = "Teratas",
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                item(key = "artist-songs-card-${route.id}") {
                    val colors = MaterialTheme.colorScheme
                    SenadaCard {
                        Column(Modifier.fillMaxWidth().padding(1.dp)) {
                            section.songs.forEachIndexed { index, track ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = colors.outline,
                                    )
                                }
                                Box(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                    TrackRow(
                                        track = track,
                                        favorite = track.id in library.favoriteIds,
                                        trackNumber = index + 1,
                                        showArtworkWithNumber = true,
                                        onClick = {
                                            actions.playAll(
                                                section.songs,
                                                PlaySource.Artist(displayName),
                                                startIndex = index,
                                            )
                                        },
                                    ) {
                                        TrackMenu(track, library, actions)
                                    }
                                }
                            }
                            section.fullListPlaylistId?.let { playlistId ->
                                HorizontalDivider(color = colors.outline)
                                ArtistSeeAllFooterRow(
                                    artistName = displayName,
                                    onClick = {
                                        onOpenPlaylist(
                                            PlaylistSummary(
                                                id = playlistId,
                                                title = "Lagu terpopuler $displayName",
                                                subtitle = displayName,
                                                artworkUrl = artworkUrl,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            is ArtistPageSection.PopularAlbums -> {
                item(key = "artist-popular-albums-heading-${route.id}") {
                    BrowseSectionHeader(
                        title = section.title,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                item(key = "artist-popular-albums-row-${route.id}") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(section.albums, key = { "artist-popular-album-${route.id}-${it.id}" }) { album ->
                            ReleaseCard(album, onClick = { onOpenAlbum(album) })
                        }
                    }
                }
            }
            is ArtistPageSection.NewestAlbums -> {
                item(key = "artist-newest-albums-heading-${route.id}") {
                    BrowseSectionHeader(
                        title = section.title,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                item(key = "artist-newest-albums-row-${route.id}") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(section.albums, key = { "artist-newest-album-${route.id}-${it.id}" }) { album ->
                            ReleaseCard(album, onClick = { onOpenAlbum(album) })
                        }
                    }
                }
            }
        }
    }

    if (!state.loading && state.error == null && detail != null && sections.isEmpty()) {
        item(key = "artist-empty-${route.id}") {
            BrowseNoticeCard(
                eyebrow = "Halaman artis",
                message = "Belum ada lagu atau album untuk artis ini.",
            )
        }
    }
}

@Composable
private fun ArtistHeroCard(
    displayName: String,
    artworkUrl: String?,
    monthlyListeners: String?,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    val playContainer = if (canPlay) colors.primary else colors.surfaceContainerHighest
    val playContent = if (canPlay) textOnAccent(playContainer) else colors.onSurface.copy(alpha = 0.38f)

    val identityText: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MonoLabel("Artis")
            Text(
                text = displayName,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.04).em,
                ),
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            monthlyListeners?.takeIf { it.isNotBlank() }?.let { listeners ->
                Text(
                    text = listeners,
                    style = browseRowSubtitleStyle(),
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }

    SenadaCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(1.dp).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (largeFont) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CatalogArtwork(
                        artworkUrl = artworkUrl,
                        shape = CircleShape,
                        fallbackIcon = SenadaIcons.Person,
                        imageSize = 544,
                        modifier = Modifier.size(84.dp),
                    )
                    identityText()
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CatalogArtwork(
                        artworkUrl = artworkUrl,
                        shape = CircleShape,
                        fallbackIcon = SenadaIcons.Person,
                        imageSize = 544,
                        modifier = Modifier.size(84.dp),
                    )
                    Box(Modifier.weight(1f)) { identityText() }
                }
            }
            HorizontalDivider(color = colors.outline)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPlay,
                    enabled = canPlay,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = playContainer,
                        contentColor = playContent,
                        disabledContainerColor = colors.surfaceContainerHighest,
                        disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = "Putar lagu terpopuler $displayName"
                    },
                ) {
                    Icon(
                        imageVector = SenadaIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Putar",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
                Surface(
                    onClick = onShuffle,
                    enabled = canPlay,
                    shape = RoundedCornerShape(24.dp),
                    color = if (canPlay) colors.surfaceContainerHigh else Color.Transparent,
                    border = BorderStroke(1.dp, if (canPlay) colors.outline else colors.outlineVariant),
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        role = Role.Button
                        contentDescription = "Putar acak lagu terpopuler $displayName"
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val contentTint = if (canPlay) colors.onSurface else colors.onSurface.copy(alpha = 0.38f)
                        Icon(
                            imageVector = SenadaIcons.Shuffle,
                            contentDescription = null,
                            tint = contentTint,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "Acak",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = contentTint,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistSeeAllFooterRow(artistName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "Lihat semua lagu terpopuler $artistName"
                role = Role.Button
            }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Lihat semua",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clearAndSetSemantics {},
        )
        Icon(
            imageVector = SenadaIcons.ProfileChevron,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun LazyListScope.albumDetailScreen(
    route: CatalogRoute.Album,
    state: CatalogLoadState<AlbumDetail>,
    previousLabel: String,
    library: LibraryState,
    actions: LibraryActions,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenArtist: (id: String, name: String) -> Unit,
) {
    val detail = state.data
    val displayTitle = detail?.title?.takeIf { it.isNotBlank() } ?: route.label
    val artworkUrl = detail?.artworkUrl ?: route.artworkUrl
    val tracks = detail?.tracks.orEmpty()
    val artistId = detail?.artistId?.takeIf { it.isNotBlank() }
    val primaryArtist = detail?.artists?.firstOrNull { it.isNotBlank() }.orEmpty()

    item(key = "album-header-${route.id}") {
        val meta = remember(detail, route.subtitle) { albumCollectionMeta(detail, route.subtitle) }
        val menuItems = remember(artistId != null, tracks.isNotEmpty()) {
            collectionMenuItems(
                kind = CollectionKind.Album(hasArtistId = artistId != null),
                hasTracks = tracks.isNotEmpty(),
            )
        }
        CollectionHeader(
            title = displayTitle,
            meta = meta,
            cover = CollectionCover(artworkUrl = artworkUrl, fallbackIcon = SenadaIcons.Album),
            menuItems = menuItems,
            onMenuAction = { action ->
                when (action) {
                    CollectionMenuAction.EnqueueAll -> actions.enqueueAll(tracks)
                    CollectionMenuAction.OpenArtist -> artistId?.let { onOpenArtist(it, primaryArtist) }
                    else -> Unit
                }
            },
            canPlay = tracks.isNotEmpty(),
            onPlay = { actions.playAll(tracks, PlaySource.Album(displayTitle), resetShuffle = true) },
            onShuffle = { actions.playShuffled(tracks, PlaySource.Album(displayTitle)) },
            backLabel = previousLabel,
            onBack = onBack,
            eyebrow = detail?.typeLabel?.takeIf { it.isNotBlank() } ?: "Album",
            chips = if (artistId != null && primaryArtist.isNotBlank()) {
                {
                    AlbumArtistChip(
                        artistName = primaryArtist,
                        onClick = { onOpenArtist(artistId, primaryArtist) },
                    )
                }
            } else {
                null
            },
        )
    }

    if (state.loading && detail == null) {
        item(key = "album-loading-${route.id}") {
            BrowseRowSkeletonCard(statusLabel = "Memuat halaman album…", rowCount = 5)
        }
    }
    state.error?.let { message ->
        item(key = "album-error-${route.id}") {
            CatalogErrorCard(message, onRetry)
        }
    }
    if (tracks.isNotEmpty()) {
        item(key = "album-tracks-heading-${route.id}") {
            BrowseSectionHeader(
                title = "Daftar lagu",
                monoLabel = "${tracks.size} lagu",
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        itemsIndexed(tracks, key = { index, track -> "album-track-${route.id}-$index-${track.id}" }) { index, track ->
            CatalogTrackRow(
                track = track,
                favorite = track.id in library.favoriteIds,
                trackNumber = index + 1,
                library = library,
                actions = actions,
                onPlay = { actions.playAll(tracks, PlaySource.Album(displayTitle), startIndex = index) },
            )
        }
    }
    if (!state.loading && state.error == null && detail != null && tracks.isEmpty()) {
        item(key = "album-empty-${route.id}") {
            BrowseNoticeCard(
                eyebrow = "Daftar lagu",
                message = "Belum ada lagu di album ini.",
            )
        }
    }
}

@Composable
private fun AlbumArtistChip(artistName: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier.minTouchTarget().semantics {
            role = Role.Button
            contentDescription = "Buka artis $artistName"
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = SenadaIcons.Person,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = artistName,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Icon(
                imageVector = SenadaIcons.ProfileChevron,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
internal fun CatalogTrackRow(
    track: Track,
    favorite: Boolean,
    trackNumber: Int? = null,
    library: LibraryState,
    actions: LibraryActions,
    onPlay: () -> Unit,
) {
    TrackRow(
        track = track,
        favorite = favorite,
        trackNumber = trackNumber,
        onClick = onPlay,
    ) {
        TrackMenu(track, library, actions)
    }
}

@Composable
internal fun CatalogSummaryRow(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    artworkShape: Shape,
    fallbackIcon: ImageVector,
    accessibilityLabel: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SenadaCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = accessibilityLabel
                    role = Role.Button
                }
                .padding(1.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CatalogArtwork(
                artworkUrl = artworkUrl,
                shape = artworkShape,
                fallbackIcon = fallbackIcon,
                imageSize = 120,
                modifier = Modifier.size(52.dp),
            )
            Column(
                modifier = Modifier.weight(1f).clearAndSetSemantics {},
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    style = browseRowTitleStyle(),
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        detail,
                        style = browseRowSubtitleStyle(),
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = SenadaIcons.ProfileChevron,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ReleaseCard(album: AlbumSummary, onClick: () -> Unit) {
    val detail = album.subtitle?.takeIf { it.isNotBlank() } ?: album.year
    val accessibilityLabel = listOfNotNull("Buka album ${album.title}", detail).joinToString(", ")
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    val cardWidth = if (largeFont) 148.dp else 132.dp
    Column(
        modifier = Modifier
            .width(cardWidth)
            .minTouchTarget()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = accessibilityLabel
                role = Role.Button
            },
    ) {
        CatalogArtwork(
            artworkUrl = album.artworkUrl,
            shape = RoundedCornerShape(LocalSenadaLook.current.listCoverRadius),
            fallbackIcon = SenadaIcons.Album,
            imageSize = 320,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Spacer(Modifier.height(6.dp))
        Column(modifier = Modifier.clearAndSetSemantics {}) {
            Text(
                text = album.title,
                style = homeTileTitleStyle(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (largeFont) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.let { text ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = text,
                    style = homeTileSubtitleStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (largeFont) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun CatalogArtwork(
    artworkUrl: String?,
    shape: Shape,
    fallbackIcon: ImageVector,
    imageSize: Int,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val borderColor = if (dark) Color.White.copy(alpha = 0.08f) else colors.outlineVariant
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.size(28.dp),
        )
        artworkUrl?.takeIf { it.isNotBlank() }?.let { url ->
            AsyncImage(
                model = remember(url, imageSize) { resizeGoogleArtwork(url, imageSize) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
internal fun CatalogErrorCard(message: String, onRetry: () -> Unit) {
    BrowseNoticeCard(
        eyebrow = "Terjadi kendala",
        message = message,
        actionLabel = "Coba lagi",
        onAction = onRetry,
    )
}

@Composable
internal fun PoliteStatusText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
