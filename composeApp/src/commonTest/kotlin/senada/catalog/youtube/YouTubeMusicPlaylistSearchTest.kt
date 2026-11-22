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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import senada.catalog.PlaylistImportException

class YouTubeMusicPlaylistSearchTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun catalog(reply: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): YouTubeMusicCatalog {
        val client = InnertubeClient(HttpClient(MockEngine { request -> requests += request; reply(request) }))
        return YouTubeMusicCatalog(client)
    }

    private fun MockRequestHandleScope.json(text: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(text, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.jsonBody(): JsonObject =
        Json.parseToJsonElement((body as TextContent).text).jsonObject

    @Test
    fun searchesFeaturedAndCommunityPlaylistsAndDeduplicatesMergedResults() = runTest {
        val cat = catalog { request ->
            val body = request.jsonBody()
            when (body.string("params")) {
                InnertubeClient.FEATURED_PLAYLISTS_FILTER -> json(FEATURED_PLAYLIST_SEARCH_JSON)
                InnertubeClient.COMMUNITY_PLAYLISTS_FILTER -> json(COMMUNITY_PLAYLIST_SEARCH_JSON)
                else -> error("Unexpected params: ${body.string("params")}")
            }
        }

        val page = cat.searchPlaylists("chill")

        assertEquals(2, requests.size)
        assertTrue(requests.any { it.jsonBody().string("params") == InnertubeClient.FEATURED_PLAYLISTS_FILTER })
        assertTrue(requests.any { it.jsonBody().string("params") == InnertubeClient.COMMUNITY_PLAYLISTS_FILTER })
        // Duplikat ID RDCLAK_DUP hanya muncul sekali.
        assertEquals(listOf("RDCLAK_FEAT_1", "PL_COMM_1", "RDCLAK_DUP"), page.items.map { it.id })

        val featured = page.items[0]
        assertEquals("Chill Malam", featured.title)
        assertEquals("YouTube Music • 86 lagu", featured.subtitle)
        assertEquals("https://lh3.googleusercontent.com/feat1=w544-h544-l90-rj", featured.artworkUrl)

        val community = page.items[1]
        assertEquals("Lofi Belajar", community.title)
        assertEquals("Alphasvara • 1,7 jt x ditonton", community.subtitle)
        assertEquals("https://lh3.googleusercontent.com/comm1=w544-h544-l90-rj", community.artworkUrl)

        assertEquals("COMM_CONT_2", page.nextPageToken)
    }

    @Test
    fun parsesPlaylistSearchContinuationPage() = runTest {
        val cat = catalog { request ->
            val body = request.jsonBody()
            assertEquals("SEARCH_CONT_2", body.string("continuation"))
            json(SEARCH_CONTINUATION_JSON)
        }

        val page = cat.searchPlaylists("chill", pageToken = "SEARCH_CONT_2")

        assertEquals(1, page.items.size)
        assertEquals("PL_COMM_2", page.items[0].id)
        assertEquals("Indie Senja", page.items[0].title)
        assertEquals("Studio Senja • 42 lagu", page.items[0].subtitle)
        assertEquals("SEARCH_CONT_3", page.nextPageToken)
    }

    @Test
    fun parsesBrowsePlaylistDetailHeaderOwnerSongCountAndContinuation() = runTest {
        val cat = catalog { request ->
            val body = request.jsonBody()
            when (body.string("continuation")) {
                null -> {
                    assertEquals("VLRDCLAK5uy_chill", body.string("browseId"))
                    json(BROWSE_DETAIL_PAGE_1)
                }
                "CONT_DETAIL_2" -> json(BROWSE_DETAIL_PAGE_2)
                else -> error("Unexpected body: $body")
            }
        }

        val detail = cat.playlist("RDCLAK5uy_chill")

        assertEquals("RDCLAK5uy_chill", detail.id)
        assertEquals("Chill Nusantara", detail.title)
        assertEquals("YouTube Music", detail.owner)
        assertEquals("84 lagu", detail.songCountText)
        assertEquals("https://lh3.googleusercontent.com/cover=w544-h544-l90-rj", detail.artworkUrl)
        assertFalse(detail.truncated)
        assertEquals(listOf("yt:track:v1", "yt:track:v2"), detail.tracks.map { it.id })
    }

    @Test
    fun fallsBackToNextPlaylistForMixWhenBrowseHasNoTracks() = runTest {
        val cat = catalog { request ->
            when {
                request.url.encodedPath.endsWith("/browse") -> json(EMPTY_BROWSE_JSON)
                request.url.encodedPath.endsWith("/next") -> {
                    val body = request.jsonBody()
                    assertEquals("RDAMVM5F28ye50-Kc", body.string("playlistId"))
                    assertEquals("true", body.at("isAudioOnly")?.toString())
                    json(NEXT_MIX_JSON)
                }
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }

        val detail = cat.playlist("RDAMVM5F28ye50-Kc")

        assertEquals("RDAMVM5F28ye50-Kc", detail.id)
        assertEquals("Mix untuk Kamu", detail.title)
        assertEquals("2 lagu", detail.songCountText)
        assertEquals(2, detail.tracks.size)
        val first = detail.tracks[0]
        assertEquals("yt:track:1RrF6Ee_io0", first.id)
        assertEquals("Monokrom", first.title)
        assertEquals(listOf("Tulus"), first.artists)
        assertEquals(215_000L, first.durationMs)
        assertEquals(YOUTUBE_MUSIC_SOURCE, first.source)
        val second = detail.tracks[1]
        assertEquals("yt:track:abc999", second.id)
        assertEquals(listOf("Nadhif Basalamah"), second.artists)
        assertNull(second.durationMs)
    }

    @Test
    fun fallsBackToNextPlaylistWhenMixBrowseFailsWithHttp400() = runTest {
        val cat = catalog { request ->
            when {
                request.url.encodedPath.endsWith("/browse") -> json("{}", HttpStatusCode.BadRequest)
                request.url.encodedPath.endsWith("/next") -> json(NEXT_MIX_JSON)
                else -> error("Unexpected path")
            }
        }

        val detail = cat.playlist("RD12345")
        assertEquals(2, detail.tracks.size)
    }

    @Test
    fun throwsForUnreachableNonMixPlaylistWithoutCallingNext() = runTest {
        val cat = catalog { request ->
            assertTrue(request.url.encodedPath.endsWith("/browse"))
            json("{}", HttpStatusCode.NotFound)
        }

        assertFailsWith<PlaylistImportException> {
            cat.playlist("PLprivate123")
        }
        assertEquals(1, requests.size)
    }
}

private const val FEATURED_PLAYLIST_SEARCH_JSON = """
{
  "contents": {
    "tabbedSearchResultsRenderer": {
      "tabs": [{
        "tabRenderer": {
          "content": {
            "sectionListRenderer": {
              "contents": [{
                "musicShelfRenderer": {
                  "continuations": [{"nextContinuationData": {"continuation": "FEAT_CONT_2"}}],
                  "contents": [
                    {
                      "musicResponsiveListItemRenderer": {
                        "navigationEndpoint": {
                          "browseEndpoint": {
                            "browseId": "VLRDCLAK_FEAT_1",
                            "browseEndpointContextSupportedConfigs": {
                              "browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_PLAYLIST"}
                            }
                          }
                        },
                        "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
                          {"url": "https://lh3.googleusercontent.com/feat1=w120-h120-l90-rj"}
                        ]}}},
                        "flexColumns": [
                          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Chill Malam"}]}}},
                          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
                            {"text": "Playlist"}, {"text": " • "}, {"text": "YouTube Music"}, {"text": " • "}, {"text": "86 lagu"}
                          ]}}}
                        ]
                      }
                    },
                    {
                      "musicResponsiveListItemRenderer": {
                        "navigationEndpoint": {"browseEndpoint": {"browseId": "VLRDCLAK_DUP"}},
                        "flexColumns": [
                          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Sama"}]}}},
                          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "YouTube Music"}]}}}
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
"""

private const val COMMUNITY_PLAYLIST_SEARCH_JSON = """
{
  "contents": {
    "tabbedSearchResultsRenderer": {
      "tabs": [{
        "tabRenderer": {
          "content": {
            "sectionListRenderer": {
              "contents": [{
                "musicShelfRenderer": {
                  "continuations": [{"nextContinuationData": {"continuation": "COMM_CONT_2"}}],
                  "contents": [
                    {
                      "musicResponsiveListItemRenderer": {
                        "navigationEndpoint": {
                          "browseEndpoint": {
                            "browseId": "VLPL_COMM_1",
                            "browseEndpointContextSupportedConfigs": {
                              "browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_PLAYLIST"}
                            }
                          }
                        },
                        "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
                          {"url": "//lh3.googleusercontent.com/comm1=w60-h60-l90-rj"}
                        ]}}},
                        "flexColumns": [
                          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Lofi Belajar"}]}}},
                          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
                            {"text": "Alphasvara"}, {"text": " • "}, {"text": "1,7 jt x ditonton"}
                          ]}}}
                        ]
                      }
                    },
                    {
                      "musicResponsiveListItemRenderer": {
                        "navigationEndpoint": {"browseEndpoint": {"browseId": "VLRDCLAK_DUP"}},
                        "flexColumns": [
                          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Duplikat"}]}}},
                          {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Pemilik"}]}}}
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
"""

private const val SEARCH_CONTINUATION_JSON = """
{
  "continuationContents": {
    "musicShelfContinuation": {
      "continuations": [{"nextContinuationData": {"continuation": "SEARCH_CONT_3"}}],
      "contents": [
        {
          "musicResponsiveListItemRenderer": {
            "navigationEndpoint": {
              "browseEndpoint": {
                "browseId": "VLPL_COMM_2",
                "browseEndpointContextSupportedConfigs": {
                  "browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_PLAYLIST"}
                }
              }
            },
            "flexColumns": [
              {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Indie Senja"}]}}},
              {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
                {"text": "Studio Senja"}, {"text": " • "}, {"text": "42 lagu"}
              ]}}}
            ]
          }
        }
      ]
    }
  }
}
"""

private const val BROWSE_DETAIL_PAGE_1 = """
{
  "contents": {
    "twoColumnBrowseResultsRenderer": {
      "tabs": [{
        "tabRenderer": {
          "content": {
            "sectionListRenderer": {
              "contents": [{
                "musicResponsiveHeaderRenderer": {
                  "title": {"runs": [{"text": "Chill Nusantara"}]},
                  "facepile": {"avatarStackViewModel": {"text": {"content": "YouTube Music"}}},
                  "secondSubtitle": {"runs": [{"text": "84 lagu"}, {"text": " • "}, {"text": "4 jam, 36 menit"}]},
                  "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [
                    {"url": "https://lh3.googleusercontent.com/cover=w120-h120-l90-rj"}
                  ]}}}
                }
              }]
            }
          }
        }
      }],
      "secondaryContents": {
        "sectionListRenderer": {
          "contents": [{
            "musicPlaylistShelfRenderer": {
              "contents": [
                {
                  "musicResponsiveListItemRenderer": {
                    "playlistItemData": {"videoId": "v1"},
                    "flexColumns": [
                      {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Lagu Satu"}]}}},
                      {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Artis A"}]}}}
                    ],
                    "fixedColumns": [
                      {"musicResponsiveListItemFixedColumnRenderer": {"text": {"runs": [{"text": "3:15"}]}}}
                    ]
                  }
                },
                {
                  "continuationItemRenderer": {
                    "continuationEndpoint": {"continuationCommand": {"token": "CONT_DETAIL_2"}}
                  }
                }
              ]
            }
          }]
        }
      }
    }
  }
}
"""

private const val BROWSE_DETAIL_PAGE_2 = """
{
  "onResponseReceivedActions": [{
    "appendContinuationItemsAction": {
      "continuationItems": [
        {
          "musicResponsiveListItemRenderer": {
            "playlistItemData": {"videoId": "v2"},
            "flexColumns": [
              {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Lagu Dua"}]}}},
              {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Artis B"}]}}}
            ],
            "fixedColumns": [
              {"musicResponsiveListItemFixedColumnRenderer": {"text": {"runs": [{"text": "4:00"}]}}}
            ]
          }
        }
      ]
    }
  }]
}
"""

private const val EMPTY_BROWSE_JSON = """{"contents": {}}"""

private const val NEXT_MIX_JSON = """
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
                      "title": "Mix untuk Kamu",
                      "contents": [
                        {
                          "playlistPanelVideoRenderer": {
                            "videoId": "1RrF6Ee_io0",
                            "title": {"runs": [{"text": "Monokrom"}]},
                            "longBylineText": {"runs": [
                              {
                                "text": "Tulus",
                                "navigationEndpoint": {
                                  "browseEndpoint": {
                                    "browseId": "UC_tulus",
                                    "browseEndpointContextSupportedConfigs": {
                                      "browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_ARTIST"}
                                    }
                                  }
                                }
                              },
                              {"text": " • "},
                              {
                                "text": "Monokrom",
                                "navigationEndpoint": {
                                  "browseEndpoint": {
                                    "browseId": "MPRE_mono",
                                    "browseEndpointContextSupportedConfigs": {
                                      "browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_ALBUM"}
                                    }
                                  }
                                }
                              },
                              {"text": " • "},
                              {"text": "2016"}
                            ]},
                            "lengthText": {"runs": [{"text": "3.35"}]},
                            "thumbnail": {"thumbnails": [
                              {"url": "https://yt3.googleusercontent.com/mix1=w226-h226-l90-rj"}
                            ]}
                          }
                        },
                        {
                          "playlistPanelVideoRenderer": {
                            "videoId": "abc999",
                            "title": {"runs": [{"text": "Kota Ini"}]},
                            "shortBylineText": {"runs": [{"text": "Nadhif Basalamah"}]}
                          }
                        }
                      ]
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
"""
