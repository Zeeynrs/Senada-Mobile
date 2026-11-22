package senada.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal val sleepTimerMinutes = listOf(15, 30, 45, 60, 90, 120)

internal sealed interface SleepTimerState {
    data object Off : SleepTimerState
    data object EndOfTrack : SleepTimerState
    data class CountingDown(val minutesRemaining: Int) : SleepTimerState
}

/** Milik controller sepanjang proses aplikasi, termasuk saat layar pemain ditutup. */
internal class SleepTimer(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val output: SampleAudioOutput,
    private val pausePlayback: () -> Unit,
) {
    private val mutableState = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state = mutableState.asStateFlow()
    private var deadlineMs: Long? = null
    private var countdown: Job? = null
    private var originalVolume: Float? = null

    fun start(minutes: Int) {
        require(minutes in sleepTimerMinutes)
        clear(SleepTimerState.CountingDown(minutes))
        deadlineMs = now() + minutes * MINUTE_MS
        countdown = scope.launch {
            while (true) {
                val remaining = (deadlineMs ?: return@launch) - now()
                if (remaining <= 0) {
                    finishTimedStop()
                    return@launch
                }
                val minutesLeft = ((remaining - 1) / MINUTE_MS + 1).toInt()
                mutableState.value = SleepTimerState.CountingDown(minutesLeft)
                val untilNextMinute = remaining - (minutesLeft - 1) * MINUTE_MS
                val untilFadeTick = if (remaining <= FADE_MS) {
                    fade(remaining)
                    minOf(FADE_STEP_MS, remaining)
                } else {
                    remaining - FADE_MS
                }
                delay(minOf(untilNextMinute, untilFadeTick))
            }
        }
    }

    fun stopAfterTrack() = clear(SleepTimerState.EndOfTrack)

    fun cancel() = clear(SleepTimerState.Off)

    fun consumeTrackEnd(): Boolean {
        if (state.value != SleepTimerState.EndOfTrack) return false
        cancel()
        return true
    }

    /** Mengejar tenggat bila proses sempat ditangguhkan saat audio sudah senyap. */
    fun expireIfDue(): Boolean {
        val deadline = deadlineMs ?: return false
        if (now() < deadline) return false
        finishTimedStop()
        return true
    }

    private fun fade(remainingMs: Long) {
        if (output.status != SampleAudioStatus.Playing) return
        val volume = originalVolume ?: output.volume.also { originalVolume = it }
        output.volume = volume * (remainingMs.toFloat() / FADE_MS)
    }

    private fun finishTimedStop() {
        // Jeda sebelum mengembalikan volume agar ujung fade tetap senyap.
        if (originalVolume != null) output.volume = 0f
        deadlineMs = null
        pausePlayback()
        cancel()
    }

    private fun clear(nextState: SleepTimerState) {
        countdown?.cancel()
        countdown = null
        deadlineMs = null
        originalVolume?.let { output.volume = it }
        originalVolume = null
        mutableState.value = nextState
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val FADE_MS = 8_000L
        const val FADE_STEP_MS = 250L
    }
}
