package senada.app.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import senada.app.AdaptiveRow
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.SenadaSources
import senada.app.TrackMenu
import senada.app.TrackRow
import senada.app.minTouchTarget
import senada.app.nowplaying.PlaySource
import senada.app.shell.CatalogRoute
import senada.app.shell.SenadaIcons
import senada.catalog.AlbumDetail
import senada.catalog.AlbumSummary
import senada.catalog.ArtistDetail
import senada.catalog.ArtistSummary
import senada.catalog.CatalogBrowser
import senada.catalog.CatalogException
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

/** Deret chip filter pil di tab Cari yang dapat digeser mendatar agar aman pada skala font besar. */
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
            Text(
                "Ketik nama artis untuk mencari di YouTube Music.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    item(key = "artist-search-heading") {
        Text(
            "Artis di YouTube Music",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
    }
    if (state.loading && state.items.isEmpty()) {
        item(key = "artist-search-loading") {
            PoliteStatusText("Mencari artis…")
        }
    }
    state.error?.let { message ->
        item(key = "artist-search-error") {
            CatalogErrorCard(message, onRetry)
        }
    }
    if (!state.loading && state.error == null && state.items.isEmpty()) {
        item(key = "artist-search-empty") {
            PoliteStatusText("Artis tidak ditemukan.")
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
            OutlinedButton(
                onClick = onLoadMore,
                enabled = !state.loading,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().minTouchTarget(),
            ) {
                Text(if (state.loading) "Memuat…" else "Muat lebih banyak")
            }
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
            Text(
                "Ketik judul album untuk mencari di YouTube Music.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    item(key = "album-search-heading") {
        Text(
            "Album di YouTube Music",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
    }
    if (state.loading && state.items.isEmpty()) {
        item(key = "album-search-loading") {
            PoliteStatusText("Mencari album…")
        }
    }
    state.error?.let { message ->
        item(key = "album-search-error") {
            CatalogErrorCard(message, onRetry)
        }
    }
    if (!state.loading && state.error == null && state.items.isEmpty()) {
        item(key = "album-search-empty") {
            PoliteStatusText("Album tidak ditemukan.")
        }
    }
    items(state.items, key = { "album-result-${it.id}" }) { album ->
        val label = listOfNotNull("Buka album ${album.title}", album.subtitle).joinToString(", ")
        CatalogSummaryRow(
            title = album.title,
            subtitle = album.subtitle,
            artworkUrl = album.artworkUrl,
            artworkShape = MaterialTheme.shapes.extraSmall,
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
            OutlinedButton(
                onClick = onLoadMore,
                enabled = !state.loading,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().minTouchTarget(),
            ) {
                Text(if (state.loading) "Memuat…" else "Muat lebih banyak")
            }
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

/** Menyimpan halaman artis, album, dan playlist yang baru dibuka agar navigasi kembali tampil instan. */
internal class CatalogDetailStore(
    private val catalog: CatalogBrowser = SenadaSources.youTubeMusic,
    private val maxEntries: Int = 20,
) {
    private val artists = mutableMapOf<String, ArtistDetail>()
    private val albums = mutableMapOf<String, AlbumDetail>()
    private val playlists = mutableMapOf<String, PlaylistDetail>()

    fun cachedArtist(id: String): ArtistDetail? = artists[id]
    fun cachedAlbum(id: String): AlbumDetail? = albums[id]
    fun cachedPlaylist(id: String): PlaylistDetail? = playlists[id]

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

    private fun <T> putBounded(map: MutableMap<String, T>, key: String, value: T) {
        map.remove(key)
        map[key] = value
        while (map.size > maxEntries) {
            val oldest = map.keys.firstOrNull() ?: break
            map.remove(oldest)
        }
    }
}

/** State Compose untuk halaman detail artis, album, atau playlist yang sedang dibuka. */
internal class CatalogPageState {
    var artist by mutableStateOf(CatalogLoadState<ArtistDetail>())
        internal set
    var album by mutableStateOf(CatalogLoadState<AlbumDetail>())
        internal set
    var playlist by mutableStateOf(CatalogLoadState<PlaylistDetail>())
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
                state.playlistSaves = emptyMap()
            }
            is CatalogRoute.Artist -> {
                state.album = CatalogLoadState()
                state.playlist = CatalogLoadState()
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
                val cached = store.cachedPlaylist(route.id)
                if (cached != null) {
                    state.playlist = CatalogLoadState(routeId = route.id, data = cached)
                } else {
                    state.playlist = CatalogLoadState(routeId = route.id, loading = true)
                    state.playlist = store.loadPlaylist(route.id)
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
        BackLink(previousLabel, onBack)
    }
    item(key = "artist-header-${route.id}") {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CatalogArtwork(
                    artworkUrl = artworkUrl,
                    shape = CircleShape,
                    fallbackIcon = SenadaIcons.Person,
                    imageSize = 544,
                    modifier = Modifier.size(116.dp),
                )
                Text(
                    "Artis",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                detail?.monthlyListeners?.let { listeners ->
                    Text(
                        listeners,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { actions.playAll(popularSongs, PlaySource.Artist(displayName)) },
                        enabled = popularSongs.isNotEmpty(),
                        shape = CircleShape,
                        modifier = Modifier.minTouchTarget().semantics {
                            contentDescription = "Putar lagu terpopuler $displayName"
                        },
                    ) {
                        Icon(
                            imageVector = SenadaIcons.Play,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Putar", modifier = Modifier.clearAndSetSemantics {})
                    }
                }
            }
        }
    }

    if (state.loading && detail == null) {
        item(key = "artist-loading-${route.id}") {
            PoliteStatusText("Memuat halaman artis…")
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
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                    )
                }
                itemsIndexed(section.songs, key = { _, track -> "artist-song-${route.id}-${track.id}" }) { index, track ->
                    CatalogTrackRow(
                        track = track,
                        favorite = track.id in library.favoriteIds,
                        library = library,
                        actions = actions,
                        onPlay = { actions.playAll(section.songs, PlaySource.Artist(displayName), startIndex = index) },
                    )
                }
                section.fullListPlaylistId?.let { playlistId ->
                    item(key = "artist-songs-all-${route.id}") {
                        ArtistSeeAllRow(
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
            is ArtistPageSection.PopularAlbums -> {
                item(key = "artist-popular-albums-heading-${route.id}") {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
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
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
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
            PoliteStatusText("Belum ada lagu atau album untuk artis ini.")
        }
    }
}

@Composable
private fun ArtistSeeAllRow(artistName: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().minTouchTarget().semantics {
            contentDescription = "Lihat semua lagu terpopuler $artistName"
            role = Role.Button
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Lihat semua",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clearAndSetSemantics {},
            )
            Icon(
                imageVector = SenadaIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
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
) {
    val detail = state.data
    val displayTitle = detail?.title?.takeIf { it.isNotBlank() } ?: route.label
    val artworkUrl = detail?.artworkUrl ?: route.artworkUrl
    val tracks = detail?.tracks.orEmpty()
    val metadata = if (detail != null) {
        listOfNotNull(
            detail.artists.joinToString().takeIf { it.isNotEmpty() },
            detail.year,
            "${tracks.size} lagu".takeIf { tracks.isNotEmpty() },
        ).joinToString(" · ")
    } else {
        route.subtitle.orEmpty()
    }

    item(key = "album-back-${route.id}") {
        BackLink(previousLabel, onBack)
    }
    item(key = "album-header-${route.id}") {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CatalogArtwork(
                    artworkUrl = artworkUrl,
                    shape = MaterialTheme.shapes.medium,
                    fallbackIcon = SenadaIcons.Album,
                    imageSize = 544,
                    modifier = Modifier.size(160.dp),
                )
                Text(
                    detail?.typeLabel ?: "Album",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    displayTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                if (metadata.isNotEmpty()) {
                    Text(
                        metadata,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { actions.playAll(tracks, PlaySource.Album(displayTitle)) },
                        enabled = tracks.isNotEmpty(),
                        shape = CircleShape,
                        modifier = Modifier.minTouchTarget().semantics {
                            contentDescription = "Putar album $displayTitle"
                        },
                    ) {
                        Icon(
                            imageVector = SenadaIcons.Play,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Putar", modifier = Modifier.clearAndSetSemantics {})
                    }
                    FilledTonalButton(
                        onClick = { actions.enqueueAll(tracks) },
                        enabled = tracks.isNotEmpty(),
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.minTouchTarget().semantics {
                            contentDescription = "Tambah semua lagu $displayTitle ke antrean"
                        },
                    ) {
                        Text("Tambah semua", modifier = Modifier.clearAndSetSemantics {})
                    }
                }
            }
        }
    }

    if (state.loading && detail == null) {
        item(key = "album-loading-${route.id}") {
            PoliteStatusText("Memuat halaman album…")
        }
    }
    state.error?.let { message ->
        item(key = "album-error-${route.id}") {
            CatalogErrorCard(message, onRetry)
        }
    }
    if (tracks.isNotEmpty()) {
        item(key = "album-tracks-heading-${route.id}") {
            Text(
                "Daftar lagu",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
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
            PoliteStatusText("Belum ada lagu di album ini.")
        }
    }
}

@Composable
internal fun BackLink(previousLabel: String, onBack: () -> Unit) {
    TextButton(
        onClick = onBack,
        modifier = Modifier.minTouchTarget().semantics {
            contentDescription = "Kembali ke $previousLabel"
        },
    ) {
        Text(
            "‹ $previousLabel",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
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
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().minTouchTarget().semantics {
            contentDescription = accessibilityLabel
            role = Role.Button
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp),
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
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = SenadaIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ReleaseCard(album: AlbumSummary, onClick: () -> Unit) {
    val detail = album.subtitle?.takeIf { it.isNotBlank() } ?: album.year
    val accessibilityLabel = listOfNotNull("Buka album ${album.title}", detail).joinToString(", ")
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.width(156.dp).minTouchTarget().semantics {
            contentDescription = accessibilityLabel
            role = Role.Button
        },
    ) {
        Column {
            CatalogArtwork(
                artworkUrl = album.artworkUrl,
                shape = MaterialTheme.shapes.medium,
                fallbackIcon = SenadaIcons.Album,
                imageSize = 320,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Column(
                modifier = Modifier.padding(12.dp).clearAndSetSemantics {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    album.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                detail?.let { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
) {
    Box(
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
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
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AdaptiveRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            minPrimaryWidth = 160.dp,
            primary = {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            },
            trailing = {
                TextButton(onClick = onRetry, modifier = Modifier.minTouchTarget()) { Text("Coba lagi") }
            },
        )
    }
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
