package senada.library

import app.cash.sqldelight.db.QueryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import senada.db.SenadaDatabase
import senada.model.Track

class PlayStatsTest {
    private val driver = testLibraryDriver()
    private val database = SenadaDatabase(driver)
    private var clock = 0L
    private val first = Track(
        id = "yt:track:a",
        title = "Pertama",
        artists = listOf("Senada"),
        artworkUrl = "https://artwork.example.invalid/pertama.jpg",
        source = "ytmusic",
    )
    private val second = Track("yt:track:b", "Kedua", emptyList(), source = "ytmusic")

    private fun TestScope.library() = LibraryRepository(
        database,
        dispatcher = StandardTestDispatcher(testScheduler),
        now = { clock },
        newPlaylistId = { "local-test" },
    )

    private fun countRows(sql: String): Long = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0)!!)
        },
        parameters = 0,
    ).value

    @Test
    fun heroWelcomesBeforeAnyPlays() = runTest {
        assertEquals(HomeHero.Welcome, library().homeHero().first())
    }

    @Test
    fun heroShowsMostPlayedTrack() = runTest {
        val library = library()
        repeat(3) { library.recordTrackPlay(first) }
        library.recordTrackPlay(second)

        assertEquals(HomeHero.FromTrack(first, 3), library.homeHero().first())
    }

    @Test
    fun tiesGoToTheMoreRecentPlay() = runTest {
        val library = library()
        library.recordTrackPlay(second)
        repeat(2) { library.recordTrackPlay(first) }
        clock++
        library.recordTrackPlay(second)

        assertEquals(HomeHero.FromTrack(second, 2), library.homeHero().first())
        assertEquals(listOf(second, first), library.mostPlayed().first())
    }

    @Test
    fun aPlaylistPlayedThreeTimesWins() = runTest {
        val library = library()
        val id = library.createPlaylist("Pagi", listOf(first, second))
        repeat(10) { library.recordTrackPlay(first) }
        repeat(3) { library.recordPlaylistPlay(id) }

        assertEquals(
            HomeHero.FromPlaylist(PlaylistSummary(id, "Pagi", "", 0, 2), first.artworkUrl, 3),
            library.homeHero().first(),
        )
    }

    @Test
    fun twoPlaylistPlaysAreNotEnough() = runTest {
        val library = library()
        val id = library.createPlaylist("Pagi", listOf(first))
        repeat(2) { library.recordPlaylistPlay(id) }
        library.recordTrackPlay(second)

        assertEquals(HomeHero.FromTrack(second, 1), library.homeHero().first())
    }

    @Test
    fun oldPlaysFallOutOfTheWindow() = runTest {
        val library = library()
        val id = library.createPlaylist("Pagi", listOf(second))
        repeat(5) { library.recordTrackPlay(first) }
        repeat(3) { library.recordPlaylistPlay(id) }

        clock = PLAY_STATS_WINDOW_MS + 24L * 60 * 60 * 1_000
        assertEquals(HomeHero.Welcome, library.homeHero().first())
        library.recordTrackPlay(second)

        assertEquals(HomeHero.FromTrack(second, 1), library.homeHero().first())
        assertEquals(1L, countRows("SELECT count(*) FROM play_event"))
        assertEquals(0L, countRows("SELECT count(*) FROM playlist_play"))
        assertEquals(1L, countRows("SELECT count(*) FROM track"))
    }

    @Test
    fun deletedPlaylistFallsBack() = runTest {
        val library = library()
        val id = library.createPlaylist("Pagi", listOf(first))
        repeat(3) { library.recordPlaylistPlay(id) }
        library.recordTrackPlay(second)

        library.deletePlaylist(id)

        assertEquals(HomeHero.FromTrack(second, 1), library.homeHero().first())
    }

    @Test
    fun emptyPlaylistIsNotCounted() = runTest {
        val library = library()
        val id = library.createPlaylist("Pagi")
        repeat(3) { library.recordPlaylistPlay(id) }

        assertEquals(0L, countRows("SELECT count(*) FROM playlist_play"))
        assertEquals(HomeHero.Welcome, library.homeHero().first())
    }

    @Test
    fun mostPlayedListsTopTracks() = runTest {
        val library = library()
        repeat(2) { library.recordTrackPlay(first) }
        repeat(3) { library.recordTrackPlay(second) }
        library.recordPlay(first.copy(id = "yt:track:history-only"))

        assertEquals(listOf(second, first), library.mostPlayed().first())
        assertEquals(listOf(second), library.mostPlayed(limit = 1).first())
    }

    @Test
    fun countedSongsSurviveCleanup() = runTest {
        val library = library()
        library.setFavorite(first, true)
        library.recordTrackPlay(first)

        library.setFavorite(first, false)

        assertEquals(HomeHero.FromTrack(first, 1), library.homeHero().first())
        assertEquals(1L, countRows("SELECT count(*) FROM track"))
    }

    @Test
    fun aVersionThreeDatabaseGainsPlayStats() = runTest {
        driver.execute(null, "DROP TABLE play_event", 0)
        driver.execute(null, "DROP TABLE playlist_play", 0)
        val library = library()
        library.setFavorite(first, true)

        SenadaDatabase.Schema.migrate(driver, 3, 4)
        library.recordTrackPlay(second)

        assertTrue(SenadaDatabase.Schema.version >= 4L)
        assertEquals(listOf(first), library.favorites.first())
        assertEquals(HomeHero.FromTrack(second, 1), library.homeHero().first())
    }
}
