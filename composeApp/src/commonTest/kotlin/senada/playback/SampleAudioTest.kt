package senada.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SampleAudioTest {
    private val output = RecordingAudioOutput()
    private val player = SampleAudioPlayer(output)

    @Test
    fun playingTheSameSourceAfterPauseResumesTheExistingItem() {
        player.play(SampleAudioSource.Local)
        player.pause()
        player.play(SampleAudioSource.Local)

        assertEquals(listOf(true, false), output.restarts)
        assertEquals(1, output.pauseCount)
    }

    @Test
    fun selectingAnotherSourceLoadsItFromTheBeginning() {
        player.play(SampleAudioSource.Local)
        player.play(SampleAudioSource.Https)

        assertEquals(listOf(true, true), output.restarts)
        assertEquals(SampleAudioSource.Https, player.state.source)
    }

    @Test
    fun playbackFailureClearsActiveStateAndCanBeRetried() {
        player.play(SampleAudioSource.Https)
        output.status = SampleAudioStatus.Failed
        player.refresh()

        assertEquals(SampleAudioStatus.Failed, player.state.status)
        assertFalse(player.state.isActive)

        player.play(SampleAudioSource.Https)
        assertEquals(listOf(true, true), output.restarts)
    }

    @Test
    fun finishedAudioCanBePlayedAgainFromTheBeginning() {
        player.play(SampleAudioSource.Local)
        output.status = SampleAudioStatus.Ended
        player.refresh()

        assertEquals(SampleAudioStatus.Ended, player.state.status)
        player.play(SampleAudioSource.Local)
        assertEquals(listOf(true, true), output.restarts)
    }

    private class RecordingAudioOutput : SampleAudioOutput {
        val restarts = mutableListOf<Boolean>()
        var pauseCount = 0
        override var status = SampleAudioStatus.Playing

        override fun play(source: SampleAudioSource, restart: Boolean) {
            restarts += restart
        }

        override fun pause() {
            pauseCount++
        }

        override fun release() = Unit
    }
}
