package senada.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import senada.db.SenadaDatabase
import senada.model.Track

/** Fixture 10.000 lagu sesuai "Definisi ringan" di rencana: daftar dibaca per halaman 50. */
class LibraryPagingTest {
    private val driver = testLibraryDriver()
    private val database = SenadaDatabase(driver)
    private var clock = 1_000_000L

    private fun TestScope.library() = LibraryRepository(
        database,
        dispatcher = StandardTestDispatcher(testScheduler),
        now = { clock++ },
        newPlaylistId = { "local-besar" },
    )

    private fun track(n: Int) = Track("yt:track:v$n", "Lagu $n", listOf("Artis ${n % 97}"), 180_000, null, "ytmusic")

    /** Mengisi langsung dalam satu transaksi supaya fixture cepat; lagu n ditambahkan pada waktu n. */
    private fun fillFavorites(count: Int) {
        database.transaction {
            for (n in 1..count) {
                val t = track(n)
                database.libraryQueries.upsertTrack(t.id, t.title, encodeArtists(t.artists), t.durationMs, null, t.source)
                database.libraryQueries.addFavorite(t.id, n.toLong())
            }
        }
    }

    private fun fillPlaylist(id: String, count: Int) {
        database.transaction {
            database.libraryQueries.insertPlaylist(id, "Besar", "", 1)
            for (n in 1..count) {
                val t = track(n)
                database.libraryQueries.upsertTrack(t.id, t.title, encodeArtists(t.artists), t.durationMs, null, t.source)
                database.libraryQueries.insertPlaylistItem(id, t.id, (n - 1).toLong())
            }
        }
    }

    @Test
    fun favoritesComeInPagesOfFiftyNewestFirst() = runTest {
        fillFavorites(10_000)
        val library = library()

        val started = TimeSource.Monotonic.markNow()
        val first = library.favoritesPage(50).first()
        println("Halaman favorit pertama dari 10.000: ${started.elapsedNow()}")

        assertEquals(50, first.size)
        assertEquals((10_000 downTo 9_951).map { "yt:track:v$it" }, first.map { it.id })
        assertEquals(10_000, library.favoriteTotal.first())
        assertEquals(10_000, library.favoriteIds.first().size)

        // Halaman berikutnya memperpanjang daftar tanpa mengubah awalnya.
        val twoPages = library.favoritesPage(100).first()
        assertEquals(first, twoPages.take(50))
        assertEquals((9_950 downTo 9_901).map { "yt:track:v$it" }, twoPages.drop(50).map { it.id })
    }

    @Test
    fun paginatingToTheEndVisitsEveryFavoriteOnce() = runTest {
        fillFavorites(10_000)
        val library = library()

        var limit = 50
        var page = library.favoritesPage(limit).first()
        while (page.size == limit) {
            limit += 50
            page = library.favoritesPage(limit).first()
        }

        assertEquals(10_000, page.size)
        assertEquals(10_000, page.map { it.id }.toSet().size)
    }

    @Test
    fun aNewFavoriteAppearsAtTheTopOfTheFirstPage() = runTest {
        fillFavorites(200)
        clock = 1_000_000L
        val library = library()

        library.setFavorite(track(50_000), true)

        assertEquals("yt:track:v50000", library.favoritesPage(50).first().first().id)
        assertEquals(201, library.favoriteTotal.first())
    }

    @Test
    fun bigPlaylistsLoadTheirFirstEntriesAndCountTheRest() = runTest {
        fillPlaylist("local-besar", 10_000)
        val library = library()

        val started = TimeSource.Monotonic.markNow()
        val playlist = library.playlist("local-besar", limit = 50).first()!!
        println("Halaman playlist pertama dari 10.000: ${started.elapsedNow()}")

        assertEquals(50, playlist.entries.size)
        assertEquals(10_000, playlist.entryCount)
        assertEquals((1..50).map { "yt:track:v$it" }, playlist.entries.map { it.track.id })
        assertEquals(10_000, library.playlistTracks("local-besar").size)

        library.movePlaylistItem("local-besar", from = 0, to = 1)
        assertEquals(listOf("yt:track:v2", "yt:track:v1"), library.playlist("local-besar", 2).first()!!.entries.map { it.track.id })
    }

    @Test
    fun allFavoritesReturnsEverythingForPlayAll() = runTest {
        fillFavorites(120)

        val all = library().allFavorites()

        assertEquals(120, all.size)
        assertEquals("yt:track:v120", all.first().id)
    }

    @Test
    fun aVersionTwoDatabaseGainsTheFavoriteIndex() = runTest {
        driver.execute(null, "DROP INDEX favorite_added_at", 0)
        driver.execute(null, "DROP TABLE play_event", 0)
        driver.execute(null, "DROP TABLE playlist_play", 0)
        driver.execute(null, "DROP TABLE imported_track", 0)
        fillFavorites(3)

        SenadaDatabase.Schema.migrate(driver, 2, SenadaDatabase.Schema.version)

        assertEquals(5L, SenadaDatabase.Schema.version)
        assertEquals(3, library().favoritesPage(50).first().size)
    }
}
