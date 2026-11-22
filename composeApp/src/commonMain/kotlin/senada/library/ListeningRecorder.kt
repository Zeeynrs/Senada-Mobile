package senada.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import senada.epochMillis
import senada.playback.PlaybackController
import senada.playback.SampleAudioStatus

/** Mengamati pemutar selama proses hidup, termasuk saat pembaruan posisi untuk layar dihentikan. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun PlaybackController.recordListeningTo(library: LibraryRepository, scope: CoroutineScope, position: () -> Long) {
    val tracker = ListeningTracker()
    scope.launch {
        state.transformLatest { state ->
            val audio = state.audio
            if (audio.status != SampleAudioStatus.Playing || state.currentEntry == null || audio.selection?.isPending != false) {
                emit(tracker.sample(state, epochMillis()))
            } else {
                // Posisi native tetap tersedia di latar belakang; status dan identitas berasal dari controller.
                while (true) {
                    val point = position().coerceIn(0L, audio.durationMs ?: Long.MAX_VALUE)
                    emit(tracker.sample(state.copy(audio = audio.copy(positionMs = point)), epochMillis()))
                    delay(1_000L)
                }
            }
        }.collect { events ->
            for (event in events) {
                try {
                    library.recordListening(event)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Gagal menyimpan statistik tidak mengganggu pemutaran.
                }
            }
        }
    }
}
