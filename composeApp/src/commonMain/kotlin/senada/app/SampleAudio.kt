package senada.app

import coil3.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import senada.audio.AudioEffectsController
import senada.audio.audioEffectsStore
import senada.catalog.DemoCatalog
import senada.catalog.youtube.YOUTUBE_MUSIC_SOURCE
import senada.local.LOCAL_FILE_SOURCE
import senada.local.LocalAudioException
import senada.local.LocalAudioFiles
import senada.local.localFileNameOf
import senada.model.Track
import senada.playback.LoudnessMatchingOutput
import senada.playback.PlaybackController
import senada.playback.QueueItem
import senada.playback.QueueStore
import senada.playback.SampleAudioOutput
import senada.playback.SampleAudioSelection
import senada.playback.SampleAudioSource
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
            loudnessDb = audio.loudnessDb,
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
internal fun createPlayback(
    output: SampleAudioOutput,
    queueStore: QueueStore,
    localFiles: LocalAudioFiles,
    context: PlatformContext,
    audioEffects: AudioEffectsController = AudioEffectsController(audioEffectsStore(context)),
): PlaybackComponents {
    val store = appearanceStore(context)
    val metered = meteredNetwork(context)
    val saveData: suspend () -> Boolean = {
        withContext(Dispatchers.Default) { dataSaverActive(store.load().saveMobileData, metered.value) }
    }
    val scope = MainScope()
    val normalizedOutput = LoudnessMatchingOutput(output, audioEffects.effects, scope)
    val controller = PlaybackController(normalizedOutput, scope) { entry -> queueAudioSelection(entry, saveData, localFiles) }
        .apply { persistQueue(queueStore) }
    return PlaybackComponents(controller, audioEffects)
}

internal class PlaybackComponents(val controller: PlaybackController, val audioEffects: AudioEffectsController)

internal fun playbackTime(positionMs: Long): String {
    val seconds = positionMs / 1_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
