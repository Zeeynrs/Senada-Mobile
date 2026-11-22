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
    fun localTrackIdsIncludeFavoritesPlaylistsAndHistory() = runTest {
        val library = library()
        val favorite = duet.copy(id = "local:favorite.mp3", source = "local")
        val playlistTrack = duet.copy(id = "local:playlist.m4a", source = "local")
        val historyTrack = duet.copy(id = "local:history.flac", source = "local")
        library.setFavorite(favorite, true)
        library.createPlaylist("Audio lokal", listOf(playlistTrack))
        library.recordPlay(historyTrack)
        library.setFavorite(duet.copy(id = "local:catalog.mp3"), true)
        library.recordPlay(duet.copy(id = "LOCAL:wrong-prefix.mp3", source = "local"))

        assertEquals(
            setOf("local:favorite.mp3", "local:playlist.m4a", "local:history.flac"),
            library.withLocalTrackIds { it.toSet() },
        )

        library.setFavorite(favorite, false)
        assertEquals(
            setOf("local:playlist.m4a", "local:history.flac"),
            library.withLocalTrackIds { it.toSet() },
        )
    }

    @Test
    fun localTracksListsOnlyImportedSongs() = runTest {
        val library = library()
        val imported = duet.copy(id = "local:rekaman.m4a", title = "Rekaman", artworkUrl = null, source = "local")
        val wrongPrefix = duet.copy(id = "LOCAL:salah.m4a", title = "Salah", artworkUrl = null, source = "local")
        library.setFavorite(track(1), true)
        library.recordImported(track(2))
        library.recordImported(wrongPrefix)
        library.recordImported(imported)

        assertEquals(listOf(imported), library.localTracks().first())
    }

    @Test
    fun importedTrackAppearsBeforeBeingPlayedAndSurvivesCleanupAndRestart() = runTest {
        val library = library()
        val imported = Track(
            id = "local:latihan.m4a",
            title = "Rekaman Latihan",
            artists = listOf("Senada"),
            durationMs = 123_456L,
            artworkUrl = null,
            source = "local",
        )

        library.recordImported(imported)
        assertEquals(listOf(imported), library.localTracks().first())

        // Pembersihan lagu tak terpakai tidak boleh menghapus file impor yang belum pernah diputar.
        library.setFavorite(track(1), true)
        library.setFavorite(track(1), false)
        assertEquals(listOf(imported), library.localTracks().first())

        // Tetap tampil dengan metadatanya setelah repository dibuka ulang seperti saat aplikasi dimulai ulang.
        assertEquals(listOf(imported), library().localTracks().first())
    }

    @Test
    fun deletedImportedFileIsNoLongerListed() = runTest {
        val library = library()
        val first = duet.copy(id = "local:pertama.m4a", title = "Pertama", artworkUrl = null, source = "local")
        val second = duet.copy(id = "local:kedua.mp3", title = "Kedua", artworkUrl = null, source = "local")
        library.recordImported(first)
        library.recordImported(second)
        library.setFavorite(first, true)

        library.syncImportedFiles(setOf("kedua.mp3"))

        assertEquals(listOf(second), library.localTracks().first())
        assertEquals(listOf(first), library.favorites.first())

        library.syncImportedFiles(emptySet())
        assertEquals(emptyList(), library.localTracks().first())

        library.setFavorite(first, false)
        assertEquals(0L, trackRows())
    }

    @Test
    fun importedOnlyTrackDoesNotBlockUnusedFileCleanup() = runTest {
        val library = library()
        val importedOnly = duet.copy(id = "local:sementara.mp3", title = "Sementara", artworkUrl = null, source = "local")
        val favorited = duet.copy(id = "local:favorit.mp3", title = "Favorit", artworkUrl = null, source = "local")
        library.recordImported(importedOnly)
        library.recordImported(favorited)
        library.setFavorite(favorited, true)

        assertEquals(setOf("local:favorit.mp3"), library.withLocalTrackIds { it.toSet() })

        library.setFavorite(favorited, false)
        assertEquals(emptySet(), library.withLocalTrackIds { it.toSet() })
        assertEquals(listOf(favorited, importedOnly), library.localTracks().first())
    }

    @Test
    fun syncImportedFilesRecordsExistingQueuedLocalTracksAndSkipsCatalogSongs() = runTest {
        val library = library()
        val queued = duet.copy(id = "local:antrean.m4a", title = "Antrean", artworkUrl = null, source = "local")
        val missing = duet.copy(id = "local:hilang.m4a", title = "Hilang", artworkUrl = null, source = "local")

        library.syncImportedFiles(
            existingFileNames = setOf("antrean.m4a"),
            knownTracks = listOf(queued, missing, track(1)),
        )

        assertEquals(listOf(queued), library.localTracks().first())
    }

    @Test
    fun aVersionFourDatabaseMigratesExistingLocalTracksToImportedList() = runTest {
        driver.execute(null, "DROP TABLE imported_track", 0)
        val library = library()
        val existingLocal = duet.copy(id = "local:lama.m4a", title = "Rekaman Lama", artworkUrl = null, source = "local")
        val newlyImported = duet.copy(id = "local:baru.mp3", title = "Baru Diimpor", artworkUrl = null, source = "local")
        library.setFavorite(existingLocal, true)
        library.setFavorite(track(1), true)

        SenadaDatabase.Schema.migrate(driver, 4, 5)

        assertEquals(6L, SenadaDatabase.Schema.version)
        assertEquals(listOf(existingLocal), library.localTracks().first())
        assertEquals(listOf(track(1), existingLocal), library.favorites.first())

        library.recordImported(newlyImported)
        assertEquals(listOf(newlyImported, existingLocal), library.localTracks().first())
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

    @Test
    fun totalDurationSumsKnownPositiveTracksAcrossPages() = runTest {
        val library = library()
        val first = track(1).copy(durationMs = 120_000L)
        val second = track(2).copy(durationMs = 180_000L)
        val unknown = duet.copy(durationMs = null)

        library.setFavorite(first, true)
        library.setFavorite(unknown, true)
        library.setFavorite(second, true)
        assertEquals(300_000L, library.favoriteDurationMs.first())

        val playlistId = library.createPlaylist("Durasi", listOf(first, unknown, second))
        assertEquals(300_000L, library.playlist(playlistId, limit = 1).first()?.totalDurationMs)

        val unknownPlaylistId = library.createPlaylist("Tanpa durasi", listOf(unknown))
        assertNull(library.playlist(unknownPlaylistId).first()?.totalDurationMs)
    }

    private fun titlesOf(playlist: Playlist) = playlist.entries.map { it.track.title.removePrefix("Lagu ").toInt() }
}
