package senada.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import senada.playback.PlaybackController
import senada.playback.SampleAudioSelection
import senada.playback.SampleAudioStatus

/** Controller yang riwayatnya sudah dicatat; titik masuk platform bisa memanggil ulang tanpa menggandakan listener. */
private var recordingController: PlaybackController? = null

/**
 * Mencatat lagu antrean ke riwayat saat mulai berbunyi. Melanjutkan lagu yang dijeda tidak dicatat ulang;
 * memutar entri lain, atau entri yang sama dari awal lagi, dicatat sebagai pemutaran baru.
 */
internal fun PlaybackController.recordHistoryTo(library: LibraryRepository, scope: CoroutineScope) {
    if (recordingController === this) return
    recordingController = this

    var recorded: SampleAudioSelection? = null
    addListener { state ->
        val selection = state.audio.selection
        val track = state.currentEntry?.track
        if (state.audio.status != SampleAudioStatus.Playing || selection == null || track == null) return@addListener
        if (selection === recorded) return@addListener
        recorded = selection
        scope.launch {
            // Riwayat bukan hal penting; kegagalan database tidak boleh mengganggu pemutaran.
            runCatching { library.recordPlay(track) }
        }
    }
}
