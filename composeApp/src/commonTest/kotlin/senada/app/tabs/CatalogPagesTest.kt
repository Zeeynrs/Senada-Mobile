package senada.app.tabs

import androidx.compose.runtime.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import senada.catalog.AlbumDetail
import senada.catalog.AlbumPage
import senada.catalog.AlbumSummary
import senada.catalog.ArtistDetail
import senada.catalog.ArtistPage
import senada.catalog.ArtistSummary
import senada.catalog.CatalogBrowser
import senada.catalog.CatalogException
import senada.catalog.DemoCatalog
import senada.catalog.TrackPage
import senada.catalog.youtube.YOUTUBE_MUSIC_SOURCE
import senada.model.Track

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogPagesTest {
    private class FakeCatalogBrowser : CatalogBrowser {
        val calls = mutableListOf<String>()
        var failNext = false

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

        // Kembali ke Lagu untuk kueri yang sama memakai hasil yang sudah ada.
        state.filter = SearchFilter.Songs
        state.refresh()
        assertEquals(listOf("songs:tulus:null", "artists:tulus:null", "albums:tulus:null"), fake.calls)

        // Mengosongkan kueri mengembalikan katalog contoh.
        state.query = "   "
        state.refresh()
        assertEquals(DemoCatalog.tracks, state.search.tracks)
        assertEquals(emptyList(), state.artistSearch.items)
        assertEquals(emptyList(), state.albumSearch.items)
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
    }
}
