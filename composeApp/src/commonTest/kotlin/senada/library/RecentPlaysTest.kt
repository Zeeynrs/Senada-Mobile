package senada.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import senada.db.SenadaDatabase
import senada.model.Track

class RecentPlaysTest {
    private val driver = testLibraryDriver()
    private val database = SenadaDatabase(driver)
    private var clock = 100L
    private var playlistSeq = 0

    private val first = Track(
        id = "yt:track:a",
        title = "Pertama",
        artists = listOf("Senada"),
        artworkUrl = null,
        source = "ytmusic",
    )
    private val second = Track(
        id = "yt:track:b",
        title = "Kedua",
        artists = listOf("Senada"),
        artworkUrl = "https://artwork.example.invalid/kedua.jpg",
        source = "ytmusic",
    )

    private fun TestScope.library() = LibraryRepository(
        database = database,
        dispatcher = StandardTestDispatcher(testScheduler),
        now = { clock },
        newPlaylistId = { "local-recent-${++playlistSeq}" },
    )

    @Test
    fun recentHomeItemsIsEmptyBeforeAnyPlays() = runTest {
        assertEquals(emptyList(), library().recentHomeItems().first())
    }

    @Test
    fun recentHomeItemsCombinesTracksAndPlayedPlaylistsNewestFirstAndHidesEmptiedPlaylists() = runTest {
        val library = library()
        val playlistId = library.createPlaylist("Santai", listOf(first, second))

        clock = 1_000L
        library.recordPlay(first)

        clock = 2_000L
        library.recordPlaylistPlay(playlistId)

        clock = 3_000L
        library.recordPlay(second)

        assertEquals(
            listOf(
                RecentHomeItem.Song(second, playedAtMs = 3_000L),
                RecentHomeItem.Playlist(
                    summary = PlaylistSummary(playlistId, "Santai", "", 100L, 2),
                    artworkUrl = second.artworkUrl,
                    playedAtMs = 2_000L,
                ),
                RecentHomeItem.Song(first, playedAtMs = 1_000L),
            ),
            library.recentHomeItems().first(),
        )

        // Memutar ulang lagu lama memindahkannya ke depan tanpa menggandakan entri.
        clock = 4_000L
        library.recordPlay(first)
        assertEquals(
            listOf("recent-track-${first.id}", "recent-track-${second.id}", "recent-playlist-$playlistId"),
            library.recentHomeItems().first().map { it.key },
        )

        // Playlist yang dikosongkan tidak boleh lagi muncul di "Baru diputar".
        val opened = library.playlist(playlistId).first()!!
        opened.entries.forEach { library.removeFromPlaylist(playlistId, it.itemId) }
        assertEquals(
            listOf(
                RecentHomeItem.Song(first, playedAtMs = 4_000L),
                RecentHomeItem.Song(second, playedAtMs = 3_000L),
            ),
            library.recentHomeItems().first(),
        )
    }
}
