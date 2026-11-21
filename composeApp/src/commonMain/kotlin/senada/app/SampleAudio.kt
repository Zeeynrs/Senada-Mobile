package senada.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong
import kotlinx.coroutines.MainScope
import org.jetbrains.compose.resources.ExperimentalResourceApi
import senada.catalog.DemoCatalog
import senada.catalog.youtube.YOUTUBE_MUSIC_SOURCE
import senada.model.Track
import senada.playback.PlaybackController
import senada.playback.QueueItem
import senada.playback.QueueStore
import senada.playback.RepeatMode
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
        // Tombol uji hanya memakai file lokal dan satu URL HTTPS tetap.
        SampleAudioSource.Https, SampleAudioSource.YouTubeMusic ->
            SampleAudioSelection(track.title, SampleAudioSource.Https, HTTPS_SAMPLE)
    }
}

/** Alamat audio entri antrean: file contoh di aplikasi, atau stream YouTube Music yang dicari saat diputar. */
internal suspend fun queueAudioSelection(entry: QueueItem): SampleAudioSelection = when (entry.track.source) {
    YOUTUBE_MUSIC_SOURCE -> {
        val audio = SenadaSources.streams.resolve(entry.track)
        SampleAudioSelection(
            entry.track.title,
            SampleAudioSource.YouTubeMusic,
            audio.location,
            entry.id,
            expiresAtEpochMs = audio.expiresAtEpochMs,
        )
    }
    else -> localAudioSelection(entry.track, entry.id)
}

@OptIn(ExperimentalResourceApi::class)
private fun localAudioSelection(track: Track, queueEntryId: Long? = null): SampleAudioSelection {
    val path = checkNotNull(DemoCatalog.localAudioPath(track.id))
    return SampleAudioSelection(track.title, SampleAudioSource.Local, Res.getUri(path), queueEntryId)
}

/** Satu controller untuk seluruh proses aplikasi; dibuat oleh titik masuk Android atau iOS. */
internal fun createPlaybackController(output: SampleAudioOutput, queueStore: QueueStore): PlaybackController =
    PlaybackController(output, MainScope(), selectionFor = ::queueAudioSelection)
        .apply { persistQueue(queueStore) }

@Composable
internal fun SampleAudioControls(
    title: String,
    state: SampleAudioState,
    onPlay: (SampleAudioSource) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    canResume: Boolean,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    repeatMode: RepeatMode,
    onRepeatModeChange: (RepeatMode) -> Unit,
    shuffleEnabled: Boolean,
    onShuffleChange: (Boolean) -> Unit,
    problem: String? = null,
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
        PlaybackProgress(state, onSeek)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onPrevious, enabled = canGoPrevious) { Text("Sebelumnya") }
            // Satu tombol putar/jeda: melanjutkan lagu terpilih dari posisinya, atau memulai antrean.
            if (state.isActive) {
                TextButton(onClick = onPause) { Text("Jeda") }
            } else {
                TextButton(onClick = onResume, enabled = canResume) { Text("Putar") }
            }
            TextButton(onClick = onNext, enabled = canGoNext) { Text("Berikutnya") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val (repeatLabel, nextMode) = when (repeatMode) {
                RepeatMode.Off -> "Mati" to RepeatMode.All
                RepeatMode.All -> "Semua" to RepeatMode.One
                RepeatMode.One -> "Satu" to RepeatMode.Off
            }
            TextButton(onClick = { onRepeatModeChange(nextMode) }) { Text("Ulangi: $repeatLabel") }
            TextButton(onClick = { onShuffleChange(!shuffleEnabled) }) {
                Text(if (shuffleEnabled) "Acak: Nyala" else "Acak: Mati")
            }
        }

        val status = when (state.status) {
            SampleAudioStatus.Idle -> null
            SampleAudioStatus.Loading -> "Memuat audio…"
            SampleAudioStatus.Playing -> when (state.selection?.source) {
                SampleAudioSource.Local -> "Memutar file lokal"
                SampleAudioSource.Https -> "Memutar dari HTTPS"
                SampleAudioSource.YouTubeMusic -> "Memutar dari YouTube Music"
                null -> null
            }
            SampleAudioStatus.Paused -> "Dijeda"
            SampleAudioStatus.Ended -> "Pemutaran selesai"
            SampleAudioStatus.Failed -> problem ?: "Audio gagal diputar. Coba lagi."
        }
        val selectedTitle = state.selection?.title
        if (status != null && selectedTitle != null) {
            Text("$selectedTitle · $status", style = MaterialTheme.typography.body2)
        }
    }
}

@Composable
private fun PlaybackProgress(state: SampleAudioState, onSeek: (Long) -> Unit) {
    val duration = state.durationMs
    if (duration == null) {
        if (state.status == SampleAudioStatus.Paused && state.selection?.isPending == true && state.positionMs > 0) {
            Text(playbackTime(state.positionMs), style = MaterialTheme.typography.body2)
        }
        return
    }
    var scrubFraction by remember(state.selection, state.canSeek) { mutableStateOf<Float?>(null) }
    val progress = scrubFraction ?: (state.positionMs.toDouble() / duration).toFloat()
    val shownPosition = scrubFraction?.let { (it * duration.toDouble()).roundToLong() } ?: state.positionMs

    Slider(
        value = progress,
        onValueChange = { scrubFraction = it },
        onValueChangeFinished = {
            scrubFraction?.let { onSeek((it * duration.toDouble()).roundToLong()) }
            scrubFraction = null
        },
        enabled = state.canSeek,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Posisi pemutaran" },
    )
    Text("${playbackTime(shownPosition)} / ${playbackTime(duration)}", style = MaterialTheme.typography.body2)
}

internal fun playbackTime(positionMs: Long): String {
    val seconds = positionMs / 1_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
