package senada.app.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import senada.app.shell.AppDestination
import senada.app.shell.CatalogRoute
import senada.catalog.AlbumDetail
import senada.catalog.AlbumPage
import senada.catalog.AlbumSummary
import senada.catalog.ArtistDetail
import senada.catalog.ArtistPage
import senada.catalog.ArtistSummary
import senada.catalog.CatalogBrowser
import senada.catalog.CatalogException
import senada.catalog.HomeCatalog
import senada.catalog.HomeFeed
import senada.catalog.HomeItem
import senada.catalog.HomeShelf
import senada.catalog.HomeShelfLayout
import senada.catalog.HomeShelfSource
import senada.catalog.MoodChip
import senada.catalog.PlaylistDetail
import senada.catalog.PlaylistPage
import senada.catalog.TrackPage
import senada.catalog.youtube.DiscoverResult
import senada.catalog.youtube.YOUTUBE_MUSIC_SOURCE
import senada.library.RecentHomeItem
import senada.model.Track

@OptIn(ExperimentalCoroutinesApi::class)
class HomeShelvesTest {
    private fun song(id: String, title: String, artist: String = "Senada") = Track(
        id = "yt:track:$id",
        title = title,
        artists = listOf(artist),
        durationMs = 200_000L,
        source = YOUTUBE_MUSIC_SOURCE,
    )

    private fun sampleFeed(tag: String = "v1"): HomeFeed = HomeFeed(
        shelves = listOf(
            HomeShelf(
                title = "Pilihan cepat $tag",
                source = HomeShelfSource.Home,
                items = (1..6).map { HomeItem.Song(song("$tag-$it", "Lagu $tag-$it")) },
                layout = HomeShelfLayout.QuickPicks,
            ),
            HomeShelf(
                title = "Tangga lagu $tag",
                source = HomeShelfSource.Charts,
                items = (1..6).map { HomeItem.Song(song("chart-$tag-$it", "Chart $it")) },
                layout = HomeShelfLayout.SongList,
            ),
            HomeShelf(
                title = "Rilis baru $tag",
                source = HomeShelfSource.Explore,
                items = listOf(
                    HomeItem.Album(AlbumSummary("MPREb_$tag", "Manusia", "Album • Tulus • 2022", "2022")),
                    HomeItem.Playlist("PL_$tag", "Santai Sore", "YouTube Music"),
                ),
                layout = HomeShelfLayout.Cards,
            ),
            HomeShelf(
                title = "Artis pilihan $tag",
                source = HomeShelfSource.Home,
                items = listOf(HomeItem.Artist(ArtistSummary("UC_$tag", "Tulus", "Artis"))),
                layout = HomeShelfLayout.Artists,
            ),
        ),
        moods = listOf(
            MoodChip("Santai", "FEmusic_moods_and_genres_category", "params_chill"),
            MoodChip("Fokus", "FEmusic_moods_and_genres_category", "params_focus"),
        ),
    )

    private class FakeHomeCatalog : HomeCatalog {
        val homeCalls = mutableListOf<Boolean>()
        val moodCalls = mutableListOf<MoodChip>()
        var nextFeed: (saveData: Boolean) -> HomeFeed = { HomeFeed(emptyList(), emptyList()) }
        var moodShelves: Map<String, List<HomeShelf>> = emptyMap()
        var failHome: Exception? = null
        var failMood: Exception? = null

        override suspend fun homeFeed(saveData: Boolean): HomeFeed {
            homeCalls += saveData
            failHome?.let { throw it }
            return nextFeed(saveData)
        }

        override suspend fun mood(chip: MoodChip): List<HomeShelf> {
            moodCalls += chip
            failMood?.let { throw it }
            return moodShelves[chip.params ?: chip.browseId].orEmpty()
        }
    }

    private class UnusedCatalogBrowser : CatalogBrowser {
        override suspend fun search(query: String, pageToken: String?): TrackPage = TrackPage(emptyList())
        override suspend fun searchArtists(query: String, pageToken: String?): ArtistPage = ArtistPage(emptyList())
        override suspend fun searchAlbums(query: String, pageToken: String?): AlbumPage = AlbumPage(emptyList())
        override suspend fun searchPlaylists(query: String, pageToken: String?): PlaylistPage = PlaylistPage(emptyList())
        override suspend fun artist(id: String): ArtistDetail = ArtistDetail(id, "")
        override suspend fun album(id: String): AlbumDetail = AlbumDetail(id, "")
        override suspend fun playlist(id: String): PlaylistDetail = PlaylistDetail(id, "")
    }

    @Test
    fun controllerLoadsOncePerSessionCachesWithinTenMinutesAndRefreshesOnForceOrExpiry() = runTest {
        var version = 0
        val catalog = FakeHomeCatalog().apply {
            nextFeed = { sampleFeed("v${++version}") }
        }
        var now = 1_000L
        val controller = HomeShelvesController(
            catalog = catalog,
            scope = this,
            now = { now },
            ttlMs = HOME_SHELVES_TTL_MS,
            workContext = coroutineContext,
        )

        // Muat pertama kali pada jaringan normal.
        controller.refreshIfNeeded(saveData = false)
        advanceUntilIdle()
        assertEquals(listOf(false), catalog.homeCalls)
        assertEquals("Pilihan cepat v1", controller.state.feed?.shelves?.firstOrNull()?.title)
        assertNull(controller.state.error)
        assertFalse(controller.state.loading)

        // Dalam jendela cache 10 menit, pemanggilan ulang tidak menyentuh jaringan.
        now += HOME_SHELVES_TTL_MS - 1L
        controller.refreshIfNeeded(saveData = false)
        advanceUntilIdle()
        assertEquals(listOf(false), catalog.homeCalls)

        // Setelah 10 menit lewat, pemanggilan ulang menyegarkan otomatis.
        now += 1L
        controller.refreshIfNeeded(saveData = false)
        advanceUntilIdle()
        assertEquals(listOf(false, false), catalog.homeCalls)
        assertEquals("Pilihan cepat v2", controller.state.feed?.shelves?.firstOrNull()?.title)

        // "Segarkan" (force = true) menyegarkan meski masih dalam masa TTL.
        now += 5_000L
        controller.refresh(saveData = false)
        advanceUntilIdle()
        assertEquals(listOf(false, false, false), catalog.homeCalls)
        assertEquals("Pilihan cepat v3", controller.state.feed?.shelves?.firstOrNull()?.title)
    }

    @Test
    fun controllerDefersOnSaveDataAndPassesSaveDataFlagWhenExplicitlyLoaded() = runTest {
        val catalog = FakeHomeCatalog().apply {
            nextFeed = { saveData ->
                if (saveData) {
                    HomeFeed(shelves = sampleFeed("hemat").shelves.take(1), moods = emptyList())
                } else {
                    sampleFeed("penuh")
                }
            }
        }
        var now = 5_000L
        val controller = HomeShelvesController(
            catalog = catalog,
            scope = this,
            now = { now },
            ttlMs = HOME_SHELVES_TTL_MS,
            workContext = coroutineContext,
        )

        // Jaringan berbayar + Hemat data: tidak memuat otomatis, tampilkan tombol tenang.
        controller.refreshIfNeeded(saveData = true)
        advanceUntilIdle()
        assertTrue(controller.state.deferredByDataSaver)
        assertNull(controller.state.feed)
        assertEquals(emptyList(), catalog.homeCalls)

        // Pengguna mengetuk "Muat rekomendasi YouTube Music" -> memuat dengan saveData = true.
        controller.refresh(saveData = true)
        advanceUntilIdle()
        assertFalse(controller.state.deferredByDataSaver)
        assertEquals(listOf(true), catalog.homeCalls)
        assertEquals(1, controller.state.feed?.shelves?.size)

        // Setelah TTL habis namun Hemat data masih aktif, jangan muat ulang otomatis tanpa aksi pengguna.
        now += HOME_SHELVES_TTL_MS + 1_000L
        controller.refreshIfNeeded(saveData = true)
        advanceUntilIdle()
        assertEquals(listOf(true), catalog.homeCalls)
        assertEquals(1, controller.state.feed?.shelves?.size)

        // Begitu kembali ke jaringan non-berbayar dan TTL sudah lewat, feed penuh dimuat otomatis.
        controller.refreshIfNeeded(saveData = false)
        advanceUntilIdle()
        assertEquals(listOf(true, false), catalog.homeCalls)
        assertEquals(4, controller.state.feed?.shelves?.size)
        assertEquals(2, controller.state.feed?.moods?.size)
    }

    @Test
    fun controllerHandlesFailureAndPartialFailureWithoutDroppingCachedShelves() = runTest {
        val networkError = CatalogException(CatalogException.Kind.Network, "offline")
        val catalog = FakeHomeCatalog().apply {
            failHome = networkError
        }
        var now = 0L
        val controller = HomeShelvesController(
            catalog = catalog,
            scope = this,
            now = { now },
            ttlMs = HOME_SHELVES_TTL_MS,
            workContext = coroutineContext,
        )

        // Kegagalan awal menampilkan pesan error tanpa crash.
        controller.refreshIfNeeded(saveData = false)
        advanceUntilIdle()
        assertEquals(networkError.userMessage, controller.state.error)
        assertNull(controller.state.feed)
        assertFalse(controller.state.loading)

        // Coba lagi mengembalikan feed parsial (hanya rak Home tanpa Explore/Charts/moods).
        catalog.failHome = null
        catalog.nextFeed = {
            HomeFeed(
                shelves = listOf(
                    HomeShelf(
                        title = "Pilihan cepat",
                        source = HomeShelfSource.Home,
                        items = listOf(HomeItem.Song(song("s1", "Lagu Satu"))),
                        layout = HomeShelfLayout.QuickPicks,
                    ),
                ),
                moods = emptyList(),
            )
        }
        controller.refresh(saveData = false)
        advanceUntilIdle()
        assertNull(controller.state.error)
        assertEquals(listOf("Pilihan cepat"), controller.state.feed?.shelves?.map { it.title })

        // Kegagalan saat menyegarkan ulang tetap mempertahankan rak yang sudah tampil sebelumnya.
        now += HOME_SHELVES_TTL_MS + 10L
        val rateLimited = CatalogException(CatalogException.Kind.RateLimited, "429")
        catalog.failHome = rateLimited
        controller.refresh(saveData = false)
        advanceUntilIdle()
        assertEquals(rateLimited.userMessage, controller.state.error)
        assertEquals(listOf("Pilihan cepat"), controller.state.feed?.shelves?.map { it.title })
    }

    @Test
    fun shouldRefreshHomeShelvesTriggersOnlyOnVisibleHomeWithoutOverlays() {
        assertTrue(
            shouldRefreshHomeShelves(
                screenVisible = true,
                destination = AppDestination.Home,
                hasCatalogRoute = false,
                mostPlayedOpen = false,
                listeningStatsOpen = false,
            ),
        )
        assertFalse(
            shouldRefreshHomeShelves(
                screenVisible = false,
                destination = AppDestination.Home,
                hasCatalogRoute = false,
                mostPlayedOpen = false,
                listeningStatsOpen = false,
            ),
        )
        assertFalse(
            shouldRefreshHomeShelves(
                screenVisible = true,
                destination = AppDestination.Search,
                hasCatalogRoute = false,
                mostPlayedOpen = false,
                listeningStatsOpen = false,
            ),
        )
        assertFalse(
            shouldRefreshHomeShelves(
                screenVisible = true,
                destination = AppDestination.Home,
                hasCatalogRoute = true,
                mostPlayedOpen = false,
                listeningStatsOpen = false,
            ),
        )
        assertFalse(
            shouldRefreshHomeShelves(
                screenVisible = true,
                destination = AppDestination.Home,
                hasCatalogRoute = false,
                mostPlayedOpen = true,
                listeningStatsOpen = false,
            ),
        )
        assertFalse(
            shouldRefreshHomeShelves(
                screenVisible = true,
                destination = AppDestination.Home,
                hasCatalogRoute = false,
                mostPlayedOpen = false,
                listeningStatsOpen = true,
            ),
        )
    }

    @Test
    fun berandaSectionOrderMatchesSpecAndKeepsPersonalPartsWhenYouTubeFailsOrDefers() {
        val recent = listOf(RecentHomeItem.Song(song("r1", "Baru"), playedAtMs = 100L))
        val personalLoaded = DiscoverUiState(
            result = DiscoverResult(tracks = listOf(song("p1", "Personal 1")), personal = true),
        )
        val feedWithEmptyShelf = HomeFeed(
            shelves = listOf(
                HomeShelf("Pilihan cepat", HomeShelfSource.Home, listOf(HomeItem.Song(song("y1", "Y1"))), HomeShelfLayout.QuickPicks),
                HomeShelf("Rak kosong", HomeShelfSource.Explore, emptyList(), HomeShelfLayout.Cards),
                HomeShelf("   ", HomeShelfSource.Charts, listOf(HomeItem.Song(song("y2", "Y2"))), HomeShelfLayout.SongList),
                HomeShelf("Tangga lagu", HomeShelfSource.Charts, listOf(HomeItem.Song(song("y3", "Y3"))), HomeShelfLayout.SongList),
            ),
            moods = listOf(
                MoodChip("Santai", "FEmusic_moods_and_genres_category", "p1"),
                MoodChip("  ", "FEmusic_moods_and_genres_category", "p2"),
            ),
        )

        // Urutan penuh: Hero -> Baru diputar (dengan kartu Favorit) -> Untuk kamu -> Mood -> Rak YouTube Music.
        val full = buildHomeSections(
            recentItems = recent,
            discover = personalLoaded,
            homeShelves = HomeShelvesUiState(feed = feedWithEmptyShelf),
        )
        assertEquals(6, full.size)
        assertIs<HomeSection.Hero>(full[0])
        assertIs<HomeSection.Recent>(full[1])
        assertIs<HomeSection.Personal>(full[2])
        val moods = assertIs<HomeSection.Moods>(full[3])
        assertEquals(listOf("Santai"), moods.chips.map { it.title })
        assertFalse(moods.showRefresh)
        val shelf0 = assertIs<HomeSection.YouTubeShelf>(full[4])
        assertEquals("Pilihan cepat", shelf0.shelf.title)
        assertTrue(shelf0.showRefresh)
        val shelf1 = assertIs<HomeSection.YouTubeShelf>(full[5])
        assertEquals("Tangga lagu", shelf1.shelf.title)
        assertFalse(shelf1.showRefresh)

        // Bila YouTube Music gagal, bagian pribadi (Hero, Baru diputar, Untuk kamu) tetap tampil.
        val ytFailed = buildHomeSections(
            recentItems = recent,
            discover = personalLoaded,
            homeShelves = HomeShelvesUiState(feed = null, loading = false, error = "Offline", deferredByDataSaver = false),
        )
        assertEquals(4, ytFailed.size)
        assertIs<HomeSection.Hero>(ytFailed[0])
        assertIs<HomeSection.Recent>(ytFailed[1])
        assertIs<HomeSection.Personal>(ytFailed[2])
        assertEquals("Offline", assertIs<HomeSection.YouTubeError>(ytFailed[3]).message)

        // Hemat data menunda keduanya -> bagian pribadi lokal + satu tombol "Muat rekomendasi YouTube Music".
        val bothDeferred = buildHomeSections(
            recentItems = recent,
            discover = DiscoverUiState(deferredByDataSaver = true),
            homeShelves = HomeShelvesUiState(deferredByDataSaver = true),
        )
        assertEquals(3, bothDeferred.size)
        assertIs<HomeSection.Hero>(bothDeferred[0])
        assertIs<HomeSection.Recent>(bothDeferred[1])
        assertIs<HomeSection.YouTubeDataSaver>(bothDeferred[2])

        // Hemat data dengan Untuk kamu yang sudah termuat sebelumnya -> Baru diputar (kartu Favorit) + Untuk kamu + tombol Hemat data.
        val homeOnlyDeferred = buildHomeSections(
            recentItems = emptyList(),
            discover = personalLoaded,
            homeShelves = HomeShelvesUiState(deferredByDataSaver = true),
        )
        assertEquals(4, homeOnlyDeferred.size)
        assertIs<HomeSection.Hero>(homeOnlyDeferred[0])
        assertEquals(emptyList(), assertIs<HomeSection.Recent>(homeOnlyDeferred[1]).items)
        assertIs<HomeSection.Personal>(homeOnlyDeferred[2])
        assertIs<HomeSection.YouTubeDataSaver>(homeOnlyDeferred[3])

        // Pemuatan awal YouTube Music menampilkan skeleton tanpa menunda bagian pribadi.
        val loading = buildHomeSections(
            recentItems = emptyList(),
            discover = personalLoaded,
            homeShelves = HomeShelvesUiState(loading = true),
        )
        assertEquals(4, loading.size)
        assertIs<HomeSection.Recent>(loading[1])
        assertIs<HomeSection.Personal>(loading[2])
        assertIs<HomeSection.YouTubeLoading>(loading[3])
    }

    @Test
    fun quickPicksPagingMathAdaptsToWidthAndFontScale() {
        // Ponsel 360 dp pada skala baku 100%: 4 baris x 1 kolom = 4 lagu/halaman.
        val phoneNormal = quickPicksGridSpec(availableWidthDp = 360f, fontScale = 1.0f)
        assertEquals(QuickPicksGridSpec(rowsPerColumn = 4, columnsPerPage = 1), phoneNormal)
        assertEquals(4, phoneNormal.itemsPerPage)

        // Ponsel 360 dp pada skala 150%: 3 baris x 1 kolom = 3 lagu/halaman.
        val phoneMedium = quickPicksGridSpec(availableWidthDp = 360f, fontScale = 1.5f)
        assertEquals(QuickPicksGridSpec(rowsPerColumn = 3, columnsPerPage = 1), phoneMedium)
        assertEquals(3, phoneMedium.itemsPerPage)

        // Ponsel 360 dp pada skala 200%: 2 baris x 1 kolom = 2 lagu/halaman agar tidak berdesakan.
        val phoneLarge = quickPicksGridSpec(availableWidthDp = 360f, fontScale = 2.0f)
        assertEquals(QuickPicksGridSpec(rowsPerColumn = 2, columnsPerPage = 1), phoneLarge)
        assertEquals(2, phoneLarge.itemsPerPage)

        // Layar lebar 640 dp pada skala baku 100%: 4 baris x 2 kolom = 8 lagu/halaman.
        val wideNormal = quickPicksGridSpec(availableWidthDp = 640f, fontScale = 1.0f)
        assertEquals(QuickPicksGridSpec(rowsPerColumn = 4, columnsPerPage = 2), wideNormal)
        assertEquals(8, wideNormal.itemsPerPage)

        // Layar lebar 640 dp pada skala 200%: turun ke 1 kolom x 2 baris agar teks judul tetap lega.
        val wideLarge = quickPicksGridSpec(availableWidthDp = 640f, fontScale = 2.0f)
        assertEquals(QuickPicksGridSpec(rowsPerColumn = 2, columnsPerPage = 1), wideLarge)
        assertEquals(2, wideLarge.itemsPerPage)

        // Tablet 960 dp pada skala baku 100%: dibatasi maksimal 3 kolom x 4 baris = 12 lagu/halaman.
        val tabletNormal = quickPicksGridSpec(availableWidthDp = 960f, fontScale = 1.0f)
        assertEquals(QuickPicksGridSpec(rowsPerColumn = 4, columnsPerPage = 3), tabletNormal)
        assertEquals(12, tabletNormal.itemsPerPage)

        // Masukan ekstrem tetap aman.
        val zeroWidth = quickPicksGridSpec(availableWidthDp = 0f, fontScale = 0.5f)
        assertEquals(QuickPicksGridSpec(rowsPerColumn = 4, columnsPerPage = 1), zeroWidth)

        // Pembagian halaman dan kolom mempertahankan urutan semua item.
        val items = (1..10).toList()
        assertEquals(
            listOf(
                listOf(listOf(1, 2, 3, 4)),
                listOf(listOf(5, 6, 7, 8)),
                listOf(listOf(9, 10)),
            ),
            paginateQuickPicks(items, phoneNormal),
        )
        assertEquals(
            listOf(
                listOf(listOf(1, 2, 3, 4), listOf(5, 6, 7, 8)),
                listOf(listOf(9, 10)),
            ),
            paginateQuickPicks(items, wideNormal),
        )
        assertEquals(5, paginateQuickPicks(items, phoneLarge).size)
        assertEquals(emptyList(), paginateQuickPicks(emptyList<Int>(), phoneNormal))
    }

    @Test
    fun accessibilityLabelsFormatAlbumPlaylistArtistAndMoodClearly() {
        assertEquals(
            "Album Manusia by Tulus",
            albumCardAccessibilityLabel(AlbumSummary("a1", "Manusia", subtitle = "Tulus")),
        )
        assertEquals(
            "Album Manusia oleh Tulus",
            albumCardAccessibilityLabel(AlbumSummary("a1", "Manusia", subtitle = "Tulus"), senada.app.AppLanguage.Indonesian),
        )
        assertEquals(
            "Album Manusia by Tulus, 2022",
            albumCardAccessibilityLabel(AlbumSummary("a1", "Manusia", subtitle = "Album • Tulus • 2022", year = "2022")),
        )
        assertEquals(
            "Album Monokrom, 2016",
            albumCardAccessibilityLabel(AlbumSummary("a2", "Monokrom", subtitle = null, year = "2016")),
        )
        assertEquals(
            "Album Monokrom",
            albumCardAccessibilityLabel(AlbumSummary("a2", "Monokrom")),
        )
        assertEquals(
            "Playlist Santai Sore",
            playlistCardAccessibilityLabel(HomeItem.Playlist("PL1", "Santai Sore")),
        )
        assertEquals(
            "Playlist Santai Sore, YouTube Music",
            playlistCardAccessibilityLabel(HomeItem.Playlist("PL1", "Santai Sore", subtitle = "YouTube Music")),
        )
        assertEquals(
            "Artist Tulus",
            artistCardAccessibilityLabel(ArtistSummary("UC1", "Tulus")),
        )
        assertEquals(
            "Artis Tulus",
            artistCardAccessibilityLabel(ArtistSummary("UC1", "Tulus"), senada.app.AppLanguage.Indonesian),
        )
        assertEquals(
            "Artist Tulus, 4,2 jt pendengar",
            artistCardAccessibilityLabel(ArtistSummary("UC1", "Tulus", subtitle = "4,2 jt pendengar")),
        )
        assertEquals(
            "Mood Fokus",
            moodChipAccessibilityLabel(MoodChip("Fokus", "FEmusic_moods_and_genres_category", "p_focus")),
        )
    }

    @Test
    fun detailStoreCachesMoodPagesSeparatelyByBrowseIdAndParams() = runTest {
        val chill = MoodChip("Santai", "FEmusic_moods_and_genres_category", "params_chill")
        val focus = MoodChip("Fokus", "FEmusic_moods_and_genres_category", "params_focus")
        val fakeHome = FakeHomeCatalog().apply {
            moodShelves = mapOf(
                "params_chill" to listOf(
                    HomeShelf(
                        title = "Playlist Santai",
                        source = HomeShelfSource.Explore,
                        items = listOf(HomeItem.Playlist("PL_chill", "Sore Tenang")),
                        layout = HomeShelfLayout.Cards,
                    ),
                ),
                "params_focus" to listOf(
                    HomeShelf(
                        title = "Playlist Fokus",
                        source = HomeShelfSource.Explore,
                        items = listOf(HomeItem.Playlist("PL_focus", "Kerja Dalam")),
                        layout = HomeShelfLayout.Cards,
                    ),
                ),
            )
        }
        val store = CatalogDetailStore(catalog = UnusedCatalogBrowser(), homeCatalog = fakeHome)
        val chillRoute = CatalogRoute.Mood(chill.browseId, chill.title, chill.params)
        val focusRoute = CatalogRoute.Mood(focus.browseId, focus.title, focus.params)

        val loadedChill = store.loadMood(chill, chillRoute.cacheKey())
        val loadedChillAgain = store.loadMood(chill, chillRoute.cacheKey())
        val loadedFocus = store.loadMood(focus, focusRoute.cacheKey())

        assertEquals(listOf("Playlist Santai"), loadedChill.data?.map { it.title })
        assertEquals(loadedChill.data, loadedChillAgain.data)
        assertEquals(listOf("Playlist Fokus"), loadedFocus.data?.map { it.title })
        assertEquals(listOf(chill, focus), fakeHome.moodCalls)

        val moodError = CatalogException(CatalogException.Kind.Network, "offline")
        fakeHome.failMood = moodError
        val failed = store.loadMood(
            MoodChip("Olahraga", "FEmusic_moods_and_genres_category", "params_workout"),
            "FEmusic_moods_and_genres_category|params_workout",
        )
        assertEquals(moodError.userMessage, failed.error)
        assertNull(failed.data)
        assertNull(store.cachedMood("FEmusic_moods_and_genres_category|params_workout"))
    }
}
