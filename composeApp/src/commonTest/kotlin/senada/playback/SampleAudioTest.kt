package senada.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SampleAudioTest {
    private val output = FakeAudioOutput()
    private val player = SampleAudioPlayer(output)
    private val local = SampleAudioSelection("Sample", SampleAudioSource.Local, "file:///sample.m4a")
    private val https = SampleAudioSelection("Sample", SampleAudioSource.Https, "https://example.org/sample.m4a")

    private val stream = SampleAudioSelection(
        "Stream", SampleAudioSource.YouTubeMusic, "https://example.org/stream.m4a", durationMs = 213_089,
    )

    @Test
    fun streamDurationIsPassedToThePlayerAsTheRealEnd() {
        player.play(stream)
        player.play(local)

        assertEquals(listOf<Long?>(213_089, null), output.endPositions)
    }

    @Test
    fun theSourcesDurationWinsOverALongerGuessFromThePlayer() {
        player.play(stream)
        output.durationMs = 260_000
        output.positionMs = 10_000
        player.refresh()

        assertEquals(213_089L, player.state.durationMs)
    }

    @Test
    fun aShorterDurationFromThePlayerIsKept() {
        player.play(stream)
        output.durationMs = 213_000
        player.refresh()

        assertEquals(213_000L, player.state.durationMs)
    }

    @Test
    fun playingSilentlyPastTheRealEndFinishesTheTrack() {
        player.play(stream)
        output.durationMs = 260_000
        output.positionMs = 213_089 + END_GRACE_MS - 1
        player.refresh()
        assertEquals(SampleAudioStatus.Playing, player.state.status)

        output.positionMs = 213_089 + END_GRACE_MS
        player.refresh()

        assertEquals(SampleAudioStatus.Ended, player.state.status)
        assertEquals(213_089L, player.state.positionMs)
        assertEquals(1, output.pauseCount)
    }

    @Test
    fun audioWithoutAKnownDurationIsNeverCutShort() {
        player.play(https)
        output.durationMs = 30_000
        output.positionMs = 29_000
        player.refresh()

        assertEquals(SampleAudioStatus.Playing, player.state.status)
        assertEquals(30_000L, player.state.durationMs)
    }

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
}
