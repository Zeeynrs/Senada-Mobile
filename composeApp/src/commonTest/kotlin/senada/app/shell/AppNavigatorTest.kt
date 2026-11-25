package senada.app.shell

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import senada.catalog.MoodChip

class AppNavigatorTest {
    @Test
    fun restoresLegacySettingsTabAndStatsFlag() {
        val legacy = assertNotNull(AppNavigator.Saver.restore(listOf("Settings", false, null, false)))
        assertEquals(AppDestination.Profile, legacy.destination)
        assertNull(legacy.currentProfileRoute)
        val stats = assertNotNull(AppNavigator.Saver.restore(listOf("Home", false, null, false, true)))
        assertEquals(AppDestination.Profile, stats.destination)
        assertEquals(ProfileRoute.Stats, stats.currentProfileRoute)
    }

    @Test
    fun profileStackRestoresPopsAndClearsOnTabChange() {
        val navigator = AppNavigator()
        navigator.openProfileRoute(ProfileRoute.AudioLab)
        navigator.openProfileRoute(ProfileRoute.Equalizer)
        val saved = with(AppNavigator.Saver) { SaverScope { true }.save(navigator) }
        val restored = assertNotNull(AppNavigator.Saver.restore(assertNotNull(saved)))
        assertEquals(ProfileRoute.Equalizer, restored.currentProfileRoute)
        assertTrue(restored.back())
        assertEquals(ProfileRoute.AudioLab, restored.currentProfileRoute)
        restored.select(AppDestination.Library)
        assertTrue(restored.profileStack.isEmpty())
    }

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
    fun backClosesMostPlayedBeforeLeavingProfile() {
        val navigator = AppNavigator()
        navigator.openProfileRoute(ProfileRoute.MostPlayed)

        assertTrue(navigator.canGoBack)
        assertTrue(navigator.back())
        assertNull(navigator.currentProfileRoute)
        assertEquals(AppDestination.Profile, navigator.destination)
        assertTrue(navigator.back())
        assertFalse(navigator.back())
    }

    @Test
    fun listeningStatsStaysOpenUnderNowPlayingAndClosesWhenChangingTab() {
        val navigator = AppNavigator()
        navigator.openProfileRoute(ProfileRoute.Stats)
        navigator.openNowPlaying()
        assertTrue(navigator.back())
        assertEquals(ProfileRoute.Stats, navigator.currentProfileRoute)
        assertTrue(navigator.back())
        assertNull(navigator.currentProfileRoute)
        assertEquals(AppDestination.Profile, navigator.destination)
        assertTrue(navigator.back())
        assertFalse(navigator.canGoBack)

        navigator.openProfileRoute(ProfileRoute.Stats)
        navigator.select(AppDestination.Search)
        assertNull(navigator.currentProfileRoute)
    }

    @Test
    fun selectingATabClosesDetailScreens() {
        val navigator = AppNavigator()
        navigator.openPlaylist("p")
        navigator.openProfileRoute(ProfileRoute.MostPlayed)
        navigator.openArtist("UC1", "Tulus")

        navigator.select(AppDestination.Search)

        assertNull(navigator.openedPlaylistId)
        assertNull(navigator.currentProfileRoute)
        assertNull(navigator.currentCatalogRoute)
        assertEquals(AppDestination.Search, navigator.destination)
    }

    @Test
    fun backPopsAlbumThenArtistThenReturnsToSearchThenHome() {
        val navigator = AppNavigator()
        navigator.select(AppDestination.Search)
        navigator.openArtist("UC_tulus", "Tulus", "https://example.invalid/a")
        assertEquals("Search", navigator.previousCatalogLabel())

        navigator.openAlbum("MPREb_manusia", "Manusia", "Album · 2022", "https://example.invalid/m")
        assertEquals("Tulus", navigator.previousCatalogLabel())
        assertEquals(CatalogRoute.Album("MPREb_manusia", "Manusia", "Album · 2022", "https://example.invalid/m"), navigator.currentCatalogRoute)

        assertTrue(navigator.back())
        assertEquals(CatalogRoute.Artist("UC_tulus", "Tulus", "https://example.invalid/a"), navigator.currentCatalogRoute)
        assertEquals("Search", navigator.previousCatalogLabel())

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

        navigator.openNowPlaying()
        navigator.openCatalogPlaylist("VLPL_sore", "Chill Sore", "Pemilik · 10 lagu", "https://example.invalid/pl")
        assertFalse(navigator.nowPlayingOpen)
        navigator.openCatalogPlaylist("PL_sore", "Chill Sore")
        assertEquals(1, navigator.catalogStack.size)
        assertEquals(
            CatalogRoute.Playlist("PL_sore", "Chill Sore", "Pemilik · 10 lagu", "https://example.invalid/pl"),
            navigator.currentCatalogRoute,
        )
        assertEquals("Search", navigator.previousCatalogLabel())

        assertTrue(navigator.back())
        assertNull(navigator.currentCatalogRoute)
        assertEquals(AppDestination.Search, navigator.destination)
    }

    @Test
    fun saverRestoresStatsBeneathCatalogAndAcceptsOlderSavedState() {
        val navigator = AppNavigator()
        navigator.openProfileRoute(ProfileRoute.Stats)
        navigator.openArtist("UC_tulus", "Tulus")
        navigator.openCatalogPlaylist("PL_sore", "Chill Sore", "YouTube Music · 20 lagu", "https://example.invalid/pl")
        val saved = with(AppNavigator.Saver) { SaverScope { true }.save(navigator) }
        val restored = assertNotNull(AppNavigator.Saver.restore(assertNotNull(saved)))
        assertEquals(ProfileRoute.Stats, restored.currentProfileRoute)
        assertEquals(
            CatalogRoute.Playlist("PL_sore", "Chill Sore", "YouTube Music · 20 lagu", "https://example.invalid/pl"),
            restored.currentCatalogRoute,
        )
        assertTrue(restored.back())
        assertEquals("UC_tulus", restored.currentCatalogRoute?.id)
        assertTrue(restored.back())
        assertEquals(ProfileRoute.Stats, restored.currentProfileRoute)
        assertNull(restored.currentCatalogRoute)

        val old = assertNotNull(AppNavigator.Saver.restore(listOf("Home", false, null, true)))
        assertEquals(ProfileRoute.MostPlayed, old.currentProfileRoute)
    }

    @Test
    fun saverAcceptsPlaylistFromBeforeListeningStatsWasAdded() {
        val saved = listOf(
            "Search", false, null, false,
            "playlist", "PL_lama", "Playlist lama", "10 lagu", "https://example.invalid/lama",
        )
        val restored = assertNotNull(AppNavigator.Saver.restore(saved))
        assertEquals(
            CatalogRoute.Playlist("PL_lama", "Playlist lama", "10 lagu", "https://example.invalid/lama"),
            restored.currentCatalogRoute,
        )
        assertNull(restored.currentProfileRoute)
    }

    @Test
    fun openArtistFromNowPlayingClosesNowPlayingAndPushesRoute() {
        val navigator = AppNavigator()
        navigator.select(AppDestination.Library)
        navigator.openPlaylist("p1")
        navigator.openNowPlaying()

        navigator.openArtist("UC_tulus", "Tulus")

        assertFalse(navigator.nowPlayingOpen)
        assertEquals(CatalogRoute.Artist("UC_tulus", "Tulus"), navigator.currentCatalogRoute)
        assertEquals("Library", navigator.previousCatalogLabel())

        assertTrue(navigator.back())
        assertNull(navigator.currentCatalogRoute)
        assertEquals("p1", navigator.openedPlaylistId)
    }

    @Test
    fun openArtistFromNowPlayingOverMostPlayedReturnsToMostPlayedOnBack() {
        val navigator = AppNavigator()
        navigator.openProfileRoute(ProfileRoute.MostPlayed)
        navigator.openNowPlaying()

        navigator.openArtist("UC_tulus", "Tulus")

        assertFalse(navigator.nowPlayingOpen)
        assertEquals("Most played", navigator.previousCatalogLabel())
        assertEquals("Paling sering diputar", navigator.previousCatalogLabel(senada.app.AppLanguage.Indonesian))
        assertTrue(navigator.back())
        assertNull(navigator.currentCatalogRoute)
        assertEquals(ProfileRoute.MostPlayed, navigator.currentProfileRoute)
        assertTrue(navigator.back())
        assertNull(navigator.currentProfileRoute)
    }

    @Test
    fun openArtistFromNowPlayingOverListeningStatsReturnsToStatsOnBack() {
        val navigator = AppNavigator()
        navigator.openProfileRoute(ProfileRoute.Stats)
        navigator.openNowPlaying()

        navigator.openArtist("UC_tulus", "Tulus")

        assertFalse(navigator.nowPlayingOpen)
        assertEquals("Listening stats", navigator.previousCatalogLabel())
        assertEquals("Statistik dengar", navigator.previousCatalogLabel(senada.app.AppLanguage.Indonesian))
        assertTrue(navigator.back())
        assertNull(navigator.currentCatalogRoute)
        assertEquals(ProfileRoute.Stats, navigator.currentProfileRoute)
        assertTrue(navigator.back())
        assertNull(navigator.currentProfileRoute)
    }

    @Test
    fun moodRouteHandlesBackStackParamsDistinctnessAndSaver() {
        val navigator = AppNavigator()
        navigator.openMood(MoodChip("Kosong", "   ", "p1"))
        assertTrue(navigator.catalogStack.isEmpty())

        val chill = MoodChip("Santai", "FEmusic_moods_and_genres_category", "params_chill")
        val focus = MoodChip("Fokus", "FEmusic_moods_and_genres_category", "params_focus")
        navigator.openNowPlaying()
        navigator.openMood(chill)
        assertFalse(navigator.nowPlayingOpen)
        assertEquals(CatalogRoute.Mood(chill.browseId, "Santai", "params_chill"), navigator.currentCatalogRoute)
        assertEquals("Home", navigator.previousCatalogLabel())
        assertEquals("Beranda", navigator.previousCatalogLabel(senada.app.AppLanguage.Indonesian))

        // Mood yang persis sama di puncak tumpukan diabaikan, tetapi params berbeda tetap dibuka.
        navigator.openMood(chill)
        assertEquals(1, navigator.catalogStack.size)
        navigator.openMood(focus)
        assertEquals(2, navigator.catalogStack.size)
        assertEquals("Santai", navigator.previousCatalogLabel())

        // Membuka playlist dari halaman Mood menyusun tumpukan kembali seperti halaman katalog lain.
        navigator.openCatalogPlaylist("PL_fokus", "Fokus Malam", "YouTube Music")
        assertEquals("Fokus", navigator.previousCatalogLabel())

        val saved = with(AppNavigator.Saver) { SaverScope { true }.save(navigator) }
        val restored = assertNotNull(AppNavigator.Saver.restore(assertNotNull(saved)))
        assertEquals(navigator.catalogStack, restored.catalogStack)

        assertTrue(restored.back())
        assertEquals(CatalogRoute.Mood(focus.browseId, "Fokus", "params_focus"), restored.currentCatalogRoute)
        assertTrue(restored.back())
        assertEquals(CatalogRoute.Mood(chill.browseId, "Santai", "params_chill"), restored.currentCatalogRoute)
        assertTrue(restored.back())
        assertNull(restored.currentCatalogRoute)
        assertEquals(AppDestination.Home, restored.destination)
    }
}
