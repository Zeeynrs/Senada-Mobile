package senada.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import senada.model.Track

class SampleAudioTest {
    private val output = RecordingAudioOutput()
    private val player = SampleAudioPlayer(output)
    private val local = SampleAudioSelection("Sample", SampleAudioSource.Local, "file:///sample.m4a")
    private val https = SampleAudioSelection("Sample", SampleAudioSource.Https, "https://example.org/sample.m4a")

    @Test
    fun playingTheSameSourceAfterPauseResumesTheExistingItem() {
        player.play(local)
        player.pause()
        player.play(local)

        assertEquals(listOf(true, false), output.restarts)
        assertEquals(1, output.pauseCount)
    }

    @Test
    fun selectingAnotherSourceLoadsItFromTheBeginning() {
        player.play(local)
        player.play(https)

        assertEquals(listOf(true, true), output.restarts)
        assertEquals(https, player.state.selection)
    }

    @Test
    fun playbackFailureClearsActiveStateAndCanBeRetried() {
        player.play(https)
        output.status = SampleAudioStatus.Failed
        player.refresh()

        assertEquals(SampleAudioStatus.Failed, player.state.status)
        assertFalse(player.state.isActive)

        player.play(https)
        assertEquals(listOf(true, true), output.restarts)
    }

    @Test
    fun finishedAudioCanBePlayedAgainFromTheBeginning() {
        player.play(local)
        output.status = SampleAudioStatus.Ended
        player.refresh()

        assertEquals(SampleAudioStatus.Ended, player.state.status)
        player.play(local)
        assertEquals(listOf(true, true), output.restarts)
    }

    @Test
    fun reachingTheEndStartsTheNextDuplicateFromTheBeginning() {
        val track = Track("demo:track:test", "Sample", listOf("Senada"), source = "demo")
        val queue = Queue().append(track).append(track)
        val (first, second) = queue.entries
        player.play(local.copy(queueEntryId = first.id))
        output.status = SampleAudioStatus.Ended
        player.refresh()

        player.advanceAfterEnd(queue) { entry ->
            local.copy(queueEntryId = entry.id)
        }

        assertEquals(second.id, player.state.selection?.queueEntryId)
        assertEquals(listOf(true, true), output.restarts)
    }

    @Test
    fun choosingAnotherOccurrenceOfThePlayingTrackStartsItFromTheBeginning() {
        player.play(local.copy(queueEntryId = 1))
        player.play(local.copy(queueEntryId = 2))

        assertEquals(2L, player.state.selection?.queueEntryId)
        assertEquals(listOf(true, true), output.restarts)
    }

    @Test
    fun removingTheSelectedQueueEntryStopsThePlayer() {
        player.play(local.copy(queueEntryId = 1))

        player.onQueueEntryRemoved(1)

        assertEquals(SampleAudioState(), player.state)
        assertEquals(1, output.pauseCount)
    }

    @Test
    fun refreshingActivePlaybackReadsPositionAndDuration() {
        player.play(local)
        output.positionMs = 4_200
        output.durationMs = 12_000

        player.refresh()

        assertEquals(4_200L, player.state.positionMs)
        assertEquals(12_000L, player.state.durationMs)
    }

    @Test
    fun seekingWhilePausedKeepsPlaybackPausedAndClampsThePosition() {
        output.durationMs = 12_000
        player.play(local)
        player.pause()

        player.seekTo(-100)
        player.seekTo(15_000)

        assertEquals(listOf(0L, 12_000L), output.seeks)
        assertEquals(12_000L, player.state.positionMs)
        assertEquals(SampleAudioStatus.Paused, player.state.status)
        assertEquals(listOf(true), output.restarts)
    }

    @Test
    fun seekingWaitsUntilTheAudioDurationIsKnown() {
        player.play(local)

        player.seekTo(1_000)

        assertFalse(player.state.canSeek)
        assertEquals(emptyList(), output.seeks)
    }

    private class RecordingAudioOutput : SampleAudioOutput {
        val restarts = mutableListOf<Boolean>()
        val seeks = mutableListOf<Long>()
        var pauseCount = 0
        override var status = SampleAudioStatus.Playing
        override var positionMs = 0L
        override var durationMs: Long? = null

        override fun play(uri: String, restart: Boolean) {
            restarts += restart
            status = SampleAudioStatus.Playing
        }

        override fun pause() {
            pauseCount++
        }

        override fun seekTo(positionMs: Long) {
            seeks += positionMs
            this.positionMs = positionMs
        }

        override fun release() = Unit
    }
}
