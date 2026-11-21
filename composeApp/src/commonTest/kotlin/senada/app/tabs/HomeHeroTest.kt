package senada.app.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import senada.library.HomeHero
import senada.library.PlaylistSummary
import senada.model.Track

class HomeHeroTest {
    @Test
    fun playlistCopyExplainsWhyItWasChosen() {
        val playlist = PlaylistSummary("local-pagi", "Pagi di Kota", "", 0, 2)

        assertEquals(
            HeroCopy("Playlist yang paling sering kamu pilih bulan ini", "Pagi di Kota", "Putar", "Lihat"),
            heroCopy(HomeHero.FromPlaylist(playlist, artworkUrl = null, plays = 3)),
        )
    }

    @Test
    fun trackCopyUsesTheTrackTitle() {
        val track = Track("yt:track:rintik", "Rintik", emptyList(), source = "ytmusic")

        assertEquals(
            HeroCopy("Paling sering kamu putar bulan ini", "Rintik", "Putar", "Lihat"),
            heroCopy(HomeHero.FromTrack(track, plays = 2)),
        )
    }

    @Test
    fun welcomeCopyOffersSearch() {
        assertEquals(
            HeroCopy("Selamat datang di Senada", "Mulai dengarkan", "Cari lagu", null),
            heroCopy(HomeHero.Welcome),
        )
    }
}
