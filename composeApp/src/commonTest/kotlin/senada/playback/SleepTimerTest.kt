package senada.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import senada.model.Track

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {
    private val output = FakeAudioOutput()

    private fun TestScope.playback(vararg titles: String): PlaybackController {
        val playback = PlaybackController(output, backgroundScope, now = { testScheduler.currentTime }) { entry ->
            SampleAudioSelection(entry.track.title, SampleAudioSource.Local, "file:///${entry.id}.m4a", entry.id)
        }
        titles.forEach { playback.add(Track("demo:track:$it", it, emptyList(), source = "demo")) }
        playback.resume()
        return playback
    }

    private fun TestScope.elapse(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun timedStopFadesAndPausesAtTheDeadlineWithoutAVisibleScreen() = runTest {
        val playback = playback("Pertama")
        output.volume = 0.8f
        playback.startSleepTimer(15)
        elapse(60_000)
        assertEquals(SleepTimerState.CountingDown(14), playback.sleepTimerState.value)

        elapse(831_999)
        assertEquals(0.8f, output.volume)
        elapse(4_001)
        assertEquals(0.4f, output.volume, absoluteTolerance = 0.001f)

        elapse(3_999)
        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)
        elapse(1)
        assertEquals(SampleAudioStatus.Paused, playback.state.value.audio.status)
        assertTrue(output.volumesAtPause.single() < 0.04f)
        assertEquals(0.8f, output.volume)
        assertEquals(SleepTimerState.Off, playback.sleepTimerState.value)
        playback.resume()
        assertEquals(0.8f, output.volume)
        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)
    }

    @Test
    fun cancellingDuringTheFadeRestoresVolumeAndNeverPauses() = runTest {
        val playback = playback("Pertama")
        output.volume = 0.6f
        playback.startSleepTimer(15)
        elapse(896_000)
        assertTrue(output.volume < 0.6f)

        playback.cancelSleepTimer()
        elapse(5_000)

        assertEquals(0.6f, output.volume)
        assertEquals(0, output.pauseCount)
        assertEquals(SleepTimerState.Off, playback.sleepTimerState.value)
    }

    @Test
    fun replacingTheTimerDiscardsItsOldDeadlineWhileItIsFading() = runTest {
        val playback = playback("Pertama")
        playback.startSleepTimer(15)
        elapse(896_000)

        playback.startSleepTimer(30)
        assertEquals(1f, output.volume)
        elapse(4_000)
        assertEquals(0, output.pauseCount)
        elapse(1_796_000)

        assertEquals(1, output.pauseCount)
        assertEquals(SleepTimerState.Off, playback.sleepTimerState.value)
    }

    @Test
    fun endOfTrackFollowsTheManualSkipAndOverridesAutomaticRepeat() = runTest {
        val playback = playback("Pertama", "Kedua", "Ketiga")
        playback.setRepeatMode(RepeatMode.One)
        playback.stopAfterCurrentTrack()
        assertEquals(SleepTimerState.EndOfTrack, playback.sleepTimerState.value)
        playback.next()
        runCurrent()
        assertEquals("Kedua", playback.state.value.currentEntry?.track?.title)

        output.status = SampleAudioStatus.Ended
        output.emitEvent()
        runCurrent()

        assertEquals("Kedua", playback.state.value.currentEntry?.track?.title)
        assertEquals(SampleAudioStatus.Paused, playback.state.value.audio.status)
        assertEquals(SleepTimerState.Off, playback.sleepTimerState.value)
        output.emitEvent()
        runCurrent()
        assertEquals("Kedua", playback.state.value.currentEntry?.track?.title)

        playback.resume()
        runCurrent()
        assertEquals("Ketiga", playback.state.value.currentEntry?.track?.title)
        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)
    }

    @Test
    fun cancellingEndOfTrackAllowsAutomaticAdvance() = runTest {
        val playback = playback("Pertama", "Kedua")
        playback.stopAfterCurrentTrack()
        playback.cancelSleepTimer()

        output.status = SampleAudioStatus.Ended
        output.emitEvent()
        runCurrent()

        assertEquals("Kedua", playback.state.value.currentEntry?.track?.title)
        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)
    }
}
