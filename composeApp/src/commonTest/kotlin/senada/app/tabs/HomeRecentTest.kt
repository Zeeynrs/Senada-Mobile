package senada.app.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import senada.app.shell.AppDestination
import senada.catalog.youtube.ArtistDiscoverData
import senada.catalog.youtube.DiscoverFeed
import senada.catalog.youtube.DiscoverSeeds
import senada.catalog.youtube.DiscoverSource
import senada.catalog.youtube.RadioEntry
import senada.catalog.youtube.YOUTUBE_MUSIC_SOURCE
import senada.library.PlayedPlaylist
import senada.library.PlayedTrack
import senada.library.PlaylistSummary
import senada.library.RecentHomeItem
import senada.library.selectRecentHomeItems
import senada.model.Track

@OptIn(ExperimentalCoroutinesApi::class)
class HomeRecentTest {
    private fun song(id: String, title: String, artwork: String? = null) = Track(
        id = id,
        title = title,
        artists = listOf("Senada"),
        artworkUrl = artwork,
        source = YOUTUBE_MUSIC_SOURCE,
    )

    private fun playlist(id: String, name: String, count: Int) = PlaylistSummary(
        id = id,
        name = name,
        description = "",
        createdAtMs = 0L,
        trackCount = count,
    )

    @Test
    fun emptyInputsReturnNothingSoSectionStaysHidden() {
        assertEquals(emptyList(), selectRecentHomeItems(emptyList(), emptyList()))
        assertEquals(
            emptyList(),
            selectRecentHomeItems(
                tracks = emptyList(),
                playlists = listOf(PlayedPlaylist(playlist("local-empty", "Kosong", 0), artworkUrl = null, playedAtMs = 500L)),
            ),
        )
    }

    @Test
    fun mergesSongsAndPlaylistsNewestFirstAndDeduplicatesKeepingNewestTimestamp() {
        val hujan = song("yt:track:hujan", "Selepas Hujan", "https://art.invalid/hujan.jpg")
        val pagi = song("yt:track:pagi", "Sebelum Pagi", "https://art.invalid/pagi.jpg")
        val malam = song("yt:track:malam", "Malam Tenang")
        val plPagi = playlist("local-pagi", "Pagi di Kota", count = 4)
        val plSenja = playlist("local-senja", "Senja", count = 2)

        val selected = selectRecentHomeItems(
            tracks = listOf(
                PlayedTrack(hujan, playedAtMs = 100L),
                PlayedTrack(pagi, playedAtMs = 400L),
                // Lagu yang sama diputar lagi lebih baru: harus pindah ke depan tanpa duplikat.
                PlayedTrack(hujan, playedAtMs = 600L),
                PlayedTrack(malam, playedAtMs = 200L),
                // Entri lama setelah entri baru tetap tidak menimpa waktu terbaru.
                PlayedTrack(pagi, playedAtMs = 150L),
            ),
            playlists = listOf(
                PlayedPlaylist(plPagi, artworkUrl = "https://art.invalid/pl-pagi.jpg", playedAtMs = 300L),
                PlayedPlaylist(plPagi, artworkUrl = "https://art.invalid/pl-pagi-baru.jpg", playedAtMs = 500L),
                PlayedPlaylist(plSenja, artworkUrl = "   ", playedAtMs = 250L),
            ),
        )

        assertEquals(
            listOf(
                RecentHomeItem.Song(hujan, playedAtMs = 600L),
                RecentHomeItem.Playlist(plPagi, artworkUrl = "https://art.invalid/pl-pagi-baru.jpg", playedAtMs = 500L),
                RecentHomeItem.Song(pagi, playedAtMs = 400L),
                RecentHomeItem.Playlist(plSenja, artworkUrl = null, playedAtMs = 250L),
                RecentHomeItem.Song(malam, playedAtMs = 200L),
            ),
            selected,
        )
    }

    @Test
    fun excludesEmptyCollectionsAndRespectsLimitAndTieOrder() {
        val t1 = song("yt:track:1", "Satu")
        val t2 = song("yt:track:2", "Dua")
        val plAktif = playlist("local-aktif", "Aktif", count = 3)
        val plKosong = playlist("local-kosong", "Favorit Kosong", count = 0)
        val plDikosongkanLama = playlist("local-habis", "Dikosongkan", count = 2)
        val plDikosongkanBaru = playlist("local-habis", "Dikosongkan", count = 0)

        val selected = selectRecentHomeItems(
            tracks = listOf(
                PlayedTrack(song("", "Tanpa ID"), playedAtMs = 999L),
                PlayedTrack(t1, playedAtMs = 500L),
                PlayedTrack(t2, playedAtMs = 500L),
            ),
            playlists = listOf(
                PlayedPlaylist(plKosong, artworkUrl = "https://art.invalid/x.jpg", playedAtMs = 900L),
                PlayedPlaylist(plDikosongkanLama, artworkUrl = null, playedAtMs = 300L),
                PlayedPlaylist(plDikosongkanBaru, artworkUrl = null, playedAtMs = 800L),
                PlayedPlaylist(plAktif, artworkUrl = null, playedAtMs = 500L),
            ),
            limit = 2,
        )

        // Pada waktu yang persis sama (500), playlist didahulukan sebelum lagu, lalu dibatasi limit=2.
        assertEquals(
            listOf(
                RecentHomeItem.Playlist(plAktif, artworkUrl = null, playedAtMs = 500L),
                RecentHomeItem.Song(t1, playedAtMs = 500L),
            ),
            selected,
        )
    }

    @Test
    fun shouldRefreshDiscoverTriggersOnlyWhenHomeOrEmptySearchIsVisible() {
        assertTrue(
            shouldRefreshDiscover(
                screenVisible = true,
                destination = AppDestination.Home,
                hasCatalogRoute = false,
                mostPlayedOpen = false,
                listeningStatsOpen = false,
                searchQueryBlank = false,
            ),
        )
        assertFalse(
            shouldRefreshDiscover(
                screenVisible = true,
                destination = AppDestination.Home,
                hasCatalogRoute = false,
                mostPlayedOpen = true,
                listeningStatsOpen = false,
                searchQueryBlank = true,
            ),
        )
        assertFalse(
            shouldRefreshDiscover(
                screenVisible = true,
                destination = AppDestination.Home,
                hasCatalogRoute = false,
                mostPlayedOpen = false,
                listeningStatsOpen = true,
                searchQueryBlank = true,
            ),
        )
        assertTrue(
            shouldRefreshDiscover(
                screenVisible = true,
                destination = AppDestination.Search,
                hasCatalogRoute = false,
                mostPlayedOpen = false,
                listeningStatsOpen = false,
                searchQueryBlank = true,
            ),
        )
        assertFalse(
            shouldRefreshDiscover(
                screenVisible = true,
                destination = AppDestination.Search,
                hasCatalogRoute = false,
                mostPlayedOpen = false,
                listeningStatsOpen = false,
                searchQueryBlank = false,
            ),
        )
        assertFalse(
            shouldRefreshDiscover(
                screenVisible = true,
                destination = AppDestination.Home,
                hasCatalogRoute = true,
                mostPlayedOpen = false,
                listeningStatsOpen = false,
                searchQueryBlank = true,
            ),
        )
        assertFalse(
            shouldRefreshDiscover(
                screenVisible = false,
                destination = AppDestination.Home,
                hasCatalogRoute = false,
                mostPlayedOpen = false,
                listeningStatsOpen = false,
                searchQueryBlank = true,
            ),
        )
        assertFalse(
            shouldRefreshDiscover(
                screenVisible = true,
                destination = AppDestination.Library,
                hasCatalogRoute = false,
                mostPlayedOpen = false,
                listeningStatsOpen = false,
                searchQueryBlank = true,
            ),
        )
    }

    @Test
    fun sharedDiscoverControllerDoesNotRefetchWhenSwitchingBetweenHomeAndSearch() = runTest {
        var homeCalls = 0
        val source = object : DiscoverSource {
            override suspend fun radio(videoId: String): List<RadioEntry> = emptyList()
            override suspend fun artistRelated(channelId: String): ArtistDiscoverData = ArtistDiscoverData()
            override suspend fun findArtistId(name: String): String? = null
            override suspend fun homeFeed(saveData: Boolean): DiscoverFeed {
                homeCalls++
                return DiscoverFeed(tracks = listOf(song("yt:track:rec1", "Rekomendasi 1")))
            }
        }
        var now = 10_000L
        val controller = DiscoverController(source, this, now = { now }, ttlMs = DISCOVER_TTL_MS, workContext = coroutineContext)

        // Hemat data menunda di Beranda maupun Cari tanpa menyentuh jaringan.
        controller.refreshIfNeeded(DiscoverSeeds(), saveData = true, force = false)
        controller.refreshIfNeeded(DiscoverSeeds(), saveData = true, force = false)
        advanceUntilIdle()
        assertTrue(controller.state.deferredByDataSaver)
        assertEquals(0, homeCalls)

        // Beranda memuat sekali (jaringan normal), lalu pindah ke tab Cari tidak memuat ulang.
        controller.refreshIfNeeded(DiscoverSeeds(), saveData = false, force = false)
        advanceUntilIdle()
        assertEquals(1, homeCalls)
        assertFalse(controller.state.deferredByDataSaver)

        now += 5_000L
        controller.refreshIfNeeded(DiscoverSeeds(), saveData = false, force = false)
        advanceUntilIdle()
        assertEquals(1, homeCalls)
    }
}
