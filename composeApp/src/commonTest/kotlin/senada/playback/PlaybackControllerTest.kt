package senada.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import senada.model.Track

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerTest {
    private val output = FakeAudioOutput()
    private val first = Track("demo:track:hujan", "Selepas Hujan", listOf("Senada"), source = "demo")
    private val second = Track("demo:track:pagi", "Sebelum Pagi", listOf("Senada"), source = "demo")

    private fun TestScope.controller() = PlaybackController(output, backgroundScope) { entry ->
        SampleAudioSelection(entry.track.title, SampleAudioSource.Local, "file:///${entry.id}.m4a", entry.id)
    }

    private val PlaybackController.selectedEntryId get() = state.value.audio.selection?.queueEntryId

    @Test
    fun hidingTheUiKeepsAudioPlaying() = runTest {
        val playback = controller()
        playback.add(first)
        playback.setUiVisible(true)
        playback.playEntry(playback.state.value.queue.entries.single().id)

        playback.setUiVisible(false)
        advanceTimeBy(5_000)

        assertEquals(0, output.pauseCount)
        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)
    }

    @Test
    fun endEventAdvancesToTheNextEntryWhileTheUiIsHidden() = runTest {
        val playback = controller()
        playback.add(first)
        playback.add(second)
        val (firstEntry, secondEntry) = playback.state.value.queue.entries
        playback.playEntry(firstEntry.id)

        output.status = SampleAudioStatus.Ended
        output.emitEvent()
        runCurrent()

        assertEquals(secondEntry.id, playback.selectedEntryId)
        assertEquals(listOf(true, true), output.restarts)
    }

    @Test
    fun positionIsPolledOnlyWhileTheUiIsVisible() = runTest {
        val playback = controller()
        playback.add(first)
        playback.playEntry(playback.state.value.queue.entries.single().id)
        playback.setUiVisible(true)

        output.positionMs = 5_000
        advanceTimeBy(300)
        assertEquals(5_000L, playback.state.value.audio.positionMs)

        playback.setUiVisible(false)
        output.positionMs = 8_000
        advanceTimeBy(1_000)
        assertEquals(5_000L, playback.state.value.audio.positionMs)
    }

    @Test
    fun previousRestartsTheCurrentEntryAfterThreeSeconds() = runTest {
        val playback = controller()
        output.durationMs = 12_000
        playback.add(first)
        playback.add(second)
        val secondEntry = playback.state.value.queue.entries.last()
        playback.playEntry(secondEntry.id)
        output.positionMs = 4_000

        playback.previous()

        assertEquals(secondEntry.id, playback.selectedEntryId)
        assertEquals(listOf(0L), output.seeks)
    }

    @Test
    fun previousNearTheStartMovesToTheEarlierEntry() = runTest {
        val playback = controller()
        output.durationMs = 12_000
        playback.add(first)
        playback.add(second)
        val (firstEntry, secondEntry) = playback.state.value.queue.entries
        playback.playEntry(secondEntry.id)
        output.positionMs = 1_000

        playback.previous()

        assertEquals(firstEntry.id, playback.selectedEntryId)
        assertEquals(emptyList(), output.seeks)
    }

    @Test
    fun nextAtTheEndOfTheQueueKeepsTheCurrentEntry() = runTest {
        val playback = controller()
        playback.add(first)
        val entry = playback.state.value.queue.entries.single()
        playback.playEntry(entry.id)

        playback.next()

        assertEquals(entry.id, playback.selectedEntryId)
        assertEquals(listOf(true), output.restarts)
    }

    @Test
    fun resumeWithoutSelectionStartsTheFirstEntry() = runTest {
        val playback = controller()
        playback.add(first)
        playback.add(second)

        playback.resume()

        assertEquals(playback.state.value.queue.entries.first().id, playback.selectedEntryId)
    }

    @Test
    fun interruptionResumesOnlyWhenAudioWasPlaying() = runTest {
        val playback = controller()
        playback.add(first)
        playback.playEntry(playback.state.value.queue.entries.single().id)

        output.status = SampleAudioStatus.Paused
        playback.onInterruptionBegan()
        assertEquals(SampleAudioStatus.Paused, playback.state.value.audio.status)

        playback.onInterruptionEnded(shouldResume = true)
        assertEquals(listOf(true, false), output.restarts)
        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)
    }

    @Test
    fun interruptionAfterAManualPauseStaysPaused() = runTest {
        val playback = controller()
        playback.add(first)
        playback.playEntry(playback.state.value.queue.entries.single().id)
        playback.pause()

        playback.onInterruptionBegan()
        playback.onInterruptionEnded(shouldResume = true)

        assertEquals(listOf(true), output.restarts)
        assertEquals(SampleAudioStatus.Paused, playback.state.value.audio.status)
    }

    @Test
    fun replayingAFinishedEntryIsNotSkippedByItsOwnPlayerEvent() = runTest {
        val playback = controller()
        playback.add(first)
        val firstEntry = playback.state.value.queue.entries.single()
        playback.playEntry(firstEntry.id)
        output.status = SampleAudioStatus.Ended
        output.emitEvent()
        runCurrent()
        playback.add(second)

        output.emitEventOnPlay = true
        playback.playEntry(firstEntry.id)
        runCurrent()

        assertEquals(firstEntry.id, playback.selectedEntryId)
        assertEquals(listOf(true, true), output.restarts)
    }

    @Test
    fun listenersHearStatusChangesAndSeeksButNotPositionTicks() = runTest {
        val playback = controller()
        val heard = mutableListOf<SampleAudioStatus>()
        playback.addListener { heard += it.audio.status }
        output.durationMs = 12_000
        playback.add(first)
        playback.setUiVisible(true)
        playback.playEntry(playback.state.value.queue.entries.single().id)
        val afterPlay = heard.size

        output.positionMs = 2_000
        advanceTimeBy(1_000)
        assertEquals(afterPlay, heard.size)

        playback.seekTo(6_000)
        assertEquals(afterPlay + 1, heard.size)
    }

    @Test
    fun removingThePlayingEntryClearsTheSelection() = runTest {
        val playback = controller()
        playback.add(first)
        val entry = playback.state.value.queue.entries.single()
        playback.playEntry(entry.id)

        playback.remove(entry.id)

        assertNull(playback.state.value.audio.selection)
        assertEquals(emptyList(), playback.state.value.queue.entries)
    }
}
