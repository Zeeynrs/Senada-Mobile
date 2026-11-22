package senada.app.shell

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppNavigatorTest {
    @Test
    fun backClosesNowPlayingFirst() {
        val navigator = AppNavigator()
        navigator.select(AppDestination.Library)
        navigator.openPlaylist("p")
        navigator.openNowPlaying()

        assertTrue(navigator.back())
        assertFalse(navigator.nowPlayingOpen)
        assertEquals("p", navigator.openedPlaylistId)
    }

    @Test
    fun backThenClosesPlaylistThenGoesHome() {
        val navigator = AppNavigator()
        navigator.select(AppDestination.Library)
        navigator.openPlaylist("p")

        assertTrue(navigator.back())
        assertNull(navigator.openedPlaylistId)
        assertEquals(AppDestination.Library, navigator.destination)

        assertTrue(navigator.back())
        assertEquals(AppDestination.Home, navigator.destination)

        assertFalse(navigator.back())
        assertFalse(navigator.canGoBack)
    }

    @Test
    fun backClosesMostPlayedBeforeLeavingHome() {
        val navigator = AppNavigator()
        navigator.openMostPlayed()

        assertTrue(navigator.canGoBack)
        assertTrue(navigator.back())
        assertFalse(navigator.mostPlayedOpen)
        assertFalse(navigator.back())
    }

    @Test
    fun selectingATabClosesDetailScreens() {
        val navigator = AppNavigator()
        navigator.openPlaylist("p")
        navigator.openMostPlayed()
        navigator.openArtist("UC1", "Tulus")

        navigator.select(AppDestination.Search)

        assertNull(navigator.openedPlaylistId)
        assertFalse(navigator.mostPlayedOpen)
        assertNull(navigator.currentCatalogRoute)
        assertEquals(AppDestination.Search, navigator.destination)
    }

    @Test
    fun backPopsAlbumThenArtistThenReturnsToSearchThenHome() {
        val navigator = AppNavigator()
        navigator.select(AppDestination.Search)
        navigator.openArtist("UC_tulus", "Tulus", "https://example.invalid/a")
        assertEquals("Cari", navigator.previousCatalogLabel())

        navigator.openAlbum("MPREb_manusia", "Manusia", "Album · 2022", "https://example.invalid/m")
        assertEquals("Tulus", navigator.previousCatalogLabel())
        assertEquals(CatalogRoute.Album("MPREb_manusia", "Manusia", "Album · 2022", "https://example.invalid/m"), navigator.currentCatalogRoute)

        assertTrue(navigator.back())
        assertEquals(CatalogRoute.Artist("UC_tulus", "Tulus", "https://example.invalid/a"), navigator.currentCatalogRoute)
        assertEquals("Cari", navigator.previousCatalogLabel())

        assertTrue(navigator.back())
        assertNull(navigator.currentCatalogRoute)
        assertEquals(AppDestination.Search, navigator.destination)

        assertTrue(navigator.back())
        assertEquals(AppDestination.Home, navigator.destination)
        assertFalse(navigator.back())
    }

    @Test
    fun backClosesNowPlayingBeforePoppingCatalogPages() {
        val navigator = AppNavigator()
        navigator.select(AppDestination.Search)
        navigator.openArtist("UC_tulus", "Tulus")
        navigator.openAlbum("MPREb_manusia", "Manusia")
        navigator.openNowPlaying()

        assertTrue(navigator.back())
        assertFalse(navigator.nowPlayingOpen)
        assertEquals("MPREb_manusia", navigator.currentCatalogRoute?.id)

        assertTrue(navigator.back())
        assertEquals("UC_tulus", navigator.currentCatalogRoute?.id)
    }

    @Test
    fun duplicateTopRouteAndBlankIdsAreIgnored() {
        val navigator = AppNavigator()
        navigator.select(AppDestination.Search)
        navigator.openArtist("   ", "Kosong")
        navigator.openAlbum("", "Kosong")
        assertTrue(navigator.catalogStack.isEmpty())

        navigator.openArtist("UC1", "Tulus")
        navigator.openArtist("UC1", "Tulus")
        assertEquals(1, navigator.catalogStack.size)

        navigator.openAlbum("MPREb_1", "Manusia")
        navigator.openAlbum("MPREb_1", "Manusia")
        assertEquals(2, navigator.catalogStack.size)
    }

    @Test
    fun saverRestoresCatalogBackStack() {
        val navigator = AppNavigator()
        navigator.select(AppDestination.Search)
        navigator.openArtist("UC_tulus", "Tulus", "https://example.invalid/artist")
        navigator.openAlbum("MPREb_manusia", "Manusia", "2022", "https://example.invalid/album")
        navigator.openCatalogPlaylist("PL_sore", "Chill Sore", "YouTube Music · 20 lagu", "https://example.invalid/pl")
        navigator.openNowPlaying()

        val saved = with(AppNavigator.Saver) { SaverScope { true }.save(navigator) }
        val restored = AppNavigator.Saver.restore(assertNotNull(saved))
        assertNotNull(restored)

        assertEquals(AppDestination.Search, restored.destination)
        assertTrue(restored.nowPlayingOpen)
        assertEquals(navigator.catalogStack, restored.catalogStack)
    }

    @Test
    fun catalogPlaylistRouteHandlesBackStackDuplicateGuardAndPrefix() {
        val navigator = AppNavigator()
        navigator.select(AppDestination.Search)
        navigator.openCatalogPlaylist("   ", "Kosong")
        assertTrue(navigator.catalogStack.isEmpty())

        navigator.openCatalogPlaylist("VLPL_sore", "Chill Sore", "Pemilik · 10 lagu", "https://example.invalid/pl")
        navigator.openCatalogPlaylist("PL_sore", "Chill Sore")
        assertEquals(1, navigator.catalogStack.size)
        assertEquals(
            CatalogRoute.Playlist("PL_sore", "Chill Sore", "Pemilik · 10 lagu", "https://example.invalid/pl"),
            navigator.currentCatalogRoute,
        )
        assertEquals("Cari", navigator.previousCatalogLabel())

        assertTrue(navigator.back())
        assertNull(navigator.currentCatalogRoute)
        assertEquals(AppDestination.Search, navigator.destination)
    }
}
