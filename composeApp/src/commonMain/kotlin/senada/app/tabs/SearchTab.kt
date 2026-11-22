package senada.app.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import senada.app.ImportAudioButton
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.SenadaSources
import senada.app.minTouchTarget
import senada.app.searchHistoryItems
import senada.app.shell.SenadaIcons
import senada.catalog.AlbumSummary
import senada.catalog.ArtistSummary
import senada.catalog.CatalogBrowser
import senada.catalog.PlaylistSummary
import senada.model.Track

/** Kueri, filter, dan hasil tab Cari; hasil terbaru menang, mengetik lagi membatalkan pencarian sebelumnya. */
internal class SearchTabState(
    queryState: MutableState<String>,
    filterState: MutableState<SearchFilter>,
    private val scope: CoroutineScope,
    private val catalog: CatalogBrowser = SenadaSources.youTubeMusic,
    private val debounceMs: Long = SEARCH_DEBOUNCE_MS,
) {
    var query by queryState
    var filter by filterState
    var search by mutableStateOf(SearchState())
        private set
    var artistSearch by mutableStateOf(ArtistSearchState())
        private set
    var albumSearch by mutableStateOf(AlbumSearchState())
        private set
    var playlistSearch by mutableStateOf(PlaylistSearchState())
        private set
    var attempt by mutableStateOf(0)
        private set
    private var moreJob: Job? = null
    private var lastRefreshedQuery: String = ""
    private var lastAttempt: Int = 0

    fun loadMore() {
        moreJob?.cancel()
        val currentFilter = filter
        moreJob = scope.launch {
            when (currentFilter) {
                SearchFilter.Songs -> {
                    search = search.copy(loading = true, error = null)
                    search = loadSearchPage(search, catalog)
                }
                SearchFilter.Artists -> {
                    artistSearch = artistSearch.copy(loading = true, error = null)
                    artistSearch = loadArtistSearchPage(artistSearch, catalog)
                }
                SearchFilter.Albums -> {
                    albumSearch = albumSearch.copy(loading = true, error = null)
                    albumSearch = loadAlbumSearchPage(albumSearch, catalog)
                }
                SearchFilter.Playlists -> {
                    playlistSearch = playlistSearch.copy(loading = true, error = null)
                    playlistSearch = loadPlaylistSearchPage(playlistSearch, catalog)
                }
            }
        }
    }

    /** Saat gagal: lanjutkan halaman berikutnya bila ada, selain itu ulangi pencarian. */
    fun retry() {
        val hasNext = when (filter) {
            SearchFilter.Songs -> search.nextPageToken != null
            SearchFilter.Artists -> artistSearch.nextPageToken != null
            SearchFilter.Albums -> albumSearch.nextPageToken != null
            SearchFilter.Playlists -> playlistSearch.nextPageToken != null
        }
        if (hasNext) loadMore() else attempt++
    }

    suspend fun refresh() {
        moreJob?.cancel()
        val trimmed = query.trim()
        val queryChanged = trimmed != lastRefreshedQuery
        val retried = attempt != lastAttempt
        lastRefreshedQuery = trimmed
        lastAttempt = attempt

        if (trimmed.isEmpty()) {
            search = SearchState()
            artistSearch = ArtistSearchState()
            albumSearch = AlbumSearchState()
            playlistSearch = PlaylistSearchState()
            return
        }
        if (queryChanged) {
            if (search.query != trimmed) search = SearchState(query = "", tracks = emptyList())
            if (artistSearch.query != trimmed) artistSearch = ArtistSearchState()
            if (albumSearch.query != trimmed) albumSearch = AlbumSearchState()
            if (playlistSearch.query != trimmed) playlistSearch = PlaylistSearchState()
        }
        when (filter) {
            SearchFilter.Songs -> {
                if (!queryChanged && !retried && search.query == trimmed && search.error == null && !search.loading) return
                search = SearchState(query = trimmed, tracks = emptyList(), loading = true)
                if (queryChanged && debounceMs > 0) delay(debounceMs)
                search = loadSearchPage(search, catalog)
            }
            SearchFilter.Artists -> {
                if (!queryChanged && !retried && artistSearch.query == trimmed && artistSearch.error == null && !artistSearch.loading) return
                artistSearch = ArtistSearchState(query = trimmed, loading = true)
                if (queryChanged && debounceMs > 0) delay(debounceMs)
                artistSearch = loadArtistSearchPage(artistSearch, catalog)
            }
            SearchFilter.Albums -> {
                if (!queryChanged && !retried && albumSearch.query == trimmed && albumSearch.error == null && !albumSearch.loading) return
                albumSearch = AlbumSearchState(query = trimmed, loading = true)
                if (queryChanged && debounceMs > 0) delay(debounceMs)
                albumSearch = loadAlbumSearchPage(albumSearch, catalog)
            }
            SearchFilter.Playlists -> {
                if (!queryChanged && !retried && playlistSearch.query == trimmed && playlistSearch.error == null && !playlistSearch.loading) return
                playlistSearch = PlaylistSearchState(query = trimmed, loading = true)
                if (queryChanged && debounceMs > 0) delay(debounceMs)
                playlistSearch = loadPlaylistSearchPage(playlistSearch, catalog)
            }
        }
    }
}

@Composable
internal fun rememberSearchTabState(): SearchTabState {
    val query = rememberSaveable { mutableStateOf("") }
    val filter = rememberSaveable { mutableStateOf(SearchFilter.Songs) }
    val scope = rememberCoroutineScope()
    val state = remember(query, filter, scope) { SearchTabState(query, filter, scope) }
    LaunchedEffect(state.query, state.filter, state.attempt) { state.refresh() }
    return state
}

/** Tab Cari: kolom cari pil 52 dp, chip filter Lagu/Artis/Album/Playlist, impor audio, riwayat saat kosong, lalu hasil. */
internal fun LazyListScope.searchTab(
    tab: SearchTabState,
    searchHistory: List<String>,
    library: LibraryState,
    actions: LibraryActions,
    onImported: suspend (Track) -> Unit,
    onRecordSearch: () -> Unit,
    onRemoveSearch: (String) -> Unit,
    onClearSearches: () -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit = {},
) {
    item(key = "search-field") {
        val focusManager = LocalFocusManager.current
        SearchPillField(
            query = tab.query,
            onQueryChange = { tab.query = it },
            onSearch = {
                onRecordSearch()
                focusManager.clearFocus()
            },
        )
    }
    item(key = "search-filters") {
        SearchFilterChips(selected = tab.filter, onSelect = { tab.filter = it })
    }
    item(key = "import-audio") { ImportAudioButton(onImported = onImported) }
    if (tab.query.isBlank()) {
        searchHistoryItems(
            history = searchHistory,
            onPick = { tab.query = it },
            onRemove = onRemoveSearch,
            onClear = onClearSearches,
        )
    }
    when (tab.filter) {
        SearchFilter.Songs -> searchResults(
            search = tab.search,
            library = library,
            actions = actions,
            onRetry = tab::retry,
            onLoadMore = tab::loadMore,
            onResultUsed = onRecordSearch,
        )
        SearchFilter.Artists -> artistSearchResults(
            state = tab.artistSearch,
            onOpenArtist = onOpenArtist,
            onRetry = tab::retry,
            onLoadMore = tab::loadMore,
            onResultUsed = onRecordSearch,
        )
        SearchFilter.Albums -> albumSearchResults(
            state = tab.albumSearch,
            onOpenAlbum = onOpenAlbum,
            onRetry = tab::retry,
            onLoadMore = tab::loadMore,
            onResultUsed = onRecordSearch,
        )
        SearchFilter.Playlists -> playlistSearchResults(
            state = tab.playlistSearch,
            onOpenPlaylist = onOpenPlaylist,
            onRetry = tab::retry,
            onLoadMore = tab::loadMore,
            onResultUsed = onRecordSearch,
        )
    }
}

/** Kolom cari berbentuk pil setinggi minimal 52 dp yang tetap meluas saat teks diperbesar. */
@Composable
private fun SearchPillField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            interactionSource = interactionSource,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(start = 16.dp, end = if (query.isNotEmpty()) 4.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = SenadaIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Box(
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                "Cari judul atau artis",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.minTouchTarget().semantics {
                                contentDescription = "Hapus pencarian"
                            },
                        ) {
                            Icon(
                                imageVector = SenadaIcons.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}
