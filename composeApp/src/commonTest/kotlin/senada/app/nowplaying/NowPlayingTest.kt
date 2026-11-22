package senada.app.nowplaying

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import senada.app.contrastRatio

class NowPlayingTest {
    @Test
    fun sourceLabels() {
        assertEquals("Dari Favorit", PlaySource.Favorites.label())
        assertEquals("Dari playlist Senja", PlaySource.Playlist("Senja").label())
        assertEquals("Dari Riwayat", PlaySource.History.label())
        assertEquals("Dari File impor", PlaySource.Imported.label())
        assertEquals("Dari Paling sering diputar", PlaySource.MostPlayed.label())
        assertEquals("Dari artis Tulus", PlaySource.Artist("Tulus").label())
        assertEquals("Dari album Manusia", PlaySource.Album("Manusia").label())
        assertNull(null.label())
    }

    @Test
    fun alwaysDarkEvenInLightTheme() {
        assertEquals(Color(0xFF0B0C0E), nowPlayingColorScheme(null).background)
    }

    @Test
    fun coverAccentStaysReadableOnDark() {
        val scheme = nowPlayingColorScheme(Color(0xFF1A1A40))
        assertTrue(contrastRatio(scheme.primary, scheme.background) >= 4.5f)
    }
}
