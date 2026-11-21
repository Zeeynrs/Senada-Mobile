package senada.library

import app.cash.sqldelight.db.QueryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import senada.db.SenadaDatabase
import senada.model.Track

class LibraryRepositoryTest {
    private val driver = testLibraryDriver()
    private val database = SenadaDatabase(driver)
    private var clock = 1_000L
    private var playlistIds = 0

    private fun TestScope.library() = LibraryRepository(
        database,
        dispatcher = StandardTestDispatcher(testScheduler),
        now = { clock++ },
        newPlaylistId = { "local-test${++playlistIds}" },
    )

    private fun track(n: Int) = Track("yt:track:video$n", "Lagu $n", listOf("Penyanyi $n"), 200_000L + n, null, "ytmusic")

    private val duet = Track(
        id = "yt:track:duet",
        title = "Hujan, Lagi",
        artists = listOf("Revina Alvira", "Rian, \"D'Masiv\"", "Nadin Amizah"),
        durationMs = null,
        artworkUrl = "https://lh3.example.invalid/cover=w544-h544",
        source = "ytmusic",
    )

    /** Jumlah baris lagu tersimpan, untuk memastikan lagu yang tidak dipakai ikut terhapus. */
    private fun trackRows(): Long = driver.executeQuery(
        identifier = null,
        sql = "SELECT count(*) FROM track",
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0)!!)
        },
        parameters = 0,
    ).value

    @Test
    fun favoritesAreNewestFirstAndKeepEveryTrackField() = runTest {
        val library = library()

        library.setFavorite(track(1), true)
        library.setFavorite(duet, true)

        assertEquals(listOf(duet, track(1)), library.favorites.first())
        assertTrue(library.isFavorite(duet.id).first())
    }

    @Test
    fun favoritesAreReadBackByANewRepository() = runTest {
        library().setFavorite(duet, true)

        assertEquals(listOf(duet), library().favorites.first())
    }

    @Test
    fun toggleFavoriteFlipsAndForgetsUnusedTracks() = runTest {
        val library = library()

        assertTrue(library.toggleFavorite(duet))
        assertFalse(library.toggleFavorite(duet))

        assertEquals(emptyList(), library.favorites.first())
        assertFalse(library.isFavorite(duet.id).first())
        assertEquals(0L, trackRows())
    }

    @Test
    fun favoritingAgainRefreshesTheStoredTrack() = runTest {
        val library = library()
        library.setFavorite(duet.copy(artworkUrl = null), true)

        library.setFavorite(duet, true)

        assertEquals(listOf(duet), library.favorites.first())
    }

    @Test
    fun playlistsKeepOrderAndAllowTheSameSongTwice() = runTest {
        val library = library()
        val id = library.createPlaylist("  Pagi  ", listOf(track(1), track(2)))

        assertEquals(3, library.addToPlaylist(id, listOf(track(1), duet, track(3))))

        val playlist = library.playlist(id).first()!!
        assertEquals("local-test1", playlist.id)
        assertEquals("Pagi", playlist.name)
        assertEquals(listOf(track(1), track(2), track(1), duet, track(3)), playlist.entries.map { it.track })
        assertEquals(5, playlist.entries.map { it.itemId }.toSet().size)
        assertEquals(listOf(PlaylistSummary(id, "Pagi", "", 1_000L, 5)), library.playlists.first())
    }

    @Test
    fun newestPlaylistComesFirstAndBlankNamesGetADefault() = runTest {
        val library = library()
        library.createPlaylist("Lama")
        library.createPlaylist("   ")

        assertEquals(listOf("Playlist baru", "Lama"), library.playlists.first().map { it.name })
    }

    @Test
    fun movingAndRemovingEntriesPersistsTheNewOrder() = runTest {
        val library = library()
        val id = library.createPlaylist("Urut", listOf(track(1), track(2), track(3), track(1)))

        assertTrue(library.movePlaylistItem(id, from = 2, to = 0))
        assertFalse(library.movePlaylistItem(id, from = 0, to = 9))
        assertEquals(listOf(3, 1, 2, 1), titlesOf(library.playlist(id).first()!!))

        // Hanya salinan pertama lagu 1 yang dihapus; salinan lainnya tetap.
        val firstCopy = library.playlist(id).first()!!.entries[1].itemId
        assertTrue(library.removeFromPlaylist(id, firstCopy))
        assertEquals(listOf(3, 2, 1), titlesOf(library.playlist(id).first()!!))

        library.addToPlaylist(id, listOf(track(4)))
        assertEquals(listOf(3, 2, 1, 4), titlesOf(library.playlist(id).first()!!))
    }

    @Test
    fun renameAndDeleteOnlyTouchExistingPlaylists() = runTest {
        val library = library()
        val id = library.createPlaylist("Lama", listOf(track(1)))

        assertFalse(library.renamePlaylist(id, "   "))
        assertTrue(library.renamePlaylist(id, "Baru"))
        assertFalse(library.renamePlaylist("local-missing", "X"))
        assertEquals("Baru", library.playlist(id).first()!!.name)
        assertEquals(0, library.addToPlaylist("local-missing", listOf(track(2))))

        assertTrue(library.deletePlaylist(id))
        assertFalse(library.deletePlaylist(id))
        assertNull(library.playlist(id).first())
        assertEquals(emptyList(), library.playlists.first())
        assertEquals(0L, trackRows())
    }

    @Test
    fun historyKeepsTheLatest200UniqueSongs() = runTest {
        val library = library()
        for (n in 1..205) library.recordPlay(track(n))
        library.recordPlay(track(10))

        val history = library.history().first()
        assertEquals(LibraryRepository.HISTORY_LIMIT, history.size)
        assertEquals(track(10), history.first())
        assertEquals(track(205), history[1])
        assertEquals(1, history.count { it == track(10) })
        // Lagu 1–5 tersingkir saat lagu 201–205 masuk; memutar ulang lagu 10 tidak menambah jumlah.
        assertTrue(history.none { it.id in (1..5).map { n -> track(n).id } })
        assertEquals(track(6), history.last())
        assertEquals(listOf(track(10), track(205)), library.history(limit = 2).first())
    }

    @Test
    fun songsStillUsedElsewhereSurviveRemoval() = runTest {
        val library = library()
        library.setFavorite(duet, true)
        library.recordPlay(duet)

        library.setFavorite(duet, false)

        assertEquals(listOf(duet), library.history().first())
    }

    @Test
    fun savedItemsKeepTheirMetadata() = runTest {
        val library = library()
        val metadata = """{"kind":"album","id":"MPREb_x","name":"Manusia"}"""

        library.setSaved("album", "MPREb_x", metadata, saved = true)
        assertEquals(listOf(SavedItem("album", "MPREb_x", metadata, 1_000L)), library.savedItems.first())

        library.setSaved("album", "MPREb_x", metadata, saved = false)
        assertEquals(emptyList(), library.savedItems.first())
    }

    @Test
    fun artistNamesRoundTripThroughStorage() {
        assertEquals(duet.artists, decodeArtists(encodeArtists(duet.artists)))
        assertEquals(emptyList(), decodeArtists("bukan json"))
    }

    private fun titlesOf(playlist: Playlist) = playlist.entries.map { it.track.title.removePrefix("Lagu ").toInt() }
}
