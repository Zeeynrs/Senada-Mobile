package senada.app

import coil3.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import senada.audio.AudioEffectsController
import senada.audio.audioEffectsStore
import senada.catalog.youtube.YOUTUBE_MUSIC_SOURCE
import senada.local.LOCAL_FILE_SOURCE
import senada.local.LocalAudioException
import senada.local.LocalAudioFiles
import senada.local.localFileNameOf
import senada.offline.canDownloadOffline
import senada.playback.LoudnessMatchingOutput
import senada.playback.PlaybackController
import senada.playback.QueueItem
import senada.playback.QueueStore
import senada.playback.SampleAudioOutput
import senada.playback.SampleAudioSelection
import senada.playback.SampleAudioSource

/**
 * Alamat audio entri antrean: stream YouTube Music yang dicari saat diputar, file audio yang diimpor
 * pengguna, atau file contoh di build debug.
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
    else -> demoAudioSelection(entry.track, entry.id)
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
    val offline = offlineDownloads(context)
    val controller = PlaybackController(
        normalizedOutput, scope,
        isDownloaded = { track -> offline.state.value.song(track.id) != null },
        offlineSelectionFor = { entry ->
            val snapshot = offline.state.value
            val song = if (canDownloadOffline(entry.track) && (!snapshot.ready || snapshot.song(entry.track.id) != null)) {
                offline.song(entry.track)
            } else null
            song?.let {
                SampleAudioSelection(
                    entry.track.title, SampleAudioSource.Offline, it.audioUri, entry.id,
                    durationMs = it.durationMs ?: entry.track.durationMs, loudnessDb = it.loudnessDb,
                )
            }
        },
    ) { entry -> queueAudioSelection(entry, saveData, localFiles) }
        .apply { persistQueue(queueStore) }
    return PlaybackComponents(controller, audioEffects)
}

internal class PlaybackComponents(val controller: PlaybackController, val audioEffects: AudioEffectsController)

internal fun playbackTime(positionMs: Long): String {
    val seconds = positionMs / 1_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
