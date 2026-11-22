package senada.catalog.youtube

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import senada.catalog.CatalogException

class YouTubeMusicBrowseParsingTest {
    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun MockRequestHandleScope.jsonResponse(text: String) =
        respond(text, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.jsonBody(): JsonObject =
        Json.parseToJsonElement((body as TextContent).text).jsonObject

    @Test
    fun artistSearchReadsArtistsArtworkAndContinuation() {
        val page = parseArtistSearchPage(json(SEARCH_ARTISTS_PAGE_1))

        assertEquals(listOf("Tulus", "Tulus M"), page.items.map { it.name })
        val first = page.items.first()
        assertEquals("UC_DHlXllTSMB8pTC38_leFg", first.id)
        assertTrue(first.subtitle!!.contains("Artis"))
        assertTrue(first.artworkUrl!!.contains("=w544-h544"))
        assertEquals("ARTIST_PAGE_2_TOKEN", page.nextPageToken)
    }

    @Test
    fun artistSearchContinuationReadsNextPage() {
        val page = parseArtistSearchPage(json(SEARCH_ARTISTS_PAGE_2))

        assertEquals(1, page.items.size)
        assertEquals("UCexampleYuraYunita", page.items.first().id)
        assertEquals("Yura Yunita", page.items.first().name)
        assertNull(page.nextPageToken)
    }

    @Test
    fun albumSearchReadsAlbumsYearAndContinuation() {
        val page = parseAlbumSearchPage(json(SEARCH_ALBUMS_PAGE_1))

        assertEquals(listOf("Pamit", "Monokrom"), page.items.map { it.title })
        val monokrom = page.items[1]
        assertEquals("MPREb_4TEBtGA3cZb", monokrom.id)
        assertEquals("2016", monokrom.year)
        assertTrue(monokrom.subtitle!!.contains("Tulus"))
        assertTrue(monokrom.artworkUrl!!.contains("=w544-h544"))
        assertEquals("ALBUM_PAGE_2_TOKEN", page.nextPageToken)
    }

    @Test
    fun albumSearchContinuationReadsNextPage() {
        val page = parseAlbumSearchPage(json(SEARCH_ALBUMS_PAGE_2))

        assertEquals(1, page.items.size)
        val gajah = page.items.first()
        assertEquals("MPREb_exampleGajah", gajah.id)
        assertEquals("Gajah", gajah.title)
        assertEquals("2014", gajah.year)
        assertNull(page.nextPageToken)
    }

    @Test
    fun artistPageReadsHeaderTopSongsAndReleasesWithoutRecommendations() {
        val detail = parseArtistPage("UC_DHlXllTSMB8pTC38_leFg", json(ARTIST_PAGE_TULUS))

        assertEquals("UC_DHlXllTSMB8pTC38_leFg", detail.id)
        assertEquals("Tulus", detail.name)
        assertTrue(detail.artworkUrl!!.contains("=w544-h544"))
        assertTrue(detail.monthlyListeners!!.contains("audiens bulanan"))

        assertEquals(listOf("Teh Hijau", "Jatuh Suka"), detail.topSongs.map { it.title })
        val firstSong = detail.topSongs.first()
        assertEquals("yt:track:5F28ye50-Kc", firstSong.id)
        assertEquals(listOf("Tulus"), firstSong.artists)
        assertEquals(YOUTUBE_MUSIC_SOURCE, firstSong.source)
        assertNotNull(firstSong.artworkUrl)

        // Hanya rak album dan single/EP yang diambil; video dan artis serupa diabaikan.
        assertEquals(listOf("Album", "Single & EP"), detail.releases.map { it.title })
        assertEquals(listOf("Manusia", "Monokrom"), detail.releases[0].items.map { it.title })
        assertEquals("MPREb_7FNmIeIV9kf", detail.releases[0].items.first().id)
        assertEquals("2022", detail.releases[0].items.first().year)
        assertEquals(listOf("Teh Hijau", "Adaptasi"), detail.releases[1].items.map { it.title })
        assertEquals("2026", detail.releases[1].items.first().year)
    }

    @Test
    fun artistPageSupportsVisualHeaderForegroundThumbnail() {
        val reply = """
            {
              "header": {
                "musicVisualHeaderRenderer": {
                  "title": { "runs": [{ "text": "Nadin Amizah" }] },
                  "foregroundThumbnail": {
                    "musicThumbnailRenderer": {
                      "thumbnail": {
                        "thumbnails": [
                          { "url": "https://lh3.googleusercontent.com/nadin=w200-h200", "width": 200, "height": 200 }
                        ]
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val detail = parseArtistPage("UCnadin", json(reply))
        assertEquals("Nadin Amizah", detail.name)
        assertEquals("https://lh3.googleusercontent.com/nadin=w544-h544", detail.artworkUrl)
        assertEquals(emptyList(), detail.topSongs)
        assertEquals(emptyList(), detail.releases)
    }

    @Test
    fun albumPageReadsMetadataAndTracksInOrderWithInheritedArtistAndCover() {
        val album = parseAlbumPage("MPREb_7FNmIeIV9kf", json(ALBUM_PAGE_MANUSIA))

        assertEquals("MPREb_7FNmIeIV9kf", album.id)
        assertEquals("Manusia", album.title)
        assertEquals(listOf("Tulus"), album.artists)
        assertEquals("UC_DHlXllTSMB8pTC38_leFg", album.artistId)
        assertEquals("2022", album.year)
        assertEquals("Album", album.typeLabel)
        assertTrue(album.artworkUrl!!.contains("=w544-h544"))

        assertEquals(listOf("Tujuh Belas", "Kelana", "Remedi"), album.tracks.map { it.title })
        assertEquals(
            listOf("yt:track:cA7Gzh2ISL0", "yt:track:Wc2ykyBEgIw", "yt:track:12LLF5HZvh8"),
            album.tracks.map { it.id },
        )
        assertEquals(listOf(255_000L, 192_000L, 248_000L), album.tracks.map { it.durationMs })
        album.tracks.forEach { track ->
            assertEquals(listOf("Tulus"), track.artists)
            assertEquals(album.artworkUrl, track.artworkUrl)
            assertEquals(YOUTUBE_MUSIC_SOURCE, track.source)
        }
    }

    @Test
    fun albumPageSkipsUnplayableRowsAndReadsOverlayVideoIdAndTrackArtists() {
        val reply = """
            {
              "header": {
                "musicDetailHeaderRenderer": {
                  "title": { "runs": [{ "text": "Kompilasi Senja" }] },
                  "subtitle": { "runs": [{ "text": "Album" }, { "text": " • " }, { "text": "Various Artists" }] }
                }
              },
              "contents": {
                "singleColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [{
                            "musicShelfRenderer": {
                              "contents": [
                                {
                                  "musicResponsiveListItemRenderer": {
                                    "flexColumns": [
                                      { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Tidak Tersedia" }] } } }
                                    ]
                                  }
                                },
                                {
                                  "musicResponsiveListItemRenderer": {
                                    "overlay": {
                                      "musicItemThumbnailOverlayRenderer": {
                                        "content": {
                                          "musicPlayButtonRenderer": {
                                            "playNavigationEndpoint": {
                                              "watchEndpoint": { "videoId": "vidOverlay1" }
                                            }
                                          }
                                        }
                                      }
                                    },
                                    "flexColumns": [
                                      { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Lagu Duet" }] } } },
                                      {
                                        "musicResponsiveListItemFlexColumnRenderer": {
                                          "text": {
                                            "runs": [
                                              {
                                                "text": "Tulus",
                                                "navigationEndpoint": {
                                                  "browseEndpoint": {
                                                    "browseId": "UC1",
                                                    "browseEndpointContextSupportedConfigs": {
                                                      "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_ARTIST" }
                                                    }
                                                  }
                                                }
                                              },
                                              { "text": " & " },
                                              {
                                                "text": "Yura Yunita",
                                                "navigationEndpoint": {
                                                  "browseEndpoint": {
                                                    "browseId": "UC2",
                                                    "browseEndpointContextSupportedConfigs": {
                                                      "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_ARTIST" }
                                                    }
                                                  }
                                                }
                                              }
                                            ]
                                          }
                                        }
                                      }
                                    ],
                                    "fixedColumns": [
                                      { "musicResponsiveListItemFixedColumnRenderer": { "text": { "runs": [{ "text": "3:40" }] } } }
                                    ]
                                  }
                                }
                              ]
                            }
                          }]
                        }
                      }
                    }
                  }]
                }
              }
            }
        """.trimIndent()

        val album = parseAlbumPage("MPREb_comp", json(reply))
        assertEquals("Kompilasi Senja", album.title)
        assertEquals(listOf("Various Artists"), album.artists)
        assertNull(album.year)
        assertEquals(1, album.tracks.size)
        val track = album.tracks.first()
        assertEquals("yt:track:vidOverlay1", track.id)
        assertEquals(listOf("Tulus", "Yura Yunita"), track.artists)
        assertEquals(220_000L, track.durationMs)
    }

    @Test
    fun emptyArtistOrAlbumRepliesFailAsUnreadable() {
        val empty = json("{}")
        assertEquals(
            CatalogException.Kind.Unreadable,
            assertFailsWith<CatalogException> { parseArtistPage("UCx", empty) }.kind,
        )
        assertEquals(
            CatalogException.Kind.Unreadable,
            assertFailsWith<CatalogException> { parseAlbumPage("MPREb_x", empty) }.kind,
        )
    }

    @Test
    fun catalogSendsArtistAlbumFiltersAndBrowseIds() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = InnertubeClient(
            HttpClient(
                MockEngine { request ->
                    requests += request
                    val body = request.jsonBody()
                    val path = request.url.encodedPath
                    when {
                        path.endsWith("/browse") && body.string("browseId") == "UC_DHlXllTSMB8pTC38_leFg" ->
                            jsonResponse(ARTIST_PAGE_TULUS)
                        path.endsWith("/browse") && body.string("browseId") == "MPREb_7FNmIeIV9kf" ->
                            jsonResponse(ALBUM_PAGE_MANUSIA)
                        request.url.parameters["ctoken"] == "ARTIST_PAGE_2_TOKEN" ->
                            jsonResponse(SEARCH_ARTISTS_PAGE_2)
                        request.url.parameters["ctoken"] == "ALBUM_PAGE_2_TOKEN" ->
                            jsonResponse(SEARCH_ALBUMS_PAGE_2)
                        body.string("params") == InnertubeClient.ARTISTS_FILTER ->
                            jsonResponse(SEARCH_ARTISTS_PAGE_1)
                        else -> jsonResponse(SEARCH_ALBUMS_PAGE_1)
                    }
                },
            ),
        )
        val catalog = YouTubeMusicCatalog(client)

        val artists1 = catalog.searchArtists(" tulus ")
        val artists2 = catalog.searchArtists("tulus", artists1.nextPageToken)
        val albums1 = catalog.searchAlbums("tulus")
        val albums2 = catalog.searchAlbums("tulus", albums1.nextPageToken)
        val artist = catalog.artist("UC_DHlXllTSMB8pTC38_leFg")
        val album = catalog.album("MPREb_7FNmIeIV9kf")

        assertEquals(2, artists1.items.size)
        assertEquals(1, artists2.items.size)
        assertEquals(2, albums1.items.size)
        assertEquals(1, albums2.items.size)
        assertEquals("Tulus", artist.name)
        assertEquals("Manusia", album.title)

        assertEquals(InnertubeClient.ARTISTS_FILTER, requests[0].jsonBody().string("params"))
        assertEquals("ARTIST_PAGE_2_TOKEN", requests[1].url.parameters["ctoken"])
        assertEquals(InnertubeClient.ALBUMS_FILTER, requests[2].jsonBody().string("params"))
        assertEquals("ALBUM_PAGE_2_TOKEN", requests[3].url.parameters["ctoken"])
        assertTrue(requests[4].url.encodedPath.endsWith("/browse"))
        assertEquals("UC_DHlXllTSMB8pTC38_leFg", requests[4].jsonBody().string("browseId"))
        assertTrue(requests[5].url.encodedPath.endsWith("/browse"))
        assertEquals("MPREb_7FNmIeIV9kf", requests[5].jsonBody().string("browseId"))
    }

    @Test
    fun blankArtistAndAlbumQueriesSkipNetwork() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val catalog = YouTubeMusicCatalog(
            InnertubeClient(HttpClient(MockEngine { request -> requests += request; jsonResponse("{}") })),
        )

        assertEquals(emptyList(), catalog.searchArtists("   ").items)
        assertEquals(emptyList(), catalog.searchAlbums("").items)
        assertEquals(emptyList(), requests)
    }
}
