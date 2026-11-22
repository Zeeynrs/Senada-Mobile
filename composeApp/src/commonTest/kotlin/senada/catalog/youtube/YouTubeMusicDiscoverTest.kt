package senada.catalog.youtube

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import senada.app.tabs.DISCOVER_TTL_MS
import senada.app.tabs.DiscoverController
import senada.catalog.AlbumSummary
import senada.catalog.ArtistSummary
import senada.catalog.CatalogException
import senada.local.LOCAL_FILE_SOURCE
import senada.model.Track

@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeMusicDiscoverTest {
    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun ytTrack(videoId: String, title: String, vararg artists: String) = Track(
        id = youTubeTrackId(videoId),
        title = title,
        artists = artists.toList(),
        source = YOUTUBE_MUSIC_SOURCE,
    )

    private class FakeDiscoverSource : DiscoverSource {
        val radioCalls = mutableListOf<String>()
        val artistCalls = mutableListOf<String>()
        val findArtistCalls = mutableListOf<String>()
        val homeCalls = mutableListOf<Boolean>()

        var radios: Map<String, List<RadioEntry>> = emptyMap()
        var artistPages: Map<String, ArtistDiscoverData> = emptyMap()
        var artistIds: Map<String, String> = emptyMap()
        var home: DiscoverFeed = DiscoverFeed()
        var failAll = false

        override suspend fun radio(videoId: String): List<RadioEntry> {
            radioCalls += videoId
            if (failAll) throw CatalogException(CatalogException.Kind.Network, "offline")
            return radios[videoId].orEmpty()
        }

        override suspend fun artistRelated(channelId: String): ArtistDiscoverData {
            artistCalls += channelId
            if (failAll) throw CatalogException(CatalogException.Kind.Network, "offline")
            return artistPages[channelId] ?: ArtistDiscoverData()
        }

        override suspend fun findArtistId(name: String): String? {
            findArtistCalls += name
            if (failAll) throw CatalogException(CatalogException.Kind.Network, "offline")
            return artistIds[name.lowercase()]
        }

        override suspend fun homeFeed(saveData: Boolean): DiscoverFeed {
            homeCalls += saveData
            if (failAll) throw CatalogException(CatalogException.Kind.Network, "offline")
            return home
        }
    }

    @Test
    fun seedSelectionWeightsRecentPlaysDoubleFavoritesOnceAndSkipsDemoAndLocal() {
        val t1 = ytTrack("v1", "Lagu 1", "Tulus")
        val t2 = ytTrack("v2", "Lagu 2", "Hindia")
        val t3 = ytTrack("v3", "Lagu 3", "Nadin Amizah")
        val t4 = ytTrack("v4", "Lagu 4", "Nadin Amizah")
        val t5 = ytTrack("v5", "Lagu 5", "Nadin Amizah")
        val local = Track("local:a.mp3", "Lokal", listOf("Artis Lokal"), source = LOCAL_FILE_SOURCE)
        val demo = Track("demo:track:hujan", "Selepas Hujan", listOf("Senada"), source = "demo")

        // t1 (Tulus) diputar 2 kali -> bobot 2*2=4; t2 (Hindia) diputar 1 kali -> bobot 2;
        // Nadin Amizah ada di 3 favorit -> bobot 3; Hindia +1 favorit -> bobot 3 (lebih baru dari Nadin).
        val seeds = pickDiscoverSeeds(
            history = listOf(local, demo, t1, t2),
            favorites = listOf(t3, t4, t5, t2),
            recentPlayCounts = listOf(t1 to 2, t2 to 1),
        )

        assertEquals(listOf("Tulus", "Hindia", "Nadin Amizah"), seeds.artistNames)
        assertEquals(listOf("v1", "v2"), seeds.songVideoIds)
        assertTrue(t1.id in seeds.recentlyPlayedTrackIds)
        assertTrue(t2.id in seeds.recentlyPlayedTrackIds)
        assertFalse(t3.id in seeds.recentlyPlayedTrackIds)
    }

    @Test
    fun radioParserReadsTracksAlbumsArtistIdsAndWrapperRenderer() {
        val entries = parseRadioTracks(json(NEXT_RADIO_TULUS))

        assertEquals(
            listOf("Teh Hijau", "Monokrom", "Sesi Potret (feat. Ari Lesmana)"),
            entries.map { it.track.title },
        )
        assertEquals(
            listOf("yt:track:5F28ye50-Kc", "yt:track:1RrF6Ee_io0", "yt:track:UmBNAt4xMSE"),
            entries.map { it.track.id },
        )
        assertEquals(listOf(213_000L, 215_000L, 243_000L), entries.map { it.track.durationMs })
        entries.forEach { assertEquals(YOUTUBE_MUSIC_SOURCE, it.track.source) }

        val monokrom = entries[1]
        assertEquals(listOf("Tulus"), monokrom.track.artists)
        assertEquals("UC_DHlXllTSMB8pTC38_leFg", monokrom.artistIds["tulus"])
        assertEquals("MPREb_4TEBtGA3cZb", monokrom.album?.id)
        assertEquals("Monokrom", monokrom.album?.title)

        val wrapped = """
            {
              "contents": {
                "singleColumnMusicWatchNextResultsRenderer": {
                  "tabbedRenderer": {
                    "watchNextTabbedResultsRenderer": {
                      "tabs": [{
                        "tabRenderer": {
                          "content": {
                            "musicQueueRenderer": {
                              "content": {
                                "playlistPanelRenderer": {
                                  "contents": [{
                                    "playlistPanelVideoWrapperRenderer": {
                                      "primaryRenderer": {
                                        "playlistPanelVideoRenderer": {
                                          "videoId": "wrap1",
                                          "title": { "runs": [{ "text": "Lagu Bungkus" }] },
                                          "shortBylineText": { "runs": [{ "text": "Artis Bungkus" }] },
                                          "lengthText": { "runs": [{ "text": "3:10" }] }
                                        }
                                      }
                                    }
                                  }]
                                }
                              }
                            }
                          }
                        }
                      }]
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val parsedWrapped = parseRadioTracks(json(wrapped))
        assertEquals(1, parsedWrapped.size)
        assertEquals("yt:track:wrap1", parsedWrapped.first().track.id)
        assertEquals(listOf("Artis Bungkus"), parsedWrapped.first().track.artists)
        assertEquals(190_000L, parsedWrapped.first().track.durationMs)
    }

    @Test
    fun artistRelatedParserReadsFansMightAlsoLikeAndAlbums() {
        val data = parseArtistRelated(json(ARTIST_PAGE_TULUS))

        assertEquals(listOf("Rizky Febian"), data.relatedArtists.map { it.name })
        assertEquals("UCGhU4I_1obQHUdq_plb1g2w", data.relatedArtists.first().id)
        assertEquals(listOf("Manusia", "Monokrom", "Teh Hijau", "Adaptasi"), data.albums.map { it.title })
    }

    @Test
    fun homeFeedParserReadsSongsAlbumsAndArtists() {
        val feed = parseHomeFeed(json(HOME_SHELVES_SAMPLE))

        assertEquals(listOf("Teh Hijau", "Negoro Angin"), feed.tracks.map { it.title })
        assertEquals(listOf("yt:track:5F28ye50-Kc", "yt:track:BLQPNaBklGA"), feed.tracks.map { it.id })
        assertEquals(listOf("Lumpur Dan Berlian", "Adiak Baparangai"), feed.albums.map { it.title })
        assertEquals(listOf("Denny Caknan", "Yeni Inka"), feed.artists.map { it.name })
    }

    @Test
    fun buildInterleavesSkipsRecentlyPlayedAndTopsUpFromHome() = runTest {
        val source = FakeDiscoverSource().apply {
            radios = mapOf(
                "s1" to listOf(
                    RadioEntry(ytTrack("s1", "Seed 1", "Tulus"), artistIds = mapOf("tulus" to "UC_tulus")),
                    RadioEntry(ytTrack("r1a", "Radio 1A", "Tulus"), album = AlbumSummary("MPREb_r1", "Album R1")),
                    RadioEntry(ytTrack("played", "Baru Diputar", "Tulus")),
                    RadioEntry(ytTrack("r1b", "Radio 1B", "Tulus")),
                ),
                "s2" to listOf(
                    RadioEntry(ytTrack("s2", "Seed 2", "Hindia")),
                    RadioEntry(ytTrack("r2a", "Radio 2A", "Hindia")),
                ),
            )
            artistIds = mapOf("hindia" to "UC_hindia")
            artistPages = mapOf(
                "UC_tulus" to ArtistDiscoverData(
                    relatedArtists = listOf(ArtistSummary("UC_hindia", "Hindia"), ArtistSummary("UC_rel1", "Rizky Febian")),
                    albums = listOf(AlbumSummary("MPREb_a1", "Manusia")),
                ),
                "UC_hindia" to ArtistDiscoverData(
                    relatedArtists = listOf(ArtistSummary("UC_rel2", "Feast")),
                    albums = listOf(AlbumSummary("MPREb_a2", "Menari")),
                ),
            )
            home = DiscoverFeed(
                tracks = (1..15).map { ytTrack("h$it", "Home $it", "Pop") },
                artists = listOf(ArtistSummary("UC_home1", "Home Artist")),
                albums = listOf(AlbumSummary("MPREb_h1", "Home Album")),
            )
        }
        val seeds = DiscoverSeeds(
            artistNames = listOf("Tulus", "Hindia"),
            songVideoIds = listOf("s1", "s2"),
            recentlyPlayedTrackIds = setOf(youTubeTrackId("played")),
        )

        val result = buildDiscoverRecommendations(seeds, source, saveData = false)

        assertTrue(result.personal)
        // Tulus ID dipanen langsung dari radio s1 tanpa perlu searchArtists.
        assertEquals(listOf("Hindia"), source.findArtistCalls)
        // Radio diselang-seling: r1a, r2a, r1b; lagu "played" dilewati; total dibatasi 12.
        assertEquals(DISCOVER_LIMIT, result.tracks.size)
        assertEquals(listOf("yt:track:r1a", "yt:track:r2a", "yt:track:r1b"), result.tracks.take(3).map { it.id })
        assertFalse(result.tracks.any { it.id == youTubeTrackId("played") })
        // Artis benih (UC_hindia) dikeluarkan; hasil selang-seling menghasilkan UC_rel2 lalu UC_rel1 lalu UC_home1.
        assertEquals(listOf("UC_rel2", "UC_rel1", "UC_home1"), result.artists.map { it.id })
        assertTrue(result.albums.map { it.id }.containsAll(listOf("MPREb_a1", "MPREb_a2", "MPREb_r1", "MPREb_h1")))
    }

    @Test
    fun newUserFallsBackToHomeAndSaveDataReducesRequestsAndOfflineThrows() = runTest {
        val source = FakeDiscoverSource().apply {
            home = DiscoverFeed(
                tracks = (1..10).map { ytTrack("h$it", "Home $it", "Pop") },
                artists = listOf(ArtistSummary("UC_1", "Artis 1")),
                albums = listOf(AlbumSummary("MPREb_1", "Album 1")),
            )
        }

        val newUser = buildDiscoverRecommendations(DiscoverSeeds(), source, saveData = false)
        assertFalse(newUser.personal)
        assertEquals(listOf(false), source.homeCalls)
        assertEquals(10, newUser.tracks.size)

        val saveDataUser = buildDiscoverRecommendations(DiscoverSeeds(), source, saveData = true)
        assertEquals(listOf(false, true), source.homeCalls)
        assertEquals(DISCOVER_SAVE_DATA_LIMIT, saveDataUser.tracks.size)

        source.failAll = true
        assertEquals(
            CatalogException.Kind.Network,
            assertFailsWith<CatalogException> { buildDiscoverRecommendations(DiscoverSeeds(), source) }.kind,
        )
    }

    @Test
    fun controllerCachesWithinTtlDefersOnDataSaverAndRetriesAfterError() = runTest {
        val source = FakeDiscoverSource().apply {
            home = DiscoverFeed(tracks = listOf(ytTrack("h1", "Pop 1", "Artis")))
        }
        var now = 1_000L
        val controller = DiscoverController(source, this, now = { now }, ttlMs = DISCOVER_TTL_MS)

        // Mode hemat data menunda pemuatan otomatis.
        controller.refreshIfNeeded(DiscoverSeeds(), saveData = true, force = false)
        advanceUntilIdle()
        assertTrue(controller.state.deferredByDataSaver)
        assertNull(controller.state.result)
        assertEquals(emptyList(), source.homeCalls)

        // Pengguna menekan "Muat" secara eksplisit -> memuat dalam mode hemat data.
        controller.refreshIfNeeded(DiscoverSeeds(), saveData = true, force = true)
        advanceUntilIdle()
        assertFalse(controller.state.deferredByDataSaver)
        assertNotNull(controller.state.result)
        assertEquals(listOf(true), source.homeCalls)

        // Dalam masa TTL, panggilan ulang tidak memukul jaringan.
        now += 60_000L
        controller.refreshIfNeeded(DiscoverSeeds(artistNames = listOf("Tulus")), saveData = false, force = false)
        advanceUntilIdle()
        assertEquals(listOf(true), source.homeCalls)

        // Setelah TTL lewat dengan benih baru, pemuatan ulang berjalan otomatis dan error tampil tenang.
        now += DISCOVER_TTL_MS + 1
        source.failAll = true
        controller.refreshIfNeeded(DiscoverSeeds(artistNames = listOf("Tulus")), saveData = false, force = false)
        advanceUntilIdle()
        assertNotNull(controller.state.error)

        source.failAll = false
        controller.refreshIfNeeded(DiscoverSeeds(), saveData = false, force = true)
        advanceUntilIdle()
        assertNull(controller.state.error)
        assertNotNull(controller.state.result)
    }

    @Test
    fun innertubeNextAndHomeFeedUseExpectedEndpointsAndSaveDataBudget() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = InnertubeClient(
            HttpClient(
                MockEngine { request ->
                    requests += request
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    val reply = when {
                        request.url.encodedPath.endsWith("/next") -> NEXT_RADIO_TULUS
                        body.string("browseId") == "UC_DHlXllTSMB8pTC38_leFg" -> ARTIST_PAGE_TULUS
                        else -> HOME_SHELVES_SAMPLE
                    }
                    respond(reply, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
        )
        val source = YouTubeMusicDiscoverSource(client)

        val radio = source.radio("5F28ye50-Kc")
        assertEquals(3, radio.size)
        val nextReq = requests.first()
        val nextBody = Json.parseToJsonElement((nextReq.body as TextContent).text).jsonObject
        assertTrue(nextReq.url.encodedPath.endsWith("/next"))
        assertEquals("5F28ye50-Kc", nextBody.string("videoId"))
        assertEquals("RDAMVM5F28ye50-Kc", nextBody.string("playlistId"))

        requests.clear()
        source.homeFeed(saveData = true)
        assertEquals(1, requests.size)

        requests.clear()
        source.homeFeed(saveData = false)
        assertEquals(3, requests.size)
    }
}
