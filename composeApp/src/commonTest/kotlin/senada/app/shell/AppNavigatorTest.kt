package senada.app.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

        navigator.select(AppDestination.Search)

        assertNull(navigator.openedPlaylistId)
        assertFalse(navigator.mostPlayedOpen)
        assertEquals(AppDestination.Search, navigator.destination)
    }
}
