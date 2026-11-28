package senada.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import senada.playback.SampleAudioSelection
import senada.playback.SampleAudioSource
import senada.playback.SampleAudioState
import senada.playback.SampleAudioStatus

class LyricsFrameClockTest {
    @Test
    fun anchorsAcrossFramesPauseSpeedChangesAndBackwardSeek() {
        val clock = LyricsPositionFilter()
        val playing = SampleAudioState(
            selection = SampleAudioSelection("Song", SampleAudioSource.Local, "file:///song.m4a", 1L),
            status = SampleAudioStatus.Playing,
            positionMs = 500L,
            sampledAtMs = 100L,
        )

        assertEquals(516L, clock.resolve(playing, 116L))
        assertEquals(532L, clock.resolve(playing, 132L))
        assertEquals(532L, clock.resolve(playing.copy(positionMs = 520L, sampledAtMs = 132L), 132L))
        assertEquals(532L, clock.resolve(playing.copy(positionMs = 480L, sampledAtMs = 132L), 132L))

        val paused = playing.copy(status = SampleAudioStatus.Paused, positionMs = 545L, sampledAtMs = 145L)
        assertEquals(545L, clock.resolve(paused, 145L))
        assertEquals(545L, clock.resolve(paused, 500L))

        val resumed = paused.copy(status = SampleAudioStatus.Playing, playbackSpeed = 2f, sampledAtMs = 500L)
        assertEquals(577L, clock.resolve(resumed, 516L))
        assertEquals(593L, clock.resolve(resumed.copy(positionMs = 577L, sampledAtMs = 516L, playbackSpeed = 1f), 532L))

        val seek = resumed.copy(positionMs = 570L, sampledAtMs = 532L, discontinuityVersion = 1L)
        assertEquals(570L, clock.resolve(seek, 532L))
        val next = seek.copy(selection = seek.selection?.copy(queueEntryId = 2L), positionMs = 0L)
        assertEquals(0L, clock.resolve(next, 532L))
    }

    @Test
    fun forwardReportJitterIsEasedInsteadOfJumped() {
        val clock = LyricsPositionFilter()
        clock.resolve(playing(positionMs = 1_000L, sampledAtMs = 0L), 0L)
        assertEquals(1_016L, clock.resolve(playing(positionMs = 1_000L, sampledAtMs = 0L), 16L))

        // The player re-anchors 40 ms ahead of the extrapolation. The old filter jumped straight there.
        val ahead = playing(positionMs = 1_072L, sampledAtMs = 32L)
        val step = clock.resolve(ahead, 32L) - 1_016L
        assertTrue(step in 17L..35L, "one frame moved $step ms")

        var shown = 0L
        for (frame in 3..20) shown = clock.resolve(ahead, frame * 16L)
        assertEquals(ahead.positionAt(320L), shown)
    }

    @Test
    fun backwardReportJitterSlowsDownWithoutRunningBackward() {
        val clock = LyricsPositionFilter()
        clock.resolve(playing(positionMs = 1_000L, sampledAtMs = 0L), 0L)
        clock.resolve(playing(positionMs = 1_000L, sampledAtMs = 0L), 16L)

        val behind = playing(positionMs = 990L, sampledAtMs = 32L)
        var previous = 1_016L
        for (frame in 2..30) {
            val shown = clock.resolve(behind, frame * 16L)
            assertTrue(shown >= previous, "went back from $previous to $shown")
            previous = shown
        }
        assertEquals(behind.positionAt(480L), previous)
    }

    @Test
    fun largeForwardGapSnapsAndStaleFramesRestart() {
        val clock = LyricsPositionFilter()
        clock.resolve(playing(positionMs = 1_000L, sampledAtMs = 0L), 0L)
        assertEquals(1_300L, clock.resolve(playing(positionMs = 1_300L, sampledAtMs = 16L), 16L))

        // Nothing read the clock for a while (screen hidden): start from the player again.
        assertEquals(4_000L, clock.resolve(playing(positionMs = 4_000L, sampledAtMs = 2_000L), 2_000L))
    }

    private fun playing(positionMs: Long, sampledAtMs: Long) = SampleAudioState(
        selection = SampleAudioSelection("Song", SampleAudioSource.Local, "file:///song.m4a", 1L),
        status = SampleAudioStatus.Playing,
        positionMs = positionMs,
        sampledAtMs = sampledAtMs,
    )
}
