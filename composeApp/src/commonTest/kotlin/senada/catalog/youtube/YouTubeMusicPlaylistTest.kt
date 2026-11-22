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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import senada.catalog.PlaylistImportException

class YouTubeMusicPlaylistTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun innertube(reply: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        InnertubeClient(HttpClient(MockEngine { request -> requests += request; reply(request) }))

    private fun MockRequestHandleScope.json(text: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(text, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.jsonBody(): JsonObject =
        Json.parseToJsonElement((body as TextContent).text).jsonObject

    @Test
    fun parsesPlaylistHeaderFixedColumnDurationsAndContinuationPages() = runTest {
        val client = innertube { request ->
            val body = request.jsonBody()
            when (body.string("continuation")) {
                null -> json(PLAYLIST_PAGE_1)
                "CONT_TOKEN_2" -> json(PLAYLIST_PAGE_2)
                else -> error("Unexpected continuation: $body")
            }
        }
        val playlists = YouTubeMusicPlaylists(client)

        val playlist = playlists.fetchPlaylist("PLtest123")

        assertEquals("Lagu Santai Sore", playlist.name)
        assertEquals(3, playlist.tracks.size)
        assertEquals(listOf("Lagu Dihapus"), playlist.skippedTitles)
        assertFalse(playlist.truncated)

        val first = playlist.tracks[0]
        assertEquals("yt:track:vid1", first.id)
        assertEquals("Hati-Hati di Jalan", first.title)
        assertEquals(listOf("Tulus"), first.artists)
        assertEquals(242_000L, first.durationMs)
        assertEquals(YOUTUBE_MUSIC_SOURCE, first.source)
        assertTrue(first.artworkUrl!!.contains("=w544-h544"))

        val second = playlist.tracks[1]
        assertEquals("yt:track:vid2", second.id)
        assertEquals(listOf("Hindia"), second.artists)
        assertEquals(195_000L, second.durationMs)

        val third = playlist.tracks[2]
        assertEquals("yt:track:vid3", third.id)
        assertEquals("Evaluasi", third.title)
        assertEquals(listOf("Hindia", "Rara Sekar"), third.artists)
        assertEquals(208_000L, third.durationMs)

        assertEquals(2, requests.size)
        assertEquals("VLPLtest123", requests[0].jsonBody().string("browseId"))
        assertEquals("CONT_TOKEN_2", requests[1].jsonBody().string("continuation"))
        assertEquals("VISITOR_PL", requests[1].headers["X-Goog-Visitor-Id"])
        assertEquals("VISITOR_PL", requests[1].jsonBody().string("context", "client", "visitorData"))
    }

    @Test
    fun supportsLegacyPlaylistContinuationAndEnforcesLimit() = runTest {
        val client = innertube { request ->
            val body = request.jsonBody()
            when (body.string("continuation")) {
                null -> json(LEGACY_PLAYLIST_PAGE_1)
                "LEGACY_TOKEN_2" -> json(LEGACY_PLAYLIST_PAGE_2)
                else -> error("Unexpected continuation")
            }
        }
        val playlists = YouTubeMusicPlaylists(client)

        val limited = playlists.fetchPlaylist("PLlegacy", limit = 2)

        assertEquals("Playlist Klasik", limited.name)
        assertEquals(2, limited.tracks.size)
        assertTrue(limited.truncated)
        assertEquals(listOf("yt:track:leg1", "yt:track:leg2"), limited.tracks.map { it.id })
    }

    @Test
    fun keepsFirstPageTracksWhenContinuationFails() = runTest {
        val client = innertube { request ->
            if (request.jsonBody().string("continuation") == null) {
                json(PLAYLIST_PAGE_1)
            } else {
                json("{}", HttpStatusCode.ServiceUnavailable)
            }
        }

        val playlist = YouTubeMusicPlaylists(client).fetchPlaylist("PLpartial")

        assertEquals(2, playlist.tracks.size)
        assertTrue(playlist.truncated)
    }

    @Test
    fun reportsClearErrorForPrivateMissingOrEmptyPlaylists() = runTest {
        val notFound = YouTubeMusicPlaylists(innertube { json("{}", HttpStatusCode.NotFound) })
        val notFoundError = assertFailsWith<PlaylistImportException> { notFound.fetchPlaylist("PLmissing") }
        assertTrue(notFoundError.userMessage.contains("publik", ignoreCase = true))

        val empty = YouTubeMusicPlaylists(innertube { json(EMPTY_PLAYLIST_RESPONSE) })
        val emptyError = assertFailsWith<PlaylistImportException> { empty.fetchPlaylist("PLempty") }
        assertTrue(emptyError.userMessage.contains("publik", ignoreCase = true))

        val rateLimited = YouTubeMusicPlaylists(innertube { json("{}", HttpStatusCode.TooManyRequests) })
        val rateError = assertFailsWith<PlaylistImportException> { rateLimited.fetchPlaylist("PLrate") }
        assertTrue(rateError.userMessage.contains("membatasi", ignoreCase = true))
    }

    @Test
    fun candidateSearcherExtractsVideoTypeAndAlbumForMatching() = runTest {
        val client = innertube { json(SEARCH_WITH_TYPES) }
        val searcher = InnertubeCandidateSearcher(client)

        val candidates = searcher.search("tulus manusia", includeVideos = false)

        assertEquals(2, candidates.size)
        assertEquals("MUSIC_VIDEO_TYPE_ATV", candidates[0].videoType)
        assertEquals("Manusia", candidates[0].album)
        assertEquals("yt:track:E7kHvjvU6JY", candidates[0].track.id)
        assertEquals("MUSIC_VIDEO_TYPE_UGC", candidates[1].videoType)
    }
}

private const val PLAYLIST_PAGE_1 = """
{
  "responseContext": { "visitorData": "VISITOR_PL" },
  "contents": {
    "twoColumnBrowseResultsRenderer": {
      "tabs": [{
        "tabRenderer": {
          "content": {
            "sectionListRenderer": {
              "contents": [{
                "musicResponsiveHeaderRenderer": {
                  "title": { "runs": [{ "text": "Lagu Santai Sore" }] }
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
                    "thumbnail": {
                      "musicThumbnailRenderer": {
                        "thumbnail": {
                          "thumbnails": [
                            { "url": "//lh3.googleusercontent.com/cover1=w60-h60-l90-rj", "width": 60 }
                          ]
                        }
                      }
                    },
                    "flexColumns": [
                      {
                        "musicResponsiveListItemFlexColumnRenderer": {
                          "text": {
                            "runs": [{
                              "text": "Hati-Hati di Jalan",
                              "navigationEndpoint": { "watchEndpoint": { "videoId": "vid1" } }
                            }]
                          }
                        }
                      },
                      {
                        "musicResponsiveListItemFlexColumnRenderer": {
                          "text": {
                            "runs": [{
                              "text": "Tulus",
                              "navigationEndpoint": {
                                "browseEndpoint": {
                                  "browseId": "UC1",
                                  "browseEndpointContextSupportedConfigs": {
                                    "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_ARTIST" }
                                  }
                                }
                              }
                            }]
                          }
                        }
                      }
                    ],
                    "fixedColumns": [
                      {
                        "musicResponsiveListItemFixedColumnRenderer": {
                          "text": { "simpleText": "4.02" }
                        }
                      }
                    ],
                    "playlistItemData": { "videoId": "vid1" }
                  }
                },
                {
                  "musicResponsiveListItemRenderer": {
                    "flexColumns": [
                      {
                        "musicResponsiveListItemFlexColumnRenderer": {
                          "text": { "runs": [{ "text": "Secukupnya" }] }
                        }
                      },
                      {
                        "musicResponsiveListItemFlexColumnRenderer": {
                          "text": { "runs": [{ "text": "Hindia" }] }
                        }
                      }
                    ],
                    "fixedColumns": [
                      {
                        "musicResponsiveListItemFixedColumnRenderer": {
                          "text": { "runs": [{ "text": "3:15" }] }
                        }
                      }
                    ],
                    "overlay": {
                      "musicItemThumbnailOverlayRenderer": {
                        "content": {
                          "musicPlayButtonRenderer": {
                            "playNavigationEndpoint": {
                              "watchEndpoint": { "videoId": "vid2" }
                            }
                          }
                        }
                      }
                    }
                  }
                },
                {
                  "musicResponsiveListItemRenderer": {
                    "flexColumns": [
                      {
                        "musicResponsiveListItemFlexColumnRenderer": {
                          "text": { "runs": [{ "text": "Lagu Dihapus" }] }
                        }
                      }
                    ]
                  }
                },
                {
                  "continuationItemRenderer": {
                    "continuationEndpoint": {
                      "continuationCommand": { "token": "CONT_TOKEN_2" }
                    }
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

private const val PLAYLIST_PAGE_2 = """
{
  "onResponseReceivedActions": [{
    "appendContinuationItemsAction": {
      "continuationItems": [
        {
          "musicResponsiveListItemRenderer": {
            "flexColumns": [
              {
                "musicResponsiveListItemFlexColumnRenderer": {
                  "text": {
                    "runs": [{
                      "text": "Evaluasi",
                      "navigationEndpoint": { "watchEndpoint": { "videoId": "vid3" } }
                    }]
                  }
                }
              },
              {
                "musicResponsiveListItemFlexColumnRenderer": {
                  "text": {
                    "runs": [
                      {
                        "text": "Hindia",
                        "navigationEndpoint": {
                          "browseEndpoint": {
                            "browseId": "UC_hindia",
                            "browseEndpointContextSupportedConfigs": {
                              "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_ARTIST" }
                            }
                          }
                        }
                      },
                      { "text": " & " },
                      {
                        "text": "Rara Sekar",
                        "navigationEndpoint": {
                          "browseEndpoint": {
                            "browseId": "UC_rara",
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
              {
                "musicResponsiveListItemFixedColumnRenderer": {
                  "text": { "runs": [{ "text": "3:28" }] }
                }
              }
            ],
            "playlistItemData": { "videoId": "vid3" }
          }
        }
      ]
    }
  }]
}
"""

private const val LEGACY_PLAYLIST_PAGE_1 = """
{
  "header": {
    "musicDetailHeaderRenderer": {
      "title": { "simpleText": "Playlist Klasik" }
    }
  },
  "contents": {
    "singleColumnBrowseResultsRenderer": {
      "tabs": [{
        "tabRenderer": {
          "content": {
            "sectionListRenderer": {
              "contents": [{
                "musicPlaylistShelfRenderer": {
                  "contents": [
                    {
                      "musicResponsiveListItemRenderer": {
                        "flexColumns": [
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Satu" }] } } },
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Artis 1" }] } } }
                        ],
                        "playlistItemData": { "videoId": "leg1" }
                      }
                    }
                  ],
                  "continuations": [{
                    "nextContinuationData": { "continuation": "LEGACY_TOKEN_2" }
                  }]
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

private const val LEGACY_PLAYLIST_PAGE_2 = """
{
  "continuationContents": {
    "musicPlaylistShelfContinuation": {
      "contents": [
        {
          "musicResponsiveListItemRenderer": {
            "flexColumns": [
              { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Dua" }] } } },
              { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Artis 2" }] } } }
            ],
            "playlistItemData": { "videoId": "leg2" }
          }
        },
        {
          "musicResponsiveListItemRenderer": {
            "flexColumns": [
              { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Tiga" }] } } },
              { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Artis 3" }] } } }
            ],
            "playlistItemData": { "videoId": "leg3" }
          }
        }
      ]
    }
  }
}
"""

private const val EMPTY_PLAYLIST_RESPONSE = """
{
  "contents": {
    "twoColumnBrowseResultsRenderer": {
      "tabs": [{
        "tabRenderer": {
          "content": {
            "sectionListRenderer": {
              "contents": [{
                "musicResponsiveHeaderRenderer": {
                  "title": { "runs": [{ "text": "Kosong" }] }
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

private const val SEARCH_WITH_TYPES = """
{
  "contents": {
    "tabbedSearchResultsRenderer": {
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
                          {
                            "musicResponsiveListItemFlexColumnRenderer": {
                              "text": {
                                "runs": [{
                                  "text": "Hati-Hati di Jalan",
                                  "navigationEndpoint": {
                                    "watchEndpoint": {
                                      "videoId": "E7kHvjvU6JY",
                                      "watchEndpointMusicSupportedConfigs": {
                                        "watchEndpointMusicConfig": { "musicVideoType": "MUSIC_VIDEO_TYPE_ATV" }
                                      }
                                    }
                                  }
                                }]
                              }
                            }
                          },
                          {
                            "musicResponsiveListItemFlexColumnRenderer": {
                              "text": {
                                "runs": [
                                  {
                                    "text": "Tulus",
                                    "navigationEndpoint": {
                                      "browseEndpoint": {
                                        "browseId": "UC_tulus",
                                        "browseEndpointContextSupportedConfigs": {
                                          "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_ARTIST" }
                                        }
                                      }
                                    }
                                  },
                                  { "text": " • " },
                                  {
                                    "text": "Manusia",
                                    "navigationEndpoint": {
                                      "browseEndpoint": {
                                        "browseId": "MPREb_manusia",
                                        "browseEndpointContextSupportedConfigs": {
                                          "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_ALBUM" }
                                        }
                                      }
                                    }
                                  },
                                  { "text": " • " },
                                  { "text": "4.02" }
                                ]
                              }
                            }
                          }
                        ],
                        "playlistItemData": { "videoId": "E7kHvjvU6JY" }
                      }
                    },
                    {
                      "musicResponsiveListItemRenderer": {
                        "flexColumns": [
                          {
                            "musicResponsiveListItemFlexColumnRenderer": {
                              "text": {
                                "runs": [{
                                  "text": "Hati-Hati di Jalan (Lyrics)",
                                  "navigationEndpoint": {
                                    "watchEndpoint": {
                                      "videoId": "ugc123",
                                      "watchEndpointMusicSupportedConfigs": {
                                        "watchEndpointMusicConfig": { "musicVideoType": "MUSIC_VIDEO_TYPE_UGC" }
                                      }
                                    }
                                  }
                                }]
                              }
                            }
                          },
                          {
                            "musicResponsiveListItemFlexColumnRenderer": {
                              "text": { "runs": [{ "text": "Lirik Channel" }] }
                            }
                          }
                        ],
                        "playlistItemData": { "videoId": "ugc123" }
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
