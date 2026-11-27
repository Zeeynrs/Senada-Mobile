package senada.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
