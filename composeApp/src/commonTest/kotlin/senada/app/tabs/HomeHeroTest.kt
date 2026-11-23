package senada.app.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import senada.app.shell.profileInitials
import senada.library.HomeHero
import senada.library.PlaylistSummary
import senada.model.Track

class HomeHeroTest {
    @Test
    fun playlistCopyExplainsWhyItWasChosen() {
        val playlist = PlaylistSummary("local-pagi", "Pagi di Kota", "", 0, 2)

        assertEquals(
            HeroCopy("Playlist yang sering kamu putar", "Pagi di Kota", "Putar", "Lihat", artistName = null),
            heroCopy(HomeHero.FromPlaylist(playlist, artworkUrl = null, plays = 3)),
        )
    }

    @Test
    fun trackCopyIncludesArtistNameWhenAvailable() {
        val track = Track("yt:track:rintik", "Rintik", listOf("Senada", "Nadia"), source = "ytmusic")

        assertEquals(
            HeroCopy("Karena kamu sering memutar", "Rintik", "Putar", "Lihat", artistName = "Senada, Nadia"),
            heroCopy(HomeHero.FromTrack(track, plays = 2)),
        )
    }

    @Test
    fun trackCopyOmitsBlankArtists() {
        val track = Track("yt:track:rintik", "Rintik", emptyList(), source = "ytmusic")

        assertEquals(
            HeroCopy("Karena kamu sering memutar", "Rintik", "Putar", "Lihat", artistName = null),
            heroCopy(HomeHero.FromTrack(track, plays = 2)),
        )
    }

    @Test
    fun welcomeCopyOffersSearch() {
        assertEquals(
            HeroCopy("Selamat datang di Senada", "Mulai dengarkan", "Cari lagu", null, artistName = null),
            heroCopy(HomeHero.Welcome),
        )
    }

    @Test
    fun heroHeightMatchesMockupAndScalesAcrossScreens() {
        // Kanvas mockup 390×844 dp -> persis 440 dp.
        assertEquals(440f, heroHeightDp(390f, 844f), absoluteTolerance = 0.01f)
        // Pixel 6 (411.4×914.3 dp) -> skalakan mengikuti layar (~464 dp, ~51% tinggi).
        val pixel6 = heroHeightDp(411.43f, 914.29f)
        assertTrue(pixel6 in 460f..480f, "Tinggi hero Pixel 6 seharusnya ~464 dp, dapat $pixel6")
        // Ponsel ringkas 360×780 dp -> proporsional (~406 dp, ~52% tinggi).
        val compact = heroHeightDp(360f, 780f)
        assertTrue(compact in 400f..415f, "Tinggi hero 360×780 seharusnya ~406 dp, dapat $compact")
        // Tablet besar dibatasi maksimal 560 dp.
        assertEquals(560f, heroHeightDp(1024f, 1366f), absoluteTolerance = 0.01f)
        // Layar sangat pendek tetap punya batas minimal yang aman.
        assertEquals(HERO_MIN_HEIGHT_DP, heroHeightDp(844f, 390f), absoluteTolerance = 0.01f)
        // Belum terukur -> kembali ke tinggi baku 440 dp.
        assertEquals(440f, heroHeightDp(0f, 0f), absoluteTolerance = 0.01f)
    }

    @Test
    fun profileInitialsTakesUpToTwoUppercaseLetters() {
        assertEquals("", profileInitials(""))
        assertEquals("", profileInitials("   "))
        assertEquals("A", profileInitials("Adi"))
        assertEquals("AP", profileInitials("adi pratama"))
        assertEquals("BS", profileInitials("  budi   santoso  wati "))
    }
}
