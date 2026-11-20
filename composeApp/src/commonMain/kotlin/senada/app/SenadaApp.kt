package senada.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import senada.catalog.DemoCatalog
import senada.model.Track
import senada.playback.PlaybackController

@Composable
internal fun SenadaApp(playback: PlaybackController) {
    val state by playback.state.collectAsState()
    val queue = state.queue
    val audioState = state.audio
    var query by rememberSaveable { mutableStateOf("") }
    val searchResults by produceState(DemoCatalog.tracks, query) {
        value = DemoCatalog.search(query).items
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
                item { Text("Katalog contoh", style = MaterialTheme.typography.h6) }
                if (searchResults.isEmpty()) {
                    item { Text("Lagu tidak ditemukan.") }
                }
                items(searchResults, key = { it.id }) { track ->
                    TrackRow(track) {
                        Button(onClick = { playback.add(track) }) { Text("Tambah") }
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
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.subtitle1)
            Text(track.artists.joinToString(), style = MaterialTheme.typography.body2)
        }
        action()
    }
}
