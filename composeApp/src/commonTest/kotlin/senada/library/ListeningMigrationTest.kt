package senada.library

import app.cash.sqldelight.db.QueryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import senada.db.SenadaDatabase
import senada.model.Track

class ListeningMigrationTest {
    @Test
    fun listening_checkpoints_replace_the_same_play_and_survive_track_cleanup() = runTest {
        val driver = testLibraryDriver()
        try {
            var clock = 100_000L
            val library = LibraryRepository(
                SenadaDatabase(driver),
                dispatcher = StandardTestDispatcher(testScheduler),
                now = { clock },
            )
            val track = Track("yt:track:lama", "Lagu lama", listOf("A", "B"), source = "ytmusic")
            val event = ListenEvent("putaran", track, 10_000L, 30_000L)
            library.setFavorite(track, true)
            library.recordListening(event)
            library.recordListening(event.copy(listenedMs = 41_000L))
            library.setFavorite(track, false)
            clock += PLAY_STATS_WINDOW_MS + 1
            val recent = track.copy(id = "yt:track:baru")
            library.recordTrackPlay(recent)

            val summary = library.listeningStats(ListenPeriod.All).first()
            assertEquals(1, summary.plays)
            assertEquals(41_000L, summary.listenedMs)
            assertEquals(track, summary.topTracks.single().track)
            assertEquals("A", summary.topArtists.single().name)
            assertEquals(listOf(recent), library.mostPlayed().first())
        } finally {
            driver.close()
        }
    }

    @Test
    fun schema_six_adds_empty_stats_and_preserves_version_five_library() = runTest {
        val driver = testLibraryDriver()
        try {
            driver.execute(null, "DROP TABLE IF EXISTS listen_event", 0)
            val library = LibraryRepository(
                SenadaDatabase(driver),
                dispatcher = StandardTestDispatcher(testScheduler),
                now = { 100L },
            )
            val song = Track("yt:track:lama", "Lagu lama", listOf("Artis"), source = "ytmusic")
            val local = Track("local:lama.mp3", "Rekaman lama", emptyList(), source = "local")
            library.recordTrackPlay(song, includeHistory = true)
            library.setFavorite(song, true)
            library.recordImported(local)
            val playlist = library.createPlaylist("Koleksi", listOf(song))

            SenadaDatabase.Schema.migrate(driver, 5, 6)

            assertEquals(6L, SenadaDatabase.Schema.version)
            val rows = driver.executeQuery(null, "SELECT count(*) FROM listen_event", { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0))
            }, 0).value
            assertEquals(0L, rows)
            assertEquals(listOf(song), library.history().first())
            assertEquals(listOf(song), library.favorites.first())
            assertEquals(listOf(song), library.mostPlayed().first())
            assertEquals(listOf(song), library.playlistTracks(playlist))
            assertEquals(listOf(local), library.localTracks().first())
        } finally {
            driver.close()
        }
    }
}
