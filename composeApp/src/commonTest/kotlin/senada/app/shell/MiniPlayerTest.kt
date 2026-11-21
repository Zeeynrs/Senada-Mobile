package senada.app.shell

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import senada.model.Track
import senada.playback.PlaybackState
import senada.playback.Queue
import senada.playback.SampleAudioSelection
import senada.playback.SampleAudioSource
import senada.playback.SampleAudioState
import senada.playback.SampleAudioStatus

class MiniPlayerTest {
    private val song = Track("yt:track:a", "Lagu", listOf("Artis"), source = "ytmusic")

    @Test
    fun hiddenWithEmptyQueueAndNothingSelected() {
        assertFalse(showsMiniPlayer(PlaybackState()))
    }

    @Test
    fun shownWhenQueueHasSongsButNothingIsSelected() {
        assertTrue(showsMiniPlayer(PlaybackState(queue = Queue().append(song))))
    }

    @Test
    fun shownForTheSampleButtons() {
        val sample = SampleAudioSelection("Selepas Hujan", SampleAudioSource.Local, "file:///contoh.m4a")
        assertTrue(showsMiniPlayer(PlaybackState(audio = SampleAudioState(sample, SampleAudioStatus.Playing))))
    }
}
