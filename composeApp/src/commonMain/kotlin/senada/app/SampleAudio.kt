package senada.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import senada.catalog.DemoCatalog
import senada.model.Track
import senada.playback.QueueItem
import senada.playback.SampleAudioOutput
import senada.playback.SampleAudioSelection
import senada.playback.SampleAudioSource
import senada.playback.SampleAudioState
import senada.playback.SampleAudioStatus
import senada.resources.Res

private const val HTTPS_SAMPLE = "https://raw.githubusercontent.com/Zeeynrs/Senada-Mobile/" +
    "9968da3556233a78d5812564d977954d010c1988/" +
    "composeApp/src/commonMain/composeResources/files/selepas-hujan.m4a"

internal fun sampleAudioSelection(source: SampleAudioSource): SampleAudioSelection {
    val track = DemoCatalog.tracks.first()
    return when (source) {
        SampleAudioSource.Local -> localAudioSelection(track)
        SampleAudioSource.Https -> SampleAudioSelection(track.title, source, HTTPS_SAMPLE)
    }
}

internal fun queueAudioSelection(entry: QueueItem): SampleAudioSelection =
    localAudioSelection(entry.track, entry.id)

@OptIn(ExperimentalResourceApi::class)
private fun localAudioSelection(track: Track, queueEntryId: Long? = null): SampleAudioSelection {
    val path = checkNotNull(DemoCatalog.localAudioPath(track.id))
    return SampleAudioSelection(track.title, SampleAudioSource.Local, Res.getUri(path), queueEntryId)
}

@Composable
internal expect fun rememberSampleAudioOutput(): SampleAudioOutput

@Composable
internal fun SampleAudioControls(
    title: String,
    state: SampleAudioState,
    onPlay: (SampleAudioSource) -> Unit,
    onPause: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Uji audio", style = MaterialTheme.typography.h6)
        Text(title)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onPlay(SampleAudioSource.Local) }, modifier = Modifier.weight(1f)) {
                Text("Putar lokal")
            }
            Button(onClick = { onPlay(SampleAudioSource.Https) }, modifier = Modifier.weight(1f)) {
                Text("Putar HTTPS")
            }
        }
        TextButton(onClick = onPause, enabled = state.isActive) { Text("Jeda") }

        val status = when (state.status) {
            SampleAudioStatus.Idle -> null
            SampleAudioStatus.Loading -> "Memuat audio…"
            SampleAudioStatus.Playing -> when (state.selection?.source) {
                SampleAudioSource.Local -> "Memutar file lokal"
                SampleAudioSource.Https -> "Memutar dari HTTPS"
                null -> null
            }
            SampleAudioStatus.Paused -> "Dijeda"
            SampleAudioStatus.Ended -> "Pemutaran selesai"
            SampleAudioStatus.Failed -> "Audio gagal diputar. Coba lagi."
        }
        val selectedTitle = state.selection?.title
        if (status != null && selectedTitle != null) {
            Text("$selectedTitle · $status", style = MaterialTheme.typography.body2)
        }
    }
}
