package senada.app.tabs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import senada.app.ImportAudioButton
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.searchHistoryItems
import senada.model.Track

/** Kueri dan hasil tab Cari; hasil terbaru menang, mengetik lagi membatalkan pencarian sebelumnya. */
internal class SearchTabState(queryState: MutableState<String>, private val scope: CoroutineScope) {
    var query by queryState
    var search by mutableStateOf(SearchState())
        private set
    var attempt by mutableStateOf(0)
        private set
    private var moreJob: Job? = null

    fun loadMore() {
        moreJob?.cancel()
        moreJob = scope.launch {
            search = search.copy(loading = true, error = null)
            search = loadSearchPage(search)
        }
    }

    /** Saat gagal: lanjutkan halaman berikutnya bila ada, selain itu ulangi pencarian. */
    fun retry() {
        if (search.nextPageToken != null) loadMore() else attempt++
    }

    suspend fun refresh() {
        moreJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            search = SearchState()
            return
        }
        search = SearchState(query = trimmed, tracks = emptyList(), loading = true)
        delay(SEARCH_DEBOUNCE_MS)
        search = loadSearchPage(search)
    }
}

@Composable
internal fun rememberSearchTabState(): SearchTabState {
    val query = rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val state = remember(query, scope) { SearchTabState(query, scope) }
    LaunchedEffect(state.query, state.attempt) { state.refresh() }
    return state
}

/** Tab Cari: kolom cari, impor file audio, riwayat pencarian saat kosong, lalu hasil. */
internal fun LazyListScope.searchTab(
    tab: SearchTabState,
    searchHistory: List<String>,
    library: LibraryState,
    actions: LibraryActions,
    onImported: (Track) -> Unit,
    onRecordSearch: () -> Unit,
    onRemoveSearch: (String) -> Unit,
    onClearSearches: () -> Unit,
) {
    item(key = "search-field") {
        val focusManager = LocalFocusManager.current
        OutlinedTextField(
            value = tab.query,
            onValueChange = { tab.query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cari judul atau artis", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onRecordSearch()
                focusManager.clearFocus()
            }),
        )
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
    searchResults(
        search = tab.search,
        library = library,
        actions = actions,
        onRetry = tab::retry,
        onLoadMore = tab::loadMore,
        onResultUsed = onRecordSearch,
    )
}
