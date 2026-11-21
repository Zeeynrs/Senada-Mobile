package senada.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import senada.catalog.CatalogException
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

    private fun selection(entry: QueueItem) =
        SampleAudioSelection(entry.track.title, SampleAudioSource.YouTubeMusic, "https://audio/${entry.id}", entry.id)

    /** Alamat audio tiap entri baru tersedia saat tes menyelesaikannya. */
    private val addresses = mutableMapOf<Long, CompletableDeferred<SampleAudioSelection>>()

    private fun TestScope.waitingController() = PlaybackController(output, backgroundScope) { entry ->
        addresses.getOrPut(entry.id) { CompletableDeferred() }.await()
    }

    private fun address(entry: QueueItem) = addresses.getOrPut(entry.id) { CompletableDeferred() }

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

    @Test
    fun waitingForAnAddressShowsLoadingThenPlays() = runTest {
        val playback = waitingController()
        playback.add(first)
        val entry = playback.state.value.queue.entries.single()

        playback.playEntry(entry.id)
        assertEquals(SampleAudioStatus.Loading, playback.state.value.audio.status)
        assertTrue(playback.state.value.audio.selection!!.isPending)
        assertEquals(emptyList(), output.restarts)

        address(entry).complete(selection(entry))
        runCurrent()

        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)
        assertEquals("https://audio/${entry.id}", playback.state.value.audio.selection?.uri)
        assertEquals(listOf(true), output.restarts)
    }

    @Test
    fun theNewestChoiceWinsWhenAnEarlierAddressArrivesLate() = runTest {
        val playback = waitingController()
        playback.add(first)
        playback.add(second)
        val (firstEntry, secondEntry) = playback.state.value.queue.entries

        playback.playEntry(firstEntry.id)
        playback.playEntry(secondEntry.id)
        address(secondEntry).complete(selection(secondEntry))
        runCurrent()
        address(firstEntry).complete(selection(firstEntry))
        runCurrent()

        assertEquals(secondEntry.id, playback.selectedEntryId)
        assertEquals(listOf(true), output.restarts)
    }

    @Test
    fun aFailedAddressKeepsTheEntryAndPlayRetries() = runTest {
        var attempts = 0
        val playback = PlaybackController(output, backgroundScope) { entry ->
            attempts++
            if (attempts == 1) throw CatalogException(CatalogException.Kind.Network, "offline")
            selection(entry)
        }
        playback.add(first)
        val entry = playback.state.value.queue.entries.single()

        playback.playEntry(entry.id)
        assertEquals(SampleAudioStatus.Failed, playback.state.value.audio.status)
        assertEquals(
            CatalogException(CatalogException.Kind.Network, "").userMessage,
            playback.state.value.problem,
        )
        assertEquals(listOf(entry), playback.state.value.queue.entries)

        playback.resume()

        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)
        assertNull(playback.state.value.problem)
        assertEquals(2, attempts)
    }

    @Test
    fun theNextAddressIsPreparedWhilePlayingSoTheEndAdvancesAtOnce() = runTest {
        val calls = mutableMapOf<Long, Int>()
        val playback = PlaybackController(output, backgroundScope) { entry ->
            calls[entry.id] = (calls[entry.id] ?: 0) + 1
            selection(entry)
        }
        playback.add(first)
        playback.add(second)
        val (firstEntry, secondEntry) = playback.state.value.queue.entries
        playback.playEntry(firstEntry.id)
        runCurrent()
        assertEquals(1, calls[secondEntry.id])

        output.status = SampleAudioStatus.Ended
        output.emitEvent()
        runCurrent()

        assertEquals(secondEntry.id, playback.selectedEntryId)
        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)
        assertEquals(1, calls[secondEntry.id])
    }

    @Test
    fun aFailingNextEntryStopsInsteadOfSkippingAhead() = runTest {
        val third = Track("demo:track:malam", "Tengah Malam", listOf("Senada"), source = "demo")
        val playback = PlaybackController(output, backgroundScope) { entry ->
            if (entry.track == second) throw CatalogException(CatalogException.Kind.Unavailable, "gone")
            selection(entry)
        }
        playback.add(first)
        playback.add(second)
        playback.add(third)
        val (firstEntry, secondEntry) = playback.state.value.queue.entries
        playback.playEntry(firstEntry.id)
        runCurrent()

        output.status = SampleAudioStatus.Ended
        output.emitEvent()
        runCurrent()

        assertEquals(secondEntry.id, playback.selectedEntryId)
        assertEquals(SampleAudioStatus.Failed, playback.state.value.audio.status)
    }

    @Test
    fun pausingWhileWaitingStaysPausedUntilResumed() = runTest {
        val playback = waitingController()
        playback.add(first)
        val entry = playback.state.value.queue.entries.single()
        playback.playEntry(entry.id)

        playback.pause()
        address(entry).complete(selection(entry))
        runCurrent()
        assertEquals(SampleAudioStatus.Paused, playback.state.value.audio.status)
        assertEquals(emptyList(), output.restarts)

        playback.resume()

        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)
        assertEquals(listOf(true), output.restarts)
    }

    @Test
    fun theEndOfADuplicateEntryStartsTheNextCopyFromTheBeginning() = runTest {
        val playback = controller()
        playback.add(first)
        playback.add(first)
        val (firstEntry, secondEntry) = playback.state.value.queue.entries
        playback.playEntry(firstEntry.id)

        output.status = SampleAudioStatus.Ended
        output.emitEvent()
        runCurrent()

        assertEquals(secondEntry.id, playback.selectedEntryId)
        assertEquals(listOf(true, true), output.restarts)
    }

    /** Jam tes dan alamat stream yang berlaku satu jam, seperti YouTube Music tapi lebih singkat. */
    private var nowMs = 0L
    private val resolutions = mutableMapOf<Long, Int>()

    private fun TestScope.expiringController() =
        PlaybackController(output, backgroundScope, now = { nowMs }) { entry ->
            val count = (resolutions[entry.id] ?: 0) + 1
            resolutions[entry.id] = count
            selection(entry).copy(uri = "https://audio/${entry.id}?v=$count", expiresAtEpochMs = nowMs + HOUR_MS)
        }

    @Test
    fun resumingAfterTheAddressExpiresFetchesANewOneAndKeepsThePosition() = runTest {
        val playback = expiringController()
        playback.add(first)
        val entry = playback.state.value.queue.entries.single()
        playback.playEntry(entry.id)
        output.positionMs = 90_000
        playback.pause()

        nowMs += 2 * HOUR_MS
        playback.resume()

        assertEquals(2, resolutions[entry.id])
        assertEquals("https://audio/${entry.id}?v=2", playback.state.value.audio.selection?.uri)
        assertEquals(listOf(true, true), output.restarts)
        assertEquals(listOf(0L, 90_000L), output.startPositions)
        assertEquals(90_000L, playback.state.value.audio.positionMs)
        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)
    }

    @Test
    fun aShortPauseResumesWithTheSameAddress() = runTest {
        val playback = expiringController()
        playback.add(first)
        val entry = playback.state.value.queue.entries.single()
        playback.playEntry(entry.id)
        output.positionMs = 90_000
        playback.pause()

        nowMs += 60_000
        playback.resume()

        assertEquals(1, resolutions[entry.id])
        assertEquals(listOf(true, false), output.restarts)
    }

    @Test
    fun aStreamFailureIsRetriedOnceFromTheSamePosition() = runTest {
        val playback = expiringController()
        playback.add(first)
        val entry = playback.state.value.queue.entries.single()
        playback.playEntry(entry.id)

        output.positionMs = 60_000
        output.status = SampleAudioStatus.Failed
        output.emitEvent()
        runCurrent()

        assertEquals(2, resolutions[entry.id])
        assertEquals(listOf(0L, 60_000L), output.startPositions)
        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)

        output.status = SampleAudioStatus.Failed
        output.emitEvent()
        runCurrent()

        assertEquals(2, resolutions[entry.id])
        assertEquals(SampleAudioStatus.Failed, playback.state.value.audio.status)
    }

    @Test
    fun playAfterAFailureContinuesFromTheLastPosition() = runTest {
        val playback = expiringController()
        playback.add(first)
        val entry = playback.state.value.queue.entries.single()
        playback.playEntry(entry.id)
        output.positionMs = 30_000
        output.status = SampleAudioStatus.Failed
        output.emitEvent()
        runCurrent()
        output.positionMs = 45_000
        output.status = SampleAudioStatus.Failed
        output.emitEvent()
        runCurrent()

        playback.resume()

        assertEquals(3, resolutions[entry.id])
        assertEquals(listOf(0L, 30_000L, 45_000L), output.startPositions)
        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)
    }

    @Test
    fun anExpiredPreparedAddressIsFetchedAgainBeforeAdvancing() = runTest {
        val playback = expiringController()
        playback.add(first)
        playback.add(second)
        val (firstEntry, secondEntry) = playback.state.value.queue.entries
        playback.playEntry(firstEntry.id)
        runCurrent()
        assertEquals(1, resolutions[secondEntry.id])

        nowMs += 2 * HOUR_MS
        output.status = SampleAudioStatus.Ended
        output.emitEvent()
        runCurrent()

        assertEquals(secondEntry.id, playback.selectedEntryId)
        assertEquals(2, resolutions[secondEntry.id])
        assertEquals("https://audio/${secondEntry.id}?v=2", playback.state.value.audio.selection?.uri)
    }

    @Test
    fun localAudioIsNotRetriedAutomatically() = runTest {
        val playback = controller()
        playback.add(first)
        playback.playEntry(playback.state.value.queue.entries.single().id)

        output.status = SampleAudioStatus.Failed
        output.emitEvent()
        runCurrent()

        assertEquals(listOf(true), output.restarts)
        assertEquals(SampleAudioStatus.Failed, playback.state.value.audio.status)
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
    }
}
