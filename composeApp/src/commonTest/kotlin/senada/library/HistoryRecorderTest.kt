package senada.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import senada.db.SenadaDatabase
import senada.model.Track
import senada.playback.FakeAudioOutput
import senada.playback.PlaybackController
import senada.playback.SampleAudioSelection
import senada.playback.SampleAudioSource
import senada.playback.SampleAudioStatus

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryRecorderTest {
    private val output = FakeAudioOutput()
    private val first = Track("yt:track:a", "Pertama", listOf("Senada"), source = "ytmusic")
    private val second = Track("yt:track:b", "Kedua", listOf("Senada"), source = "ytmusic")

    private fun TestScope.setUp(): Pair<PlaybackController, LibraryRepository> {
        val library = LibraryRepository(
            SenadaDatabase(testLibraryDriver()),
            dispatcher = StandardTestDispatcher(testScheduler),
            now = { testScheduler.currentTime },
        )
        val playback = PlaybackController(output, backgroundScope) { entry ->
            SampleAudioSelection(entry.track.title, SampleAudioSource.YouTubeMusic, "https://audio/${entry.id}", entry.id)
        }
        playback.recordHistoryTo(library, this)
        return playback to library
    }

    @Test
    fun songsAreRecordedWhenTheyStartPlaying() = runTest {
        val (playback, library) = setUp()
        playback.add(first)
        playback.add(second)
        val (firstEntry, secondEntry) = playback.state.value.queue.entries
        runCurrent()
        assertEquals(emptyList(), library.history().first())

        playback.playEntry(firstEntry.id)
        runCurrent()
        testScheduler.advanceTimeBy(1)
        playback.playEntry(secondEntry.id)
        runCurrent()

        assertEquals(listOf(second, first), library.history().first())
        assertEquals(listOf(second, first), library.mostPlayed().first())
    }

    @Test
    fun resumingAPausedSongDoesNotRecordItAgain() = runTest {
        val (playback, library) = setUp()
        playback.add(first)
        playback.playEntry(playback.state.value.queue.entries.single().id)
        runCurrent()
        assertEquals(HomeHero.FromTrack(first, 1), library.homeHero().first())
        playback.pause()

        playback.resume()
        runCurrent()

        assertEquals(SampleAudioStatus.Playing, playback.state.value.audio.status)
        assertEquals(HomeHero.FromTrack(first, 1), library.homeHero().first())
        assertEquals(listOf(first), library.history().first())
    }

    @Test
    fun theSampleButtonsAreNotRecorded() = runTest {
        val (playback, library) = setUp()

        playback.play(SampleAudioSelection("Selepas Hujan", SampleAudioSource.Local, "file:///contoh.m4a"))
        runCurrent()

        assertEquals(emptyList(), library.history().first())
        assertEquals(emptyList(), library.mostPlayed().first())
    }
}
