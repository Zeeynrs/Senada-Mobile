package senada.app

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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

    private fun track(n: Int) = Track(
        id = "yt:track:v$n",
        title = "Lagu $n",
        artists = listOf("Artis $n"),
        durationMs = 180_000L,
        source = "ytmusic",
    )

    private fun TestScope.harness(): Triple<LibraryRepository, PlaybackController, LibraryActions> {
        var now = 1_000L
        var seq = 0
        val library = LibraryRepository(
            database = SenadaDatabase(testLibraryDriver()),
            dispatcher = StandardTestDispatcher(testScheduler),
            now = { now++ },
            newPlaylistId = { "local-${++seq}" },
        )
        val output = FakeAudioOutput()
        val playback = PlaybackController(output, backgroundScope) { entry ->
            SampleAudioSelection(entry.track.title, SampleAudioSource.Local, "file:///${entry.id}.m4a", entry.id)
        }
        val actions = LibraryActions(library, playback, this)
        PlaySourceState.current.value = null
        return Triple(library, playback, actions)
    }

    @Test
    fun playShuffledEnablesShuffleAndPlayAllWithResetShuffleRestoresOrder() = runTest {
        val (_, playback, actions) = harness()
        val tracks = (1..5).map(::track)

        actions.playShuffled(tracks, PlaySource.Album("Manusia"), Random(7))

        val afterShuffle = playback.state.value
        assertTrue(afterShuffle.queue.shuffleEnabled)
        assertEquals(tracks, afterShuffle.queue.entries.map { it.track })
        assertEquals(afterShuffle.queue.playOrder.first().id, afterShuffle.currentEntry?.id)
        assertEquals(PlaySource.Album("Manusia"), PlaySourceState.current.value)

        // Memutar ulang secara berurutan harus mematikan mode acak agar urutan lagu tetap terjaga.
        val nextCollection = (10..12).map(::track)
        actions.playAll(nextCollection, PlaySource.Playlist("Pagi"), resetShuffle = true)

        val afterOrdered = playback.state.value
        assertFalse(afterOrdered.queue.shuffleEnabled)
        assertEquals(nextCollection.first(), afterOrdered.currentEntry?.track)
        assertEquals(PlaySource.Playlist("Pagi"), PlaySourceState.current.value)
    }

    @Test
    fun playShuffledOverExistingQueueSelectsTrackFromNewCollection() = runTest {
        val (_, playback, actions) = harness()
        val old = track(99)
        playback.add(old)
        playback.playEntry(playback.state.value.queue.entries.first().id)
        playback.setShuffleEnabled(true)

        val collection = (1..4).map(::track)
        actions.playShuffled(collection, PlaySource.Favorites, Random(3))

        val state = playback.state.value
        assertTrue(state.queue.shuffleEnabled)
        assertTrue(state.currentEntry?.track in collection)
        assertEquals(state.currentEntry?.id, state.queue.playOrder.first().id)
    }

    @Test
    fun shuffleAndEnqueueFavoritesLoadAllFavorites() = runTest {
        val (library, playback, actions) = harness()
        val favorites = (1..3).map(::track)
        favorites.forEach { library.setFavorite(it, true) }

        actions.enqueueAllFavorites()
        advanceUntilIdle()
        assertEquals(3, playback.state.value.queue.entries.size)

        actions.shuffleFavorites()
        advanceUntilIdle()
        val state = playback.state.value
        assertTrue(state.queue.shuffleEnabled)
        assertEquals(PlaySource.Favorites, PlaySourceState.current.value)
        assertTrue(state.currentEntry?.track in favorites)
    }

    @Test
    fun shufflePlaylistLoadsPlaylistAndStartsShuffled() = runTest {
        val (library, playback, actions) = harness()
        val songs = (1..3).map(::track)
        val playlistId = library.createPlaylist("Santai", songs)

        actions.shufflePlaylist(playlistId)
        advanceUntilIdle()

        val state = playback.state.value
        assertTrue(state.queue.shuffleEnabled)
        assertEquals(PlaySource.Playlist("Santai"), PlaySourceState.current.value)
        assertTrue(state.currentEntry?.track in songs)
    }

    @Test
    fun historyPlayResetsShuffleAndStartsFromNewestWhileRowTapKeepsShuffle() = runTest {
        val (_, playback, actions) = harness()
        val newestToOldest = listOf(track(3), track(2), track(1))
        playback.setShuffleEnabled(true)

        actions.playHistory(newestToOldest)

        val fromHeader = playback.state.value
        assertFalse(fromHeader.queue.shuffleEnabled)
        assertEquals(newestToOldest, fromHeader.queue.entries.map { it.track })
        assertEquals(track(3), fromHeader.currentEntry?.track)
        assertEquals(PlaySource.History, PlaySourceState.current.value)

        playback.setShuffleEnabled(true)
        actions.playHistory(newestToOldest, startIndex = 1)
        val fromRow = playback.state.value
        assertTrue(fromRow.queue.shuffleEnabled)
        assertEquals(track(2), fromRow.currentEntry?.track)
        assertEquals(PlaySource.History, PlaySourceState.current.value)

        actions.shuffleHistory(newestToOldest)
        val shuffled = playback.state.value
        assertTrue(shuffled.queue.shuffleEnabled)
        assertEquals(PlaySource.History, PlaySourceState.current.value)
        assertTrue(shuffled.currentEntry?.track in newestToOldest)
    }

    @Test
    fun importedTracksPlayAllAndShuffleWithImportedSource() = runTest {
        val (_, playback, actions) = harness()
        val imported = (1..3).map {
            Track("local:file-$it.m4a", "Rekaman $it", listOf("Senada"), durationMs = 60_000L, source = "local")
        }
        playback.setShuffleEnabled(true)

        actions.playImportedTracks(imported)
        val ordered = playback.state.value
        assertFalse(ordered.queue.shuffleEnabled)
        assertEquals(imported, ordered.queue.entries.map { it.track })
        assertEquals(imported.first(), ordered.currentEntry?.track)
        assertEquals(PlaySource.Imported, PlaySourceState.current.value)

        actions.playImportedTracks(imported, startIndex = 2)
        assertEquals(imported[2], playback.state.value.currentEntry?.track)

        actions.shuffleImportedTracks(imported)
        val shuffled = playback.state.value
        assertTrue(shuffled.queue.shuffleEnabled)
        assertEquals(PlaySource.Imported, PlaySourceState.current.value)
        assertTrue(shuffled.currentEntry?.track in imported)
    }
}
