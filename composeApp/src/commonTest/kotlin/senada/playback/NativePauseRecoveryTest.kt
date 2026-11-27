package senada.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import senada.model.Track

/**
 * Pemutar native kadang melaporkan Paused sesaat walau pengguna tidak menjeda: saat lagu berganti,
 * atau di iOS tepat sebelum notifikasi akhir lagu. Status tidak boleh macet di Paused karenanya.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NativePauseRecoveryTest {
    private val output = FakeAudioOutput()
    private val first = Track("t:1", "Satu", listOf("A"), source = "test")
    private val second = Track("t:2", "Dua", listOf("A"), source = "test")

    private fun TestScope.controller() = PlaybackController(output, backgroundScope) { entry ->
        SampleAudioSelection(entry.track.title, SampleAudioSource.YouTubeMusic, "https://audio/${entry.id}", entry.id)
    }

    private fun TestScope.playingFirstOfTwo(): Pair<PlaybackController, List<QueueItem>> {
        val playback = controller()
        playback.add(first)
        playback.add(second)
        val entries = playback.state.value.queue.entries
        playback.playEntry(entries.first().id)
        runCurrent()
        return playback to entries
    }

    private fun TestScope.nativeReports(status: SampleAudioStatus) {
        output.status = status
        output.emitEvent()
        runCurrent()
    }

    @Test
    fun aTransientNativePauseDoesNotFreezeThePlayingState() = runTest {
        val (playback, _) = playingFirstOfTwo()

        nativeReports(SampleAudioStatus.Paused)
        nativeReports(SampleAudioStatus.Playing)

        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)
    }

    @Test
    fun theTrackEndAfterANativePauseStillAdvancesToTheNextEntry() = runTest {
        val (playback, entries) = playingFirstOfTwo()

        nativeReports(SampleAudioStatus.Paused)
        nativeReports(SampleAudioStatus.Ended)

        assertEquals(entries[1].id, playback.state.value.audio.selection?.queueEntryId)
        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)
    }

    @Test
    fun nextShowsPlayingOnceTheNewTrackStartsAfterANativePause() = runTest {
        val (playback, entries) = playingFirstOfTwo()

        playback.next()
        nativeReports(SampleAudioStatus.Paused)
        nativeReports(SampleAudioStatus.Playing)

        assertEquals(entries[1].id, playback.state.value.audio.selection?.queueEntryId)
        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)
    }

    @Test
    fun aUserPauseStaysPausedEvenWhenTheNativePlayerReportsTheEnd() = runTest {
        val (playback, entries) = playingFirstOfTwo()

        playback.pause()
        nativeReports(SampleAudioStatus.Ended)

        assertEquals(entries[0].id, playback.state.value.audio.selection?.queueEntryId)
        assertEquals(SampleAudioStatus.Paused, playback.state.value.audio.status)
    }

    @Test
    fun aUserPauseIsNotUndoneByALateNativePlayingReport() = runTest {
        val (playback, _) = playingFirstOfTwo()

        playback.pause()
        nativeReports(SampleAudioStatus.Playing)

        assertEquals(SampleAudioStatus.Paused, playback.state.value.audio.status)
    }
}
