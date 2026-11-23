package senada.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import senada.catalog.DemoCatalog
import senada.catalog.youtube.YOUTUBE_MUSIC_SOURCE
import senada.local.LOCAL_FILE_SOURCE
import senada.local.LocalAudioException
import senada.local.LocalAudioFiles
import senada.local.localFileNameOf
import senada.model.Track
import senada.playback.PlaybackController
import senada.playback.QueueItem
import senada.playback.QueueStore
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

/**
 * Alamat audio entri antrean: stream YouTube Music yang dicari saat diputar, file audio yang diimpor
 * pengguna, atau file contoh di aplikasi.
 */
internal suspend fun queueAudioSelection(
    entry: QueueItem,
    saveData: suspend () -> Boolean = { false },
    localFiles: LocalAudioFiles,
): SampleAudioSelection = when (entry.track.source) {
    YOUTUBE_MUSIC_SOURCE -> {
        val audio = SenadaSources.streams.resolve(entry.track, saveData())
        SampleAudioSelection(
            entry.track.title,
            SampleAudioSource.YouTubeMusic,
            audio.location,
            entry.id,
            expiresAtEpochMs = audio.expiresAtEpochMs,
            durationMs = audio.durationMs,
        )
    }
    LOCAL_FILE_SOURCE -> {
        val uri = localFileNameOf(entry.track)?.let(localFiles::uriFor)
            ?: throw LocalAudioException("File audio ini sudah tidak ada di ponsel. Impor ulang file tersebut.")
        SampleAudioSelection(entry.track.title, SampleAudioSource.Local, uri, entry.id)
    }
    else -> localAudioSelection(entry.track, entry.id)
}

@OptIn(ExperimentalResourceApi::class)
private fun localAudioSelection(track: Track, queueEntryId: Long? = null): SampleAudioSelection {
    val path = checkNotNull(DemoCatalog.localAudioPath(track.id))
    return SampleAudioSelection(track.title, SampleAudioSource.Local, Res.getUri(path), queueEntryId)
}

/** Satu controller untuk seluruh proses aplikasi; dibuat oleh titik masuk Android atau iOS. */
internal fun createPlaybackController(
    output: SampleAudioOutput,
    queueStore: QueueStore,
    localFiles: LocalAudioFiles,
    context: PlatformContext,
): PlaybackController {
    val store = appearanceStore(context)
    val metered = meteredNetwork(context)
    val saveData: suspend () -> Boolean = {
        withContext(Dispatchers.Default) { dataSaverActive(store.load().saveMobileData, metered.value) }
    }
    return PlaybackController(output, MainScope()) { entry -> queueAudioSelection(entry, saveData, localFiles) }
        .apply { persistQueue(queueStore) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackProgress(state: SampleAudioState, onSeek: (Long) -> Unit) {
    val duration = state.durationMs
    if (duration == null) {
        if (state.status == SampleAudioStatus.Paused && state.selection?.isPending == true && state.positionMs > 0) {
            Text(
                playbackTime(state.positionMs),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "Posisi jeda ${accessibleDuration(state.positionMs)}"
                },
            )
        }
        return
    }
    var scrubFraction by remember(state.selection, state.canSeek) { mutableStateOf<Float?>(null) }
    val progress = scrubFraction ?: (state.positionMs.toDouble() / duration).toFloat()
    val shownPosition = scrubFraction?.let { (it * duration.toDouble()).roundToLong() } ?: state.positionMs
    val spokenProgress = "${accessibleDuration(shownPosition)} dari ${accessibleDuration(duration)}"

    Slider(
        value = progress,
        onValueChange = { scrubFraction = it },
        onValueChangeFinished = {
            scrubFraction?.let { onSeek((it * duration.toDouble()).roundToLong()) }
            scrubFraction = null
        },
        enabled = state.canSeek,
        track = { slider ->
            SliderDefaults.Track(sliderState = slider, enabled = state.canSeek, modifier = Modifier.height(4.dp))
        },
        modifier = Modifier.fillMaxWidth().excludeSystemGestures().semantics {
            contentDescription = "Posisi pemutaran"
            stateDescription = spokenProgress
        },
    )
    Text(
        "${playbackTime(shownPosition)} / ${playbackTime(duration)}",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.clearAndSetSemantics { contentDescription = spokenProgress },
    )
}

internal fun playbackTime(positionMs: Long): String {
    val seconds = positionMs / 1_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
