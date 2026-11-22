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
import senada.catalog.AlbumSummary
import senada.catalog.CatalogException
import senada.model.Track

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
    fun artistPageReadsHeaderTopSongsAlbumLinksAndSimplifiedAlbums() {
        val detail = parseArtistPage("UC_DHlXllTSMB8pTC38_leFg", json(ARTIST_PAGE_TULUS))

        assertEquals("UC_DHlXllTSMB8pTC38_leFg", detail.id)
        assertEquals("Tulus", detail.name)
        assertTrue(detail.artworkUrl!!.contains("=w544-h544"))
        assertTrue(detail.monthlyListeners!!.contains("audiens bulanan"))

        assertEquals(
            listOf("Teh Hijau", "Jatuh Suka", "Monokrom", "Interaksi", "Hati-Hati di Jalan"),
            detail.topSongs.map { it.title },
        )
        assertEquals("OLAK5uy_mHgw7gB4UOePEByyIUPoXcCIWTbVade7w", detail.topSongsPlaylistId)
        val firstSong = detail.topSongs.first()
        assertEquals("yt:track:5F28ye50-Kc", firstSong.id)
        assertEquals(listOf("Tulus"), firstSong.artists)
        assertEquals(YOUTUBE_MUSIC_SOURCE, firstSong.source)
        assertNotNull(firstSong.artworkUrl)

        // Tautan album dibaca dari kolom baris lagu tanpa mengubah model Track.
        assertEquals("MPREb_RVAbNZdUasD", detail.topSongAlbums["yt:track:5F28ye50-Kc"]?.id)
        assertEquals("Teh Hijau", detail.topSongAlbums["yt:track:5F28ye50-Kc"]?.title)
        assertEquals("MPREb_7FNmIeIV9kf", detail.topSongAlbums["yt:track:qrnJL6P-QqQ"]?.id)
        assertEquals("Manusia", detail.topSongAlbums["yt:track:qrnJL6P-QqQ"]?.title)
        assertEquals("MPREb_4TEBtGA3cZb", detail.topSongAlbums["yt:track:1RrF6Ee_io0"]?.id)
        assertEquals("MPREb_7FNmIeIV9kf", detail.topSongAlbums["yt:track:-WF72CWKKEI"]?.id)
        assertEquals("MPREb_7FNmIeIV9kf", detail.topSongAlbums["yt:track:E7kHvjvU6JY"]?.id)

        // Album terpopuler: single "Teh Hijau" diabaikan karena album penuh ada; Manusia & Monokrom
        // dari lagu teratas (dedupe) lalu dilengkapi dari rak Album hingga 4 album.
        assertEquals(
            listOf("Manusia", "Monokrom", "Gajah", "Tulus"),
            detail.popularAlbums.map { it.title },
        )
        assertEquals("MPREb_7FNmIeIV9kf", detail.popularAlbums.first().id)
        assertEquals("2022", detail.popularAlbums.first().year)
        assertNotNull(detail.popularAlbums.first().artworkUrl)

        // Album terbaru: seluruh rak Album diurutkan tahun terbaru ke terlama (2019 naik sebelum 2016).
        assertEquals(
            listOf(
                "Manusia",
                "Langsung Dari Konser Monokrom Jakarta (Live)",
                "Monokrom",
                "Gajah",
                "Tulus",
            ),
            detail.newestAlbums.map { it.title },
        )
        assertEquals(listOf("2022", "2019", "2016", "2014", "2011"), detail.newestAlbums.map { it.year })
    }

    @Test
    fun artistPagePopularAlbumsOrdersBySongRankDedupesUsesSongFallbackAndFillsUp() {
        val reply = """
            {
              "header": {
                "musicImmersiveHeaderRenderer": {
                  "title": { "runs": [{ "text": "Artis Uji" }] }
                }
              },
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
                                  ${artistTopSongJson("s1", "Lagu 1", "MPREb_c", "Album C", "https://lh3.googleusercontent.com/s1=w120-h120")},
                                  ${artistTopSongJson("s2", "Lagu 2", "MPREb_unlisted", "Album Arsip", "https://lh3.googleusercontent.com/s2=w120-h120")},
                                  ${artistTopSongJson("s3", "Lagu 3", "MPREb_c", "Album C", "https://lh3.googleusercontent.com/s3=w120-h120")},
                                  ${artistTopSongJson("s4", "Lagu 4", "MPREb_single1", "Single Lepas", "https://lh3.googleusercontent.com/s4=w120-h120")}
                                ]
                              }
                            },
                            {
                              "musicCarouselShelfRenderer": {
                                "header": {
                                  "musicCarouselShelfBasicHeaderRenderer": {
                                    "title": { "runs": [{ "text": "Album" }] }
                                  }
                                },
                                "contents": [
                                  ${artistAlbumCardJson("MPREb_a", "Album A", "2024", "https://lh3.googleusercontent.com/a=w544-h544")},
                                  ${artistAlbumCardJson("MPREb_b", "Album B", "2020", "https://lh3.googleusercontent.com/b=w544-h544")},
                                  ${artistAlbumCardJson("MPREb_c", "Album C (Edisi Rak)", "2015", "https://lh3.googleusercontent.com/c=w544-h544")},
                                  ${artistAlbumCardJson("MPREb_d", "Album D", "2012", "https://lh3.googleusercontent.com/d=w544-h544")}
                                ]
                              }
                            },
                            {
                              "musicCarouselShelfRenderer": {
                                "header": {
                                  "musicCarouselShelfBasicHeaderRenderer": {
                                    "title": { "runs": [{ "text": "Single & EP" }] }
                                  }
                                },
                                "contents": [
                                  ${artistAlbumCardJson("MPREb_single1", "Single Lepas", "Single • 2025", "https://lh3.googleusercontent.com/single1=w544-h544")}
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

        val detail = parseArtistPage("UCtest", json(reply))

        assertNull(detail.topSongsPlaylistId)
        assertEquals(
            listOf("MPREb_c", "MPREb_unlisted", "MPREb_a", "MPREb_b"),
            detail.popularAlbums.map { it.id },
        )
        // Album yang ada di rak memakai judul, tahun, dan sampul dari rak.
        assertEquals("Album C (Edisi Rak)", detail.popularAlbums[0].title)
        assertEquals("2015", detail.popularAlbums[0].year)
        assertEquals("https://lh3.googleusercontent.com/c=w544-h544", detail.popularAlbums[0].artworkUrl)
        // Album yang tidak ada di rak memakai info dari baris lagu.
        assertEquals("Album Arsip", detail.popularAlbums[1].title)
        assertEquals("https://lh3.googleusercontent.com/s2=w544-h544", detail.popularAlbums[1].artworkUrl)

        // Bila lagu terpopuler sudah menghasilkan >= 4 album berbeda, tidak ditambah dari sisa rak.
        val fiveSongs = (1..5).map { Track("yt:track:t$it", "Lagu $it", listOf("Artis"), source = YOUTUBE_MUSIC_SOURCE) }
        val fiveSongAlbums = (1..5).associate { i ->
            "yt:track:t$i" to AlbumSummary("MPREb_rank_$i", "Album Rank $i")
        }
        val shelf = listOf(
            AlbumSummary("MPREb_extra", "Album Tambahan", year = "2025"),
            AlbumSummary("MPREb_rank_1", "Album Rank 1", year = "2019"),
        )
        val rankedFive = buildPopularAlbums(fiveSongs, fiveSongAlbums, shelf, emptyList())
        assertEquals(
            listOf("MPREb_rank_1", "MPREb_rank_2", "MPREb_rank_3", "MPREb_rank_4", "MPREb_rank_5"),
            rankedFive.map { it.id },
        )
        assertEquals("2019", rankedFive.first().year)
    }

    @Test
    fun artistPageFallsBackToSinglesAndEpsOnlyWhenNoProperAlbumsExist() {
        val reply = """
            {
              "header": {
                "musicImmersiveHeaderRenderer": {
                  "title": { "runs": [{ "text": "Artis Single" }] }
                }
              },
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
                                  ${artistTopSongJson("s1", "Lagu C", "MPREb_sc", "Single C", "https://lh3.googleusercontent.com/s1=w120-h120")},
                                  ${artistTopSongJson("s2", "Lagu B", "MPREb_sb", "Single B", "https://lh3.googleusercontent.com/s2=w120-h120")},
                                  ${artistTopSongJson("s3", "Lagu C2", "MPREb_sc", "Single C", "https://lh3.googleusercontent.com/s3=w120-h120")}
                                ]
                              }
                            },
                            {
                              "musicCarouselShelfRenderer": {
                                "header": {
                                  "musicCarouselShelfBasicHeaderRenderer": {
                                    "title": { "runs": [{ "text": "Singles & EPs" }] }
                                  }
                                },
                                "contents": [
                                  ${artistAlbumCardJson("MPREb_sa", "Single A", "Single • 2025")},
                                  ${artistAlbumCardJson("MPREb_sb", "Single B", "EP • 2023")},
                                  ${artistAlbumCardJson("MPREb_sc", "Single C", "Single • 2024")},
                                  ${artistAlbumCardJson("MPREb_sd", "Single D", "Single • 2022")},
                                  ${artistAlbumCardJson("MPREb_se", "Single E", "Single • 2021")}
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

        val detail = parseArtistPage("UCsingle", json(reply))

        assertEquals(
            listOf("Single C", "Single B", "Single A", "Single D"),
            detail.popularAlbums.map { it.title },
        )
        assertEquals("2024", detail.popularAlbums.first().year)
        assertEquals(
            listOf("Single A", "Single C", "Single B", "Single D", "Single E"),
            detail.newestAlbums.map { it.title },
        )
    }

    @Test
    fun artistPageNewestAlbumsOrdersByYearDescUnknownLastAndKeepsShelfOrderOnTies() {
        val reply = """
            {
              "header": {
                "musicImmersiveHeaderRenderer": {
                  "title": { "runs": [{ "text": "Artis Tahun" }] }
                }
              },
              "contents": {
                "singleColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [
                            {
                              "musicCarouselShelfRenderer": {
                                "header": {
                                  "musicCarouselShelfBasicHeaderRenderer": {
                                    "title": { "runs": [{ "text": "Albums" }] }
                                  }
                                },
                                "contents": [
                                  ${artistAlbumCardJson("MPREb_u1", "Tanpa Tahun 1", "Album")},
                                  ${artistAlbumCardJson("MPREb_20a", "Album 2020 A", "2020")},
                                  ${artistAlbumCardJson("MPREb_23", "Album 2023", "2023")},
                                  ${artistAlbumCardJson("MPREb_20b", "Album 2020 B", "Album • 2020")},
                                  ${artistAlbumCardJson("MPREb_u2", "Tanpa Tahun 2", "Kompilasi")},
                                  ${artistAlbumCardJson("MPREb_18", "Album 2018", "2018")}
                                ]
                              }
                            },
                            {
                              "musicCarouselShelfRenderer": {
                                "header": {
                                  "musicCarouselShelfBasicHeaderRenderer": {
                                    "title": { "runs": [{ "text": "Featured on" }] }
                                  }
                                },
                                "contents": [
                                  ${artistAlbumCardJson("MPREb_feat", "Album Kolaborasi Luar", "2026")}
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

        val detail = parseArtistPage("UCyear", json(reply))

        assertEquals(
            listOf("Album 2023", "Album 2020 A", "Album 2020 B", "Album 2018", "Tanpa Tahun 1", "Tanpa Tahun 2"),
            detail.newestAlbums.map { it.title },
        )
        assertTrue(detail.popularAlbums.none { it.id == "MPREb_feat" })
        assertTrue(detail.newestAlbums.none { it.id == "MPREb_feat" })
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
        assertNull(detail.topSongsPlaylistId)
        assertEquals(emptyMap(), detail.topSongAlbums)
        assertEquals(emptyList(), detail.popularAlbums)
        assertEquals(emptyList(), detail.newestAlbums)
    }

    private fun artistTopSongJson(
        videoId: String,
        title: String,
        albumId: String,
        albumTitle: String,
        thumbUrl: String,
    ): String = """
        {
          "musicResponsiveListItemRenderer": {
            "thumbnail": {
              "musicThumbnailRenderer": {
                "thumbnail": {
                  "thumbnails": [{ "url": "$thumbUrl", "width": 120, "height": 120 }]
                }
              }
            },
            "flexColumns": [
              { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "$title" }] } } },
              { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Artis" }] } } },
              {
                "musicResponsiveListItemFlexColumnRenderer": {
                  "text": {
                    "runs": [{
                      "text": "$albumTitle",
                      "navigationEndpoint": {
                        "browseEndpoint": {
                          "browseId": "$albumId",
                          "browseEndpointContextSupportedConfigs": {
                            "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_ALBUM" }
                          }
                        }
                      }
                    }]
                  }
                }
              }
            ],
            "playlistItemData": { "videoId": "$videoId" }
          }
        }
    """.trimIndent()

    private fun artistAlbumCardJson(
        albumId: String,
        title: String,
        subtitle: String,
        thumbUrl: String = "https://lh3.googleusercontent.com/$albumId=w544-h544",
    ): String = """
        {
          "musicTwoRowItemRenderer": {
            "thumbnailRenderer": {
              "musicThumbnailRenderer": {
                "thumbnail": {
                  "thumbnails": [{ "url": "$thumbUrl", "width": 544, "height": 544 }]
                }
              }
            },
            "title": { "runs": [{ "text": "$title" }] },
            "subtitle": { "runs": [{ "text": "$subtitle" }] },
            "navigationEndpoint": {
              "browseEndpoint": {
                "browseId": "$albumId",
                "browseEndpointContextSupportedConfigs": {
                  "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_ALBUM" }
                }
              }
            }
          }
        }
    """.trimIndent()

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
