package senada.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import senada.catalog.CatalogException
import senada.catalog.DemoCatalog
import senada.model.Track
import senada.playback.PlaybackController

@Composable
internal fun SenadaApp(playback: PlaybackController) {
    // Sampul diunduh dengan klien HTTP yang sama dengan katalog.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(SenadaSources.http)) }
            .build()
    }
    val state by playback.state.collectAsState()
    val queue = state.queue
    val audioState = state.audio
    var query by rememberSaveable { mutableStateOf("") }
    var search by remember { mutableStateOf(SearchState()) }
    var searchAttempt by remember { mutableStateOf(0) }
    var moreJob by remember { mutableStateOf<Job?>(null) }
    val uiScope = rememberCoroutineScope()

    // Hasil terbaru menang: mengetik lagi membatalkan pencarian dan "muat lebih banyak" sebelumnya.
    LaunchedEffect(query, searchAttempt) {
        moreJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            search = SearchState()
            return@LaunchedEffect
        }
        search = SearchState(query = trimmed, tracks = emptyList(), loading = true)
        delay(SEARCH_DEBOUNCE_MS)
        search = loadSearchPage(search)
    }
    val loadMore = {
        moreJob?.cancel()
        moreJob = uiScope.launch {
            search = search.copy(loading = true, error = null)
            search = loadSearchPage(search)
        }
    }

    // Audio tetap berjalan di latar belakang; layar hanya mengatur kapan posisi perlu dibaca berkala.
    LifecycleStartEffect(playback) {
        playback.setUiVisible(true)
        onStopOrDispose { playback.setUiVisible(false) }
    }

    MaterialTheme(colors = if (isSystemInDarkTheme()) darkColors() else lightColors()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("Senada", style = MaterialTheme.typography.h4) }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Cari judul atau artis") },
                        singleLine = true,
                    )
                }
                item {
                    val heading = if (search.query.isEmpty()) "Katalog contoh" else "YouTube Music"
                    Text(heading, style = MaterialTheme.typography.h6)
                }
                if (search.loading && search.tracks.isEmpty()) {
                    item { Text("Mencari…") }
                }
                search.error?.let { message ->
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.body2)
                            TextButton(onClick = {
                                if (search.nextPageToken != null) loadMore() else searchAttempt++
                            }) { Text("Coba lagi") }
                        }
                    }
                }
                if (!search.loading && search.error == null && search.tracks.isEmpty()) {
                    item { Text("Lagu tidak ditemukan.") }
                }
                items(search.tracks, key = { "result-${it.id}" }) { track ->
                    TrackRow(track) {
                        Button(onClick = { playback.add(track) }) { Text("Tambah") }
                    }
                }
                if (search.nextPageToken != null && search.error == null) {
                    item {
                        TextButton(onClick = loadMore, enabled = !search.loading) {
                            Text(if (search.loading) "Memuat…" else "Muat lebih banyak")
                        }
                    }
                }
                item {
                    SampleAudioControls(
                        title = DemoCatalog.tracks.first().title,
                        state = audioState,
                        onPlay = { source -> playback.play(sampleAudioSelection(source)) },
                        onPause = playback::pause,
                        onSeek = playback::seekTo,
                        onPrevious = playback::previous,
                        onNext = playback::next,
                        canGoPrevious = audioState.selection != null,
                        canGoNext = state.canGoNext,
                        problem = state.problem,
                    )
                }
                item { Text("Antrean", style = MaterialTheme.typography.h6) }
                if (queue.entries.isEmpty()) {
                    item { Text("Antrean kosong.") }
                }
                items(queue.entries, key = { "entry-${it.id}" }) { entry ->
                    val isPlayingEntry = audioState.isActive && audioState.selection?.queueEntryId == entry.id
                    TrackRow(entry.track) {
                        TextButton(onClick = {
                            if (isPlayingEntry) playback.pause() else playback.playEntry(entry.id)
                        }) {
                            Text(if (isPlayingEntry) "Jeda" else "Putar")
                        }
                        TextButton(onClick = { playback.remove(entry.id) }) {
                            Text("Hapus")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, action: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Kotak tetap ada tanpa sampul agar judul di daftar sejajar.
        Box(
            Modifier.size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
        ) {
            track.artworkUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.subtitle1)
            val details = listOfNotNull(
                track.artists.joinToString().takeIf { it.isNotEmpty() },
                track.durationMs?.let(::playbackTime),
            )
            Text(details.joinToString(" · "), style = MaterialTheme.typography.body2)
        }
        action()
    }
}

private const val SEARCH_DEBOUNCE_MS = 350L

/** Hasil pencarian yang tampil; kueri kosong menampilkan katalog contoh. */
private data class SearchState(
    val query: String = "",
    val tracks: List<Track> = DemoCatalog.tracks,
    val nextPageToken: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Memuat halaman berikutnya (atau pertama); saat gagal, hasil dan token tetap ada untuk dicoba lagi. */
private suspend fun loadSearchPage(current: SearchState): SearchState = try {
    val page = SenadaSources.youTubeMusic.search(current.query, current.nextPageToken)
    current.copy(
        tracks = (current.tracks + page.items).distinctBy { it.id },
        nextPageToken = page.nextPageToken,
        loading = false,
        error = null,
    )
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    current.copy(loading = false, error = (e as? CatalogException)?.userMessage ?: "Pencarian gagal. Coba lagi.")
}
