package senada.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import senada.app.nowplaying.PlaySource
import senada.app.nowplaying.PlaySourceState
import senada.db.SenadaDatabase
import senada.library.LibraryRepository
import senada.library.testLibraryDriver
import senada.model.Track
import senada.playback.FakeAudioOutput
import senada.playback.PlaybackController
import senada.playback.SampleAudioSelection
import senada.playback.SampleAudioSource

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryActionsTest {
    @Test
    fun tappingAFavoriteQueuesAllFavoritesAndStartsAtTheSelectedSong() = runTest {
        val driver = testLibraryDriver()
        val previousSource = PlaySourceState.current.value
        try {
            var clock = 0L
            val library = LibraryRepository(
                SenadaDatabase(driver),
                dispatcher = StandardTestDispatcher(testScheduler),
                now = { clock++ },
            )
            val oldest = Track("demo:oldest", "Terlama", listOf("Senada"), source = "demo")
            val selected = Track("demo:selected", "Pilihan", listOf("Senada"), source = "demo")
            val newest = Track("demo:newest", "Terbaru", listOf("Senada"), source = "demo")
            listOf(oldest, selected, newest).forEach { library.setFavorite(it, true) }
            val playback = PlaybackController(FakeAudioOutput(), backgroundScope) { entry ->
                SampleAudioSelection(entry.track.title, SampleAudioSource.Local, "file:///${entry.id}.m4a", entry.id)
            }

            LibraryActions(library, playback, this).playAllFavorites(startTrackId = selected.id)
            runCurrent()

            assertEquals(listOf(newest, selected, oldest), playback.state.value.queue.entries.map { it.track })
            assertEquals(selected, playback.state.value.currentEntry?.track)
            assertEquals(PlaySource.Favorites, PlaySourceState.current.value)
        } finally {
            PlaySourceState.current.value = previousSource
            driver.close()
        }
    }
}
