package senada.app.tabs

import androidx.compose.runtime.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import senada.catalog.AlbumDetail
import senada.catalog.AlbumPage
import senada.catalog.AlbumSummary
import senada.catalog.ArtistDetail
import senada.catalog.ArtistPage
import senada.catalog.ArtistSummary
import senada.catalog.CatalogBrowser
import senada.catalog.CatalogException
import senada.catalog.DemoCatalog
import senada.catalog.PlaylistDetail
import senada.catalog.PlaylistImportException
import senada.catalog.PlaylistPage
import senada.catalog.PlaylistSummary
import senada.catalog.TrackPage
import senada.catalog.youtube.ARTIST_PAGE_TULUS
import senada.catalog.youtube.YOUTUBE_MUSIC_SOURCE
import senada.catalog.youtube.parseArtistPage
import senada.db.SenadaDatabase
import senada.library.LibraryRepository
import senada.library.testLibraryDriver
import senada.model.Track

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogPagesTest {
    private fun TestScope.testLibrary(): LibraryRepository {
        var now = 1_000L
        var seq = 0
        return LibraryRepository(
            database = SenadaDatabase(testLibraryDriver()),
            dispatcher = StandardTestDispatcher(testScheduler),
            now = { now++ },
            newPlaylistId = { "local-${++seq}" },
        )
    }

    private class FakeCatalogBrowser : CatalogBrowser {
        val calls = mutableListOf<String>()
        var failNext = false
        var playlistFailure: Exception? = null

        override suspend fun search(query: String, pageToken: String?): TrackPage {
            calls += "songs:$query:$pageToken"
            if (failNext) {
                failNext = false
                throw CatalogException(CatalogException.Kind.Network, "offline")
            }
            return TrackPage(
                listOf(Track("yt:track:1", "Teh Hijau", listOf("Tulus"), source = YOUTUBE_MUSIC_SOURCE)),
            )
        }

        override suspend fun searchArtists(query: String, pageToken: String?): ArtistPage {
            calls += "artists:$query:$pageToken"
            if (failNext) {
                failNext = false
                throw CatalogException(CatalogException.Kind.Network, "offline")
            }
            return if (pageToken == null) {
                ArtistPage(listOf(ArtistSummary("UC1", "Tulus", "Artis")), nextPageToken = "A2")
            } else {
                ArtistPage(listOf(ArtistSummary("UC2", "Yura Yunita", "Artis")), nextPageToken = null)
            }
        }

        override suspend fun searchAlbums(query: String, pageToken: String?): AlbumPage {
            calls += "albums:$query:$pageToken"
            if (failNext) {
                failNext = false
                throw CatalogException(CatalogException.Kind.Network, "offline")
            }
            return if (pageToken == null) {
                AlbumPage(listOf(AlbumSummary("MPREb_1", "Manusia", "Album • Tulus • 2022", "2022")), nextPageToken = "B2")
            } else {
                AlbumPage(listOf(AlbumSummary("MPREb_2", "Monokrom", "Album • Tulus • 2016", "2016")), nextPageToken = null)
            }
        }

        override suspend fun searchPlaylists(query: String, pageToken: String?): PlaylistPage {
            calls += "playlists:$query:$pageToken"
            if (failNext) {
                failNext = false
                throw CatalogException(CatalogException.Kind.Network, "offline")
            }
            return if (pageToken == null) {
                PlaylistPage(
                    listOf(PlaylistSummary("PL_1", "Senja Nusantara", "YouTube Music • 24 lagu")),
                    nextPageToken = "P2",
                )
            } else {
                PlaylistPage(
                    listOf(PlaylistSummary("PL_2", "Indie Pagi", "Studio Indie • 18 lagu")),
                    nextPageToken = null,
                )
            }
        }

        override suspend fun artist(id: String): ArtistDetail {
            calls += "artist:$id"
            if (failNext) {
                failNext = false
                throw CatalogException(CatalogException.Kind.Network, "offline")
            }
            return ArtistDetail(id = id, name = "Artis $id")
        }

        override suspend fun album(id: String): AlbumDetail {
            calls += "album:$id"
            if (failNext) {
                failNext = false
                throw CatalogException(CatalogException.Kind.Network, "offline")
            }
            return AlbumDetail(id = id, title = "Album $id")
        }

        override suspend fun playlist(id: String): PlaylistDetail {
            calls += "playlist:$id"
            playlistFailure?.let {
                playlistFailure = null
                throw it
            }
            if (failNext) {
                failNext = false
                throw CatalogException(CatalogException.Kind.Network, "offline")
            }
            return PlaylistDetail(
                id = id,
                title = "Playlist $id",
                owner = "YouTube Music",
                songCountText = "1 lagu",
                tracks = listOf(Track("yt:track:1", "Teh Hijau", listOf("Tulus"), source = YOUTUBE_MUSIC_SOURCE)),
            )
        }
    }

    @Test
    fun searchFilterDefaultsToSongsAndSwitchesWithoutReFetchingSameQuery() = runTest {
        val fake = FakeCatalogBrowser()
        val state = SearchTabState(
            queryState = mutableStateOf(""),
            filterState = mutableStateOf(SearchFilter.Songs),
            scope = this,
            catalog = fake,
            debounceMs = 0,
        )

        assertEquals(SearchFilter.Songs, state.filter)
        assertEquals(DemoCatalog.tracks, state.search.tracks)

        state.query = "tulus"
        state.refresh()
        assertEquals(listOf("Teh Hijau"), state.search.tracks.map { it.title })

        state.filter = SearchFilter.Artists
        state.refresh()
        assertEquals(listOf("Tulus"), state.artistSearch.items.map { it.name })
        assertEquals("A2", state.artistSearch.nextPageToken)

        state.filter = SearchFilter.Albums
        state.refresh()
        assertEquals(listOf("Manusia"), state.albumSearch.items.map { it.title })

        state.filter = SearchFilter.Playlists
        state.refresh()
        assertEquals(listOf("Senja Nusantara"), state.playlistSearch.items.map { it.title })
        assertEquals("P2", state.playlistSearch.nextPageToken)

        // Kembali ke Lagu untuk kueri yang sama memakai hasil yang sudah ada.
        state.filter = SearchFilter.Songs
        state.refresh()
        assertEquals(
            listOf("songs:tulus:null", "artists:tulus:null", "albums:tulus:null", "playlists:tulus:null"),
            fake.calls,
        )

        // Mengosongkan kueri mengembalikan katalog contoh.
        state.query = "   "
        state.refresh()
        assertEquals(DemoCatalog.tracks, state.search.tracks)
        assertEquals(emptyList(), state.artistSearch.items)
        assertEquals(emptyList(), state.albumSearch.items)
        assertEquals(emptyList(), state.playlistSearch.items)
    }

    @Test
    fun artistAndAlbumPaginationAndRetryKeepExistingResults() = runTest {
        val fake = FakeCatalogBrowser()
        val state = SearchTabState(
            queryState = mutableStateOf("tulus"),
            filterState = mutableStateOf(SearchFilter.Artists),
            scope = this,
            catalog = fake,
            debounceMs = 0,
        )

        state.refresh()
        assertEquals(listOf("UC1"), state.artistSearch.items.map { it.id })

        fake.failNext = true
        state.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("UC1"), state.artistSearch.items.map { it.id })
        assertNotNull(state.artistSearch.error)
        assertEquals("A2", state.artistSearch.nextPageToken)

        state.retry()
        advanceUntilIdle()
        assertNull(state.artistSearch.error)
        assertEquals(listOf("UC1", "UC2"), state.artistSearch.items.map { it.id })
        assertNull(state.artistSearch.nextPageToken)
    }

    @Test
    fun playlistPaginationAndRetryKeepExistingResults() = runTest {
        val fake = FakeCatalogBrowser()
        val state = SearchTabState(
            queryState = mutableStateOf("senja"),
            filterState = mutableStateOf(SearchFilter.Playlists),
            scope = this,
            catalog = fake,
            debounceMs = 0,
        )

        state.refresh()
        assertEquals(listOf("PL_1"), state.playlistSearch.items.map { it.id })

        fake.failNext = true
        state.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("PL_1"), state.playlistSearch.items.map { it.id })
        assertNotNull(state.playlistSearch.error)
        assertEquals("P2", state.playlistSearch.nextPageToken)

        state.retry()
        advanceUntilIdle()
        assertNull(state.playlistSearch.error)
        assertEquals(listOf("PL_1", "PL_2"), state.playlistSearch.items.map { it.id })
        assertNull(state.playlistSearch.nextPageToken)
    }

    @Test
    fun detailStoreCachesLoadedPagesAndEvictsOldestBeyondLimit() = runTest {
        val fake = FakeCatalogBrowser()
        val store = CatalogDetailStore(catalog = fake, maxEntries = 2)

        val a1 = store.loadArtist("UC1")
        val a1Again = store.loadArtist("UC1")
        assertEquals("Artis UC1", a1.data?.name)
        assertEquals(a1.data, a1Again.data)
        assertEquals(listOf("artist:UC1"), fake.calls)

        store.loadArtist("UC2")
        store.loadArtist("UC3")
        assertNull(store.cachedArtist("UC1"))
        assertNotNull(store.cachedArtist("UC2"))
        assertNotNull(store.cachedArtist("UC3"))

        fake.failNext = true
        val failed = store.loadAlbum("MPREb_fail")
        assertFalse(failed.loading)
        assertNull(failed.data)
        assertNotNull(failed.error)
        assertNull(store.cachedAlbum("MPREb_fail"))

        val p1 = store.loadPlaylist("PL_ok")
        val p1Again = store.loadPlaylist("PL_ok")
        assertEquals("Playlist PL_ok", p1.data?.title)
        assertEquals(p1.data, p1Again.data)

        fake.playlistFailure = PlaylistImportException("Playlist tidak dapat diakses.")
        val failedPlaylist = store.loadPlaylist("PL_private")
        assertEquals("Playlist tidak dapat diakses.", failedPlaylist.error)
        assertNull(store.cachedPlaylist("PL_private"))
    }

    @Test
    fun saveCatalogPlaylistSuffixesDuplicateNamesAndRejectsMix() = runTest {
        val repo = testLibrary()
        val tracks = listOf(
            Track("yt:track:v1", "Teh Hijau", listOf("Tulus"), 210_000L, source = YOUTUBE_MUSIC_SOURCE),
            Track("yt:track:v2", "Monokrom", listOf("Tulus"), 215_000L, source = YOUTUBE_MUSIC_SOURCE),
        )
        repo.createPlaylist("Chill Nusantara", tracks.take(1))

        val savedDup = saveCatalogPlaylist(repo, "PL_chill", "Chill Nusantara", tracks)
        assertIs<CatalogPlaylistSaveOutcome.Saved>(savedDup)
        assertEquals("Chill Nusantara (2)", savedDup.playlist.name)
        assertEquals(2, savedDup.playlist.trackCount)

        val savedEditorial = saveCatalogPlaylist(repo, "RDCLAK5uy_featured", "Featured Mix", tracks)
        assertIs<CatalogPlaylistSaveOutcome.Saved>(savedEditorial)
        assertEquals("Featured Mix", savedEditorial.playlist.name)

        val rejectedMix = saveCatalogPlaylist(repo, "RDAMVM12345", "My Mix", tracks)
        assertIs<CatalogPlaylistSaveOutcome.Rejected>(rejectedMix)
        assertEquals(MIX_SAVE_DISABLED_MESSAGE, rejectedMix.message)

        val storedPlaylists = repo.playlists.first()
        assertEquals(3, storedPlaylists.size)
        assertTrue(storedPlaylists.any { it.name == "Chill Nusantara (2)" })
        assertTrue(storedPlaylists.any { it.name == "Featured Mix" })
    }

    @Test
    fun artistPageShowsExactlyThreeSectionsFromRealArtistFixture() {
        val detail = parseArtistPage(
            "UC_DHlXllTSMB8pTC38_leFg",
            Json.parseToJsonElement(ARTIST_PAGE_TULUS).jsonObject,
        )

        val sections = artistPageSections(detail)

        assertEquals(
            listOf("Lagu terpopuler", "Album terpopuler", "Album terbaru"),
            sections.map { it.title },
        )
        val songsSection = assertIs<ArtistPageSection.PopularSongs>(sections[0])
        assertEquals(5, songsSection.songs.size)
        assertEquals("OLAK5uy_mHgw7gB4UOePEByyIUPoXcCIWTbVade7w", songsSection.fullListPlaylistId)

        val popularAlbumsSection = assertIs<ArtistPageSection.PopularAlbums>(sections[1])
        assertEquals(listOf("Manusia", "Monokrom", "Gajah", "Tulus"), popularAlbumsSection.albums.map { it.title })

        val newestAlbumsSection = assertIs<ArtistPageSection.NewestAlbums>(sections[2])
        assertEquals(
            listOf(
                "Manusia",
                "Langsung Dari Konser Monokrom Jakarta (Live)",
                "Monokrom",
                "Gajah",
                "Tulus",
            ),
            newestAlbumsSection.albums.map { it.title },
        )
    }

    @Test
    fun artistPageCapsPopularSongsAtFiveAndOmitsEmptySectionsOrMissingSeeAll() {
        val sevenSongs = (1..7).map { i ->
            Track("yt:track:s$i", "Lagu $i", listOf("Artis"), source = YOUTUBE_MUSIC_SOURCE)
        }
        val detail = ArtistDetail(
            id = "UC_simple",
            name = "Artis Sederhana",
            topSongs = sevenSongs,
            topSongsPlaylistId = null,
            popularAlbums = emptyList(),
            newestAlbums = listOf(AlbumSummary("MPREb_1", "Album Satu", year = "2024")),
        )

        val sections = artistPageSections(detail)

        assertEquals(listOf("Lagu terpopuler", "Album terbaru"), sections.map { it.title })
        val songsSection = assertIs<ArtistPageSection.PopularSongs>(sections[0])
        assertEquals(listOf("Lagu 1", "Lagu 2", "Lagu 3", "Lagu 4", "Lagu 5"), songsSection.songs.map { it.title })
        assertNull(songsSection.fullListPlaylistId)

        assertEquals(emptyList(), artistPageSections(null))
        assertEquals(emptyList(), artistPageSections(ArtistDetail(id = "UC_empty", name = "Kosong")))
    }
}
