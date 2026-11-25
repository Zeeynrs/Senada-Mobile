package senada.catalog.youtube

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import senada.catalog.CatalogException
import senada.catalog.HomeItem
import senada.catalog.HomeShelfLayout
import senada.catalog.HomeShelfSource
import senada.catalog.MoodChip

class YouTubeMusicHomeTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun home(handler: suspend MockRequestHandleScope.(HttpRequestData, JsonObject) -> HttpResponseData): YouTubeMusicHome {
        requests.clear()
        val client = InnertubeClient(
            HttpClient(
                MockEngine { request ->
                    requests += request
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    handler(request, body)
                },
            ),
        )
        return YouTubeMusicHome(client)
    }

    private fun MockRequestHandleScope.json(text: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(text, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.jsonBody(): JsonObject =
        Json.parseToJsonElement((body as TextContent).text).jsonObject

    @Test
    fun homeFeedFullLoadsAllSourcesAndMapsShelvesMoodsAndMixes() = runTest {
        val catalog = home { _, body ->
            when {
                body.string("continuation") == "HOME_PAGE_2_TOKEN" -> json(REAL_HOME_PAGE_2)
                body.string("continuation") == "HOME_PAGE_3_TOKEN" -> json(REAL_HOME_PAGE_3)
                body.string("browseId") == "FEmusic_home" -> json(REAL_HOME_PAGE_1)
                body.string("browseId") == "FEmusic_explore" -> json(REAL_EXPLORE_PAGE)
                body.string("browseId") == "FEmusic_charts" -> json(REAL_CHARTS_PAGE)
                else -> error("Unexpected request: $body")
            }
        }

        val feed = catalog.homeFeed(saveData = false)

        assertEquals(5, requests.size)
        assertEquals(
            listOf(
                "Mix lagu",
                "Kesedihan musim hujan",
                "Rilis baru",
                "Lagu trending untuk kamu",
                "Hits Indonesia.",
                "Cover dan remix",
                "Album & single terbaru",
                "Trending",
                "Tangga lagu video",
                "Artis teratas",
            ),
            feed.shelves.map { it.title },
        )

        // Rak pilihan cepat dan lagu bernomor menggunakan ID yt:track:<videoId> serta source ytmusic.
        val quickPicks = feed.shelves[0]
        assertEquals(HomeShelfSource.Home, quickPicks.source)
        assertEquals(HomeShelfLayout.QuickPicks, quickPicks.layout)
        val firstSong = quickPicks.items.first() as HomeItem.Song
        assertEquals("yt:track:BLQPNaBklGA", firstSong.track.id)
        assertEquals("Negoro Angin", firstSong.track.title)
        assertEquals(listOf("Denny Caknan"), firstSong.track.artists)
        assertEquals(YOUTUBE_MUSIC_SOURCE, firstSong.track.source)

        // Mix dari Beranda berupa playlist tanpa awalan "VL" dan berawalan "RD".
        val mixesShelf = feed.shelves[1]
        assertEquals(HomeShelfLayout.Cards, mixesShelf.layout)
        val firstMix = mixesShelf.items.first() as HomeItem.Playlist
        assertEquals("RDCLAK5uy_nQ7zJT5SkLuI4COHNLXeDYvjY8XQpz--U", firstMix.id)
        assertTrue(firstMix.id.startsWith("RD"))
        assertEquals("Jam Galau", firstMix.title)

        // Album / single dari Home dan Explore.
        val newReleases = feed.shelves[2]
        assertEquals(HomeShelfLayout.Cards, newReleases.layout)
        val singleItem = newReleases.items.first() as HomeItem.Album
        assertEquals("MPREb_z43rR5FKyfe", singleItem.album.id)

        // Rak lagu lainnya (di Home dan Explore) menjadi SongList.
        assertEquals(HomeShelfLayout.SongList, feed.shelves[3].layout)
        assertEquals(HomeShelfLayout.SongList, feed.shelves[5].layout)
        val trending = feed.shelves.first { it.title == "Trending" }
        assertEquals(HomeShelfSource.Explore, trending.source)
        assertEquals(HomeShelfLayout.SongList, trending.layout)
        assertEquals(2, trending.items.size)

        // Tangga lagu video -> Cards playlist tanpa "VL"; Artis teratas -> Artists.
        val videoCharts = feed.shelves.first { it.title == "Tangga lagu video" }
        assertEquals(HomeShelfSource.Charts, videoCharts.source)
        assertEquals(HomeShelfLayout.Cards, videoCharts.layout)
        assertEquals("OLAK5uy_n7Ig_LAUbKE6_ZeQ1pwHmJcEhwX7BekBo", (videoCharts.items.first() as HomeItem.Playlist).id)

        val topArtists = feed.shelves.first { it.title == "Artis teratas" }
        assertEquals(HomeShelfSource.Charts, topArtists.source)
        assertEquals(HomeShelfLayout.Artists, topArtists.layout)
        assertEquals("Denny Caknan", (topArtists.items.first() as HomeItem.Artist).artist.name)

        // Rak video/episode ("Video musik terbaru"), rak kosong, dan rak tombol mood tidak dijadikan shelf.
        assertFalse(feed.shelves.any { it.title == "Video musik terbaru" })
        assertFalse(feed.shelves.any { it.title == "Jenis musik & suasana" })

        // Tombol Moods & genres diekstrak ke HomeFeed.moods (tanpa pintasan grid teratas Explore).
        assertEquals(
            listOf(
                MoodChip("Bepergian", "FEmusic_moods_and_genres_category", "ggMPOg1uX044Z2o5WERLckpU"),
                MoodChip("Fokus", "FEmusic_moods_and_genres_category", "ggMPOg1uX0NvNGNhWThMYWRh"),
                MoodChip("Gaming", "FEmusic_moods_and_genres_category", "ggMPOg1uX3NmUVV4Vzl3WGQ0"),
            ),
            feed.moods,
        )
    }

    @Test
    fun quickPicksDetectionSupportsEnglishAndIndonesianTitles() {
        val enPage = parseBrowseShelves(Json.parseToJsonElement(REAL_HOME_EN_PAGE_1).jsonObject, HomeShelfSource.Home)
        assertEquals(listOf("Quick picks", "Trending community playlists"), enPage.shelves.map { it.title })
        assertEquals(HomeShelfLayout.QuickPicks, enPage.shelves[0].layout)
        assertEquals(HomeShelfLayout.Cards, enPage.shelves[1].layout)

        val idSamplePage = parseBrowseShelves(Json.parseToJsonElement(HOME_SHELVES_SAMPLE).jsonObject, HomeShelfSource.Home)
        assertEquals(HomeShelfLayout.QuickPicks, idSamplePage.shelves[0].layout)
        assertEquals("Pilihan cepat", idSamplePage.shelves[0].title)
        assertEquals(HomeShelfLayout.Cards, idSamplePage.shelves[1].layout)
        assertEquals(HomeShelfLayout.Artists, idSamplePage.shelves[2].layout)

        val idLivePage = parseBrowseShelves(Json.parseToJsonElement(REAL_HOME_PAGE_1).jsonObject, HomeShelfSource.Home)
        assertEquals(HomeShelfLayout.QuickPicks, idLivePage.shelves[0].layout)
        assertEquals("Mix lagu", idLivePage.shelves[0].title)
    }

    @Test
    fun chartsSongShelfAndTwoRowArtistsMapToExpectedLayouts() {
        val chartsWithSongsAndTwoRowArtists = """
            {
              "contents": {
                "singleColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [
                            {
                              "musicShelfRenderer": {
                                "title": { "runs": [{ "text": "Lagu teratas" }] },
                                "contents": [
                                  {
                                    "musicResponsiveListItemRenderer": {
                                      "flexColumns": [
                                        {
                                          "musicResponsiveListItemFlexColumnRenderer": {
                                            "text": {
                                              "runs": [{
                                                "text": "Teh Hijau",
                                                "navigationEndpoint": { "watchEndpoint": { "videoId": "5F28ye50-Kc" } }
                                              }]
                                            }
                                          }
                                        },
                                        {
                                          "musicResponsiveListItemFlexColumnRenderer": {
                                            "text": { "runs": [{ "text": "Tulus" }] }
                                          }
                                        }
                                      ],
                                      "playlistItemData": { "videoId": "5F28ye50-Kc" }
                                    }
                                  }
                                ]
                              }
                            },
                            {
                              "musicCarouselShelfRenderer": {
                                "header": {
                                  "musicCarouselShelfBasicHeaderRenderer": {
                                    "title": { "runs": [{ "text": "Artis sorotan" }] }
                                  }
                                },
                                "contents": [
                                  {
                                    "musicTwoRowItemRenderer": {
                                      "title": { "runs": [{ "text": "Tulus" }] },
                                      "subtitle": { "runs": [{ "text": "Artis" }] },
                                      "navigationEndpoint": {
                                        "browseEndpoint": {
                                          "browseId": "UC_DHlXllTSMB8pTC38_leFg",
                                          "browseEndpointContextSupportedConfigs": {
                                            "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_ARTIST" }
                                          }
                                        }
                                      }
                                    }
                                  }
                                ]
                              }
                            }
                          ]
                        }
                      }
                    }
                  }]
                }
              }
            }
        """.trimIndent()

        val parsed = parseBrowseShelves(Json.parseToJsonElement(chartsWithSongsAndTwoRowArtists).jsonObject, HomeShelfSource.Charts)
        assertEquals(2, parsed.shelves.size)
        assertEquals(HomeShelfLayout.SongList, parsed.shelves[0].layout)
        assertEquals("yt:track:5F28ye50-Kc", (parsed.shelves[0].items.first() as HomeItem.Song).track.id)
        assertEquals(HomeShelfLayout.Artists, parsed.shelves[1].layout)
        assertEquals("UC_DHlXllTSMB8pTC38_leFg", (parsed.shelves[1].items.first() as HomeItem.Artist).artist.id)
    }

    @Test
    fun dedupesShelfTitlesAcrossSourcesKeepingHomeThenExploreThenCharts() = runTest {
        val dupExplore = REAL_EXPLORE_PAGE.replace("Album & single terbaru", "Rilis baru")
        val dupCharts = REAL_CHARTS_PAGE.replace("Tangga lagu video", "Trending")

        val catalog = home { _, body ->
            when {
                body.string("continuation") == "HOME_PAGE_2_TOKEN" -> json(REAL_HOME_PAGE_2)
                body.string("continuation") == "HOME_PAGE_3_TOKEN" -> json(REAL_HOME_PAGE_3)
                body.string("browseId") == "FEmusic_home" -> json(REAL_HOME_PAGE_1)
                body.string("browseId") == "FEmusic_explore" -> json(dupExplore)
                body.string("browseId") == "FEmusic_charts" -> json(dupCharts)
                else -> error("Unexpected request: $body")
            }
        }

        val feed = catalog.homeFeed(saveData = false)
        val rilisBaruShelves = feed.shelves.filter { it.title.equals("Rilis baru", ignoreCase = true) }
        val trendingShelves = feed.shelves.filter { it.title.equals("Trending", ignoreCase = true) }

        assertEquals(1, rilisBaruShelves.size)
        assertEquals(HomeShelfSource.Home, rilisBaruShelves.single().source)
        assertEquals(1, trendingShelves.size)
        assertEquals(HomeShelfSource.Explore, trendingShelves.single().source)
    }

    @Test
    fun saveDataSkipsExploreChartsAndContinuations() = runTest {
        val catalog = home { _, body ->
            when (body.string("browseId")) {
                "FEmusic_home" -> json(REAL_HOME_PAGE_1)
                else -> error("Should not request Explore, Charts, or continuations in saveData mode: $body")
            }
        }

        val feed = catalog.homeFeed(saveData = true)

        assertEquals(1, requests.size)
        assertEquals("FEmusic_home", requests.single().jsonBody().string("browseId"))
        assertEquals(listOf("Mix lagu", "Kesedihan musim hujan"), feed.shelves.map { it.title })
        assertEquals(emptyList(), feed.moods)
    }

    @Test
    fun partialFailuresAreSkippedAndTotalFailureThrowsIndonesianCatalogException() = runTest {
        // Explore gagal dan lanjutan halaman 2 Home gagal -> tetap mengembalikan Home halaman 1 + Charts.
        val partialCatalog = home { _, body ->
            when {
                body.string("continuation") == "HOME_PAGE_2_TOKEN" -> json("{}", HttpStatusCode.ServiceUnavailable)
                body.string("browseId") == "FEmusic_home" -> json(REAL_HOME_PAGE_1)
                body.string("browseId") == "FEmusic_explore" -> json("{}", HttpStatusCode.ServiceUnavailable)
                body.string("browseId") == "FEmusic_charts" -> json(REAL_CHARTS_PAGE)
                else -> error("Unexpected request: $body")
            }
        }
        val partialFeed = partialCatalog.homeFeed(saveData = false)
        assertTrue(partialFeed.shelves.any { it.source == HomeShelfSource.Home })
        assertTrue(partialFeed.shelves.any { it.source == HomeShelfSource.Charts })
        assertEquals(emptyList(), partialFeed.moods)

        // Home gagal, Charts gagal, Explore berhasil -> mengembalikan Explore + moods.
        val exploreOnlyCatalog = home { _, body ->
            when (body.string("browseId")) {
                "FEmusic_explore" -> json(REAL_EXPLORE_PAGE)
                else -> json("{}", HttpStatusCode.ServiceUnavailable)
            }
        }
        val exploreOnlyFeed = exploreOnlyCatalog.homeFeed(saveData = false)
        assertTrue(exploreOnlyFeed.shelves.isNotEmpty())
        assertTrue(exploreOnlyFeed.shelves.all { it.source == HomeShelfSource.Explore })
        assertTrue(exploreOnlyFeed.moods.isNotEmpty())

        // Semua sumber gagal (HTTP 429) -> CatalogException Kind.RateLimited terlokalisasi.
        val allFailCatalog = home { _, _ -> json("{}", HttpStatusCode.TooManyRequests) }
        val error = assertFailsWith<CatalogException> { allFailCatalog.homeFeed(saveData = false) }
        assertEquals(CatalogException.Kind.RateLimited, error.kind)
        assertTrue(error.userMessage.contains("YouTube Music", ignoreCase = true))
        assertTrue(error.userMessage(senada.app.AppLanguage.Indonesian).contains("YouTube Music", ignoreCase = true))
        assertFalse(error.userMessage.startsWith("Innertube HTTP"))

        // Mode hemat data saat Home gagal -> CatalogException Kind.Network terlokalisasi.
        val saveDataFail = home { _, _ -> json("{}", HttpStatusCode.ServiceUnavailable) }
        val netError = assertFailsWith<CatalogException> { saveDataFail.homeFeed(saveData = true) }
        assertEquals(CatalogException.Kind.Network, netError.kind)
        assertTrue(netError.userMessage.contains("Cannot connect", ignoreCase = true), netError.userMessage)
        assertTrue(netError.userMessage(senada.app.AppLanguage.Indonesian).contains("Tidak dapat terhubung", ignoreCase = true))
    }

    @Test
    fun homeContinuationsKeepFirstPageVisitorDataEvenIfExploreFinishesFirst() = runTest {
        val baseExplore = Json.parseToJsonElement(REAL_EXPLORE_PAGE).jsonObject
        val exploreWithDifferentVisitor = buildJsonObject {
            putJsonObject("responseContext") { put("visitorData", "VISITOR_FROM_EXPLORE") }
            put("contents", baseExplore["contents"]!!)
        }.toString()

        val catalog = home { _, body ->
            when {
                body.string("continuation") == "HOME_PAGE_2_TOKEN" -> json(REAL_HOME_PAGE_2)
                body.string("continuation") == "HOME_PAGE_3_TOKEN" -> json(REAL_HOME_PAGE_3)
                body.string("browseId") == "FEmusic_home" -> {
                    delay(50)
                    json(REAL_HOME_PAGE_1)
                }
                body.string("browseId") == "FEmusic_explore" -> json(exploreWithDifferentVisitor)
                body.string("browseId") == "FEmusic_charts" -> json(REAL_CHARTS_PAGE)
                else -> error("Unexpected request: $body")
            }
        }

        catalog.homeFeed(saveData = false)

        val contRequests = requests.filter { it.jsonBody().string("continuation") != null }
        assertEquals(2, contRequests.size)
        for (req in contRequests) {
            assertEquals("VISITOR_HOME_1", req.headers["X-Goog-Visitor-Id"])
            assertEquals("VISITOR_HOME_1", req.jsonBody().string("context", "client", "visitorData"))
        }
    }

    @Test
    fun dropsUnplayablePodcastEpisodeAndVideoOnlyShelves() {
        val unplayableJson = """
            {
              "contents": {
                "singleColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [
                            {
                              "musicCarouselShelfRenderer": {
                                "header": { "musicCarouselShelfBasicHeaderRenderer": { "title": { "runs": [{ "text": "Acara podcast" }] } } },
                                "contents": [
                                  {
                                    "musicTwoRowItemRenderer": {
                                      "title": { "runs": [{ "text": "Podcast A" }] },
                                      "navigationEndpoint": {
                                        "browseEndpoint": {
                                          "browseId": "MPSPPL123",
                                          "browseEndpointContextSupportedConfigs": {
                                            "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE" }
                                          }
                                        }
                                      }
                                    }
                                  }
                                ]
                              }
                            },
                            {
                              "musicCarouselShelfRenderer": {
                                "header": { "musicCarouselShelfBasicHeaderRenderer": { "title": { "runs": [{ "text": "Episode terbaru" }] } } },
                                "contents": [
                                  {
                                    "musicResponsiveListItemRenderer": {
                                      "flexColumns": [
                                        {
                                          "musicResponsiveListItemFlexColumnRenderer": {
                                            "text": {
                                              "runs": [{
                                                "text": "Episode 1",
                                                "navigationEndpoint": {
                                                  "watchEndpoint": {
                                                    "videoId": "ep1",
                                                    "watchEndpointMusicSupportedConfigs": {
                                                      "watchEndpointMusicConfig": { "musicVideoType": "MUSIC_VIDEO_TYPE_PODCAST_EPISODE" }
                                                    }
                                                  }
                                                }
                                              }]
                                            }
                                          }
                                        }
                                      ],
                                      "playlistItemData": { "videoId": "ep1" }
                                    }
                                  }
                                ]
                              }
                            }
                          ]
                        }
                      }
                    }
                  }]
                }
              }
            }
        """.trimIndent()

        val parsed = parseBrowseShelves(Json.parseToJsonElement(unplayableJson).jsonObject, HomeShelfSource.Home)
        assertEquals(emptyList(), parsed.shelves)
    }

    @Test
    fun moodBrowsesWithBrowseIdAndParamsAndReturnsCardShelves() = runTest {
        val catalog = home { _, _ -> json(REAL_MOOD_CATEGORY_PAGE) }
        val chip = MoodChip("Bepergian", "FEmusic_moods_and_genres_category", "ggMPOg1uX044Z2o5WERLckpU")

        val shelves = catalog.mood(chip)

        assertEquals(1, requests.size)
        val reqBody = requests.single().jsonBody()
        assertEquals("FEmusic_moods_and_genres_category", reqBody.string("browseId"))
        assertEquals("ggMPOg1uX044Z2o5WERLckpU", reqBody.string("params"))

        assertEquals(listOf("Musik asyik", "Relaxing commute"), shelves.map { it.title })
        assertTrue(shelves.all { it.layout == HomeShelfLayout.Cards })
        val firstPlaylist = shelves.first().items.first() as HomeItem.Playlist
        assertEquals("RDCLAK5uy_mkEwQuegHYB8_aAzBO8Q__6gGoaFblISw", firstPlaylist.id)
        assertEquals("Feel Good Hip Hop & R&B", firstPlaylist.title)
        assertEquals("Playlist • YouTube Music", firstPlaylist.subtitle)
        assertTrue(firstPlaylist.artworkUrl!!.contains("=w544-h544"))
    }
}
