package senada.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import senada.app.ArtistOpenController
import senada.app.shell.AppDestination
import senada.app.shell.AppNavigator
import senada.catalog.youtube.ALBUM_PAGE_MANUSIA
import senada.catalog.youtube.ARTIST_PAGE_TULUS
import senada.catalog.youtube.SEARCH_PAGE_1
import senada.catalog.youtube.SEARCH_PAGE_2
import senada.catalog.youtube.YOUTUBE_MUSIC_SOURCE
import senada.catalog.youtube.parseAlbumPage
import senada.catalog.youtube.parseArtistPage
import senada.catalog.youtube.parseSearchPage
import senada.local.LOCAL_FILE_SOURCE
import senada.model.Track

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistMatcherTest {
    private fun artist(id: String, name: String) = ArtistSummary(id = id, name = name, subtitle = "Artis")

    private class FakeBrowser(
        private val onSearchArtists: (String) -> ArtistPage = { ArtistPage(emptyList()) },
    ) : CatalogBrowser {
        val searchQueries = mutableListOf<String>()

        override suspend fun search(query: String, pageToken: String?) = TrackPage(emptyList())
        override suspend fun searchArtists(query: String, pageToken: String?): ArtistPage {
            searchQueries += query
            return onSearchArtists(query)
        }
        override suspend fun searchAlbums(query: String, pageToken: String?) = AlbumPage(emptyList())
        override suspend fun searchPlaylists(query: String, pageToken: String?) = PlaylistPage(emptyList())
        override suspend fun artist(id: String) = ArtistDetail(id, id)
        override suspend fun album(id: String) = AlbumDetail(id, id)
        override suspend fun playlist(id: String) = PlaylistDetail(id, id)
    }

    @Test
    fun exactAndCaseInsensitiveMatchWinsOverLongerPrefix() {
        val candidates = listOf(artist("UC_m", "Tulus M"), artist("UC_t", "TULUS"))
        assertEquals("UC_t", matchArtist("Tulus", candidates)?.id)
        assertEquals("UC_t", matchArtist("tulus", candidates)?.id)
    }

    @Test
    fun diacriticsNfcAndNfdMatchCleanly() {
        assertEquals("UC_b", matchArtist("Beyoncé", listOf(artist("UC_x", "Other"), artist("UC_b", "Beyonce")))?.id)
        assertEquals("UC_b", matchArtist("Beyonce", listOf(artist("UC_b", "Beyoncé")))?.id)
        assertEquals("UC_b", matchArtist("Beyonce\u0301", listOf(artist("UC_b", "Beyonce")))?.id)
        assertEquals("UC_s", matchArtist("Sigur Rós", listOf(artist("UC_s", "Sigur Ros")))?.id)
        assertEquals("UC_r", matchArtist("Röyksopp", listOf(artist("UC_r", "Royksopp")))?.id)
        assertEquals("UC_c", matchArtist("Café Tacvba", listOf(artist("UC_c", "Cafe Tacvba")))?.id)
    }

    @Test
    fun featLeftoversAreStrippedBeforeMatching() {
        val candidates = listOf(artist("UC_t", "Tulus"), artist("UC_y", "Yura Yunita"))
        assertEquals("UC_t", matchArtist("Tulus feat. Yura Yunita", candidates)?.id)
        assertEquals("UC_t", matchArtist("Tulus (feat. Yura Yunita)", candidates)?.id)
        assertEquals("UC_t", matchArtist("Tulus ft. Yura", candidates)?.id)
        assertEquals("UC_y", matchArtist("feat. Yura Yunita", candidates)?.id)
        assertEquals("UC_y", matchArtist("featuring Yura Yunita", candidates)?.id)
        assertNull(matchArtist("feat.", candidates))
    }

    @Test
    fun noMatchReturnsNullInsteadOfWrongArtist() {
        assertNull(matchArtist("Tulus", listOf(artist("UC_m", "Tulus M"), artist("UC_o", "Tulus Band"))))
        assertNull(matchArtist("Artis Tidak Ada", listOf(artist("UC_1", "Tulus"))))
        assertNull(matchArtist("   ", listOf(artist("UC_1", "Tulus"))))
        assertNull(matchArtist("Tulus", emptyList()))
    }

    @Test
    fun artistChoicesPreserveCommasInsideSingleNameAndSplitFeat() {
        val collab = Track(
            id = "yt:track:1",
            title = "Lagu",
            artists = listOf("Tyler, The Creator", "Earth, Wind & Fire", "Tulus feat. Yura Yunita"),
            source = YOUTUBE_MUSIC_SOURCE,
        )
        assertEquals(
            listOf("Tyler, The Creator", "Earth, Wind & Fire", "Tulus", "Yura Yunita"),
            artistChoicesFor(collab),
        )
    }

    @Test
    fun localImportedFilesAndDemoTracksCannotOpenArtist() {
        val local = Track("local:abc.mp3", "Rekaman", listOf("Tulus"), source = LOCAL_FILE_SOURCE)
        val demo = Track("demo:track:hujan", "Selepas Hujan", listOf("Senada"), source = "demo")
        val online = Track("yt:track:abc", "Teh Hijau", listOf("Tulus"), source = YOUTUBE_MUSIC_SOURCE)
        val noArtist = Track("yt:track:xyz", "Tanpa Artis", emptyList(), source = YOUTUBE_MUSIC_SOURCE)

        assertFalse(canOpenArtist(local))
        assertFalse(canOpenArtist(demo))
        assertFalse(canOpenArtist(noArtist))
        assertTrue(canOpenArtist(online))
    }

    @Test
    fun resolverUsesKnownIdDirectlyAndFallsBackToSearchAndCaches() = runTest {
        val index = ArtistIdIndex()
        val trackKnown = Track("yt:track:5F28ye50-Kc", "Teh Hijau", listOf("Tulus"), source = YOUTUBE_MUSIC_SOURCE)
        val trackUnknown = Track("yt:track:other", "Cinta", listOf("Beyoncé"), source = YOUTUBE_MUSIC_SOURCE)
        index.record(trackKnown.id, "Tulus", "UC_DHlXllTSMB8pTC38_leFg")

        val browser = FakeBrowser { query ->
            when (query) {
                "Beyoncé" -> ArtistPage(listOf(artist("UC_bey", "Beyonce")))
                else -> ArtistPage(listOf(artist("UC_wrong", "Wrong Name")))
            }
        }
        val resolver = ArtistResolver(browser, index)

        val direct = resolver.resolve(trackKnown, "Tulus")
        assertEquals(ArtistResolveResult.Found("UC_DHlXllTSMB8pTC38_leFg", "Tulus"), direct)
        assertEquals(emptyList(), browser.searchQueries)

        val searched = resolver.resolve(trackUnknown, "Beyoncé")
        assertEquals(ArtistResolveResult.Found("UC_bey", "Beyonce"), searched)
        assertEquals(listOf("Beyoncé"), browser.searchQueries)

        // Panggilan kedua memakai cache nama tanpa pencarian ulang.
        assertEquals(ArtistResolveResult.Found("UC_bey", "Beyonce"), resolver.resolve(trackUnknown, "beyonce"))
        assertEquals(listOf("Beyoncé"), browser.searchQueries)

        val missingTrack = Track("yt:track:miss", "X", listOf("Artis Misterius"), source = YOUTUBE_MUSIC_SOURCE)
        assertEquals(ArtistResolveResult.NotFound, resolver.resolve(missingTrack, "Artis Misterius"))
    }

    @Test
    fun parsersPopulateArtistIdIndexFromSearchArtistAndAlbumFixtures() {
        val index = ArtistIdIndex()
        val search1 = parseSearchPage(Json.parseToJsonElement(SEARCH_PAGE_1).jsonObject, index::record)
        val search2 = parseSearchPage(Json.parseToJsonElement(SEARCH_PAGE_2).jsonObject, index::record)
        val artistPage = parseArtistPage("UC_DHlXllTSMB8pTC38_leFg", Json.parseToJsonElement(ARTIST_PAGE_TULUS).jsonObject, index::record)
        val albumPage = parseAlbumPage("MPREb_7FNmIeIV9kf", Json.parseToJsonElement(ALBUM_PAGE_MANUSIA).jsonObject, index::record)

        assertEquals("UC_DHlXllTSMB8pTC38_leFg", index.channelIdFor(search1.items.first().id, "Tulus"))
        assertEquals("UCGx0RgwyfHLNEFUuXGqL2FQ", index.channelIdFor(search2.items.last().id, "Revina Alvira"))
        assertEquals("UCm9rH8suELdHoi4r8uhYEnQ", index.channelIdFor(search2.items.last().id, "Rian"))
        assertEquals("UC_DHlXllTSMB8pTC38_leFg", index.channelIdFor(artistPage.topSongs.first().id, "Tulus"))
        assertEquals("UC_DHlXllTSMB8pTC38_leFg", index.channelIdFor(albumPage.tracks.first().id, "Tulus"))
    }

    @Test
    fun controllerClosesNowPlayingAndOpensArtistOrShowsNotFoundNotice() = runTest {
        val index = ArtistIdIndex()
        val browser = FakeBrowser { query ->
            if (query == "Tulus") ArtistPage(listOf(artist("UC_tulus", "Tulus")))
            else ArtistPage(listOf(artist("UC_other", "Tulus M")))
        }
        val resolver = ArtistResolver(browser, index)
        val navigator = AppNavigator()
        navigator.select(AppDestination.Library)
        navigator.openNowPlaying()

        val controller = ArtistOpenController(resolver, navigator, this, noticeDurationMs = 0)

        // Tidak cocok: Sedang diputar tetap terbuka dan pesan singkat tampil.
        val unknown = Track("yt:track:u", "Lagu", listOf("Bukan Artis"), source = YOUTUBE_MUSIC_SOURCE)
        controller.open(unknown, "Bukan Artis")
        advanceUntilIdle()
        assertTrue(navigator.nowPlayingOpen)
        assertNull(navigator.currentCatalogRoute)
        assertEquals("Artis tidak ditemukan", controller.message)

        // Cocok: Sedang diputar ditutup dan halaman artis dibuka.
        val known = Track("yt:track:k", "Teh Hijau", listOf("Tulus"), source = YOUTUBE_MUSIC_SOURCE)
        controller.open(known, "Tulus")
        advanceUntilIdle()
        assertFalse(navigator.nowPlayingOpen)
        assertEquals("UC_tulus", navigator.currentCatalogRoute?.id)
        assertNull(controller.message)
    }
}
