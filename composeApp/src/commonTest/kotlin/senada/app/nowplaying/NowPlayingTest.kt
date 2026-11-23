package senada.app.nowplaying

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import senada.app.contrastRatio

class NowPlayingTest {
    @Test
    fun sourceLabels() {
        assertEquals("Diputar dari Pencarian", PlaySource.Search.playerLabel())
        assertEquals("Diputar dari playlist Senja", PlaySource.Playlist("Senja").playerLabel())
        assertEquals("Diputar dari Antrean", null.playerLabel())
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

    @Test
    fun brightCoversKeepSourceLegible() {
        for (cover in listOf(Color.White, Color.Yellow, Color.Cyan, Color(0xFFB4521E), null)) {
            assertTrue(sourceLabelContrast(playerGlow(cover)) >= 4.5f, "Cover: $cover")
        }
        assertEquals(Color(0xFFB4521E), playerGlow(null))
    }

    @Test
    fun backdropCacheSizeDownsamplesSmoothlyAndHandlesEmptyFrames() {
        assertEquals(IntSize.Zero, backdropCacheSize(Size.Zero))
        assertEquals(IntSize(270, 600), backdropCacheSize(Size(1080f, 2400f)))
        assertEquals(IntSize(200, 400), backdropCacheSize(Size(200f, 400f)))
    }
}
