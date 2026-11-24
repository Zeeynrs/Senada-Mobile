package senada.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import senada.audio.AudioEffects

@OptIn(ExperimentalCoroutinesApi::class)
class LoudnessMatchingOutputTest {
    private val stream = SampleAudioSelection(
        "Music", SampleAudioSource.YouTubeMusic, "https://example.invalid/music.m4a",
        loudnessDb = 6f,
    )

    @Test
    fun togglingWhilePlayingUsesTheCurrentTrackAndKeepsTheSleepFadeVolume() = runTest {
        val device = FakeAudioOutput()
        val effects = MutableStateFlow(AudioEffects())
        val output = LoudnessMatchingOutput(device, effects, backgroundScope)
        val player = SampleAudioPlayer(output)

        player.play(stream)
        assertEquals(1f, device.volume)
        output.volume = 0.8f
        effects.value = AudioEffects(loudnessMatch = true)
        runCurrent()
        assertEquals(0.40095f, device.volume, absoluteTolerance = 0.00001f)

        output.volume = 0.4f
        assertEquals(0.4f, output.volume)
        assertEquals(0.20047f, device.volume, absoluteTolerance = 0.00001f)

        effects.value = AudioEffects()
        runCurrent()
        assertEquals(0.4f, device.volume)
        assertEquals(listOf(true), device.restarts)
    }

    @Test
    fun replacementAppliesOnlyItsOwnGainBeforePlaybackStarts() = runTest {
        val device = FakeAudioOutput()
        val volumesAtStart = mutableListOf<Float>()
        device.emitEventOnPlay = true
        device.onEvent = { volumesAtStart += device.volume }
        val effects = MutableStateFlow(AudioEffects(loudnessMatch = true))
        val player = SampleAudioPlayer(LoudnessMatchingOutput(device, effects, backgroundScope))

        player.play(stream)
        player.prepare(SampleAudioSelection.pending("Music", 1))
        assertEquals(1f, device.volume)
        player.play(stream.copy(uri = "https://example.invalid/retry.m4a", loudnessDb = 12f), forceRestart = true)
        player.play(stream.copy(loudnessDb = null))
        player.play(stream.copy(source = SampleAudioSource.Https))
        player.play(stream.copy(source = SampleAudioSource.Local))
        player.play(stream.copy(loudnessDb = -3.7f))
        player.play(stream.copy(loudnessDb = Float.POSITIVE_INFINITY))

        assertEquals(0.50119f, volumesAtStart[0], absoluteTolerance = 0.00001f)
        assertEquals(0.25119f, volumesAtStart[1], absoluteTolerance = 0.00001f)
        assertEquals(List(5) { 1f }, volumesAtStart.drop(2))
    }

    @Test
    fun changingTheToggleBeforeStartingAnotherTrackUsesTheLatestSetting() = runTest {
        val device = FakeAudioOutput()
        val effects = MutableStateFlow(AudioEffects(loudnessMatch = true))
        val player = SampleAudioPlayer(LoudnessMatchingOutput(device, effects, backgroundScope))

        player.play(stream)
        effects.value = AudioEffects()
        player.play(stream.copy(uri = "https://example.invalid/new.m4a"))
        assertEquals(1f, device.volume)
    }
}
