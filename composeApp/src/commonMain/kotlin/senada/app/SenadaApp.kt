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
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import senada.model.Track

private val sampleTracks = listOf(
    Track("demo:track:hujan", "Selepas Hujan", listOf("Senada"), source = "demo"),
    Track("demo:track:pagi", "Sebelum Pagi", listOf("Senada"), source = "demo"),
)

@Composable
fun SenadaApp() {
    val model = viewModel { QueueViewModel() }
    val queue = model.queue

    MaterialTheme(colors = if (isSystemInDarkTheme()) darkColors() else lightColors()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("Senada", style = MaterialTheme.typography.h4) }
                item { Text("Lagu contoh", style = MaterialTheme.typography.h6) }
                items(sampleTracks, key = { it.id }) { track ->
                    TrackRow(track) {
                        Button(onClick = { model.add(track) }) { Text("Tambah") }
                    }
                }
                item { Text("Antrean", style = MaterialTheme.typography.h6) }
                if (queue.entries.isEmpty()) {
                    item { Text("Antrean kosong.") }
                }
                items(queue.entries, key = { "entry-${it.id}" }) { entry ->
                    TrackRow(entry.track) {
                        TextButton(onClick = { model.remove(entry.id) }) { Text("Hapus") }
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
