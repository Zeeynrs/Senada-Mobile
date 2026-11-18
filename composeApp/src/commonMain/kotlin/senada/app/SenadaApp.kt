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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import senada.catalog.DemoCatalog
import senada.model.Track
import senada.playback.SampleAudioPlayer

@Composable
fun SenadaApp() {
    val model = viewModel { QueueViewModel() }
    val queue = model.queue
    var query by rememberSaveable { mutableStateOf("") }
    val searchResults by produceState(DemoCatalog.tracks, query) {
        value = DemoCatalog.search(query).items
    }
    val audioOutput = rememberSampleAudioOutput()
    val audio = remember(audioOutput) { SampleAudioPlayer(audioOutput) }
    var audioState by remember(audio) { mutableStateOf(audio.state) }

    DisposableEffect(audioOutput) {
        onDispose { audioOutput.release() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        audio.pause()
        audioState = audio.state
    }
    LaunchedEffect(audio, audioState.isActive) {
        while (audioState.isActive) {
            delay(250)
            audio.refresh()
            audio.advanceAfterEnd(model.queue, ::queueAudioSelection)
            audioState = audio.state
        }
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
                        Button(onClick = { model.add(track) }) { Text("Tambah") }
                    }
                }
                item {
                    val selectedEntryId = audioState.selection?.queueEntryId
                    SampleAudioControls(
                        title = DemoCatalog.tracks.first().title,
                        state = audioState,
                        onPlay = { source ->
                            audio.play(sampleAudioSelection(source))
                            audioState = audio.state
                        },
                        onPause = {
                            audio.pause()
                            audioState = audio.state
                        },
                        onSeek = { position ->
                            audio.seekTo(position)
                            audioState = audio.state
                        },
                        onMove = { offset ->
                            val currentEntryId = audio.state.selection?.queueEntryId
                            model.queue.adjacentTo(currentEntryId, offset)?.let { entry ->
                                audio.play(queueAudioSelection(entry))
                                audioState = audio.state
                            }
                        },
                        canGoPrevious = queue.adjacentTo(selectedEntryId, -1) != null,
                        canGoNext = queue.adjacentTo(selectedEntryId, 1) != null,
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
                            if (isPlayingEntry) audio.pause() else audio.play(queueAudioSelection(entry))
                            audioState = audio.state
                        }) {
                            Text(if (isPlayingEntry) "Jeda" else "Putar")
                        }
                        TextButton(onClick = {
                            model.remove(entry.id)
                            audio.onQueueEntryRemoved(entry.id)
                            audioState = audio.state
                        }) {
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
