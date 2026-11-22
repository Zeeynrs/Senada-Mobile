package senada.catalog.spotify

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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import senada.catalog.PlaylistImportException

class SpotifyPlaylistReaderTest {
    private fun reader(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        SpotifyPlaylistReader(HttpClient(MockEngine(handler)))

    private fun MockRequestHandleScope.html(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "text/html"))

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun readsSmallPlaylistFromEmbedNextDataAndSkipsPodcastEpisodes() = runTest {
        val embedHtml = wrapNextData(
            """
            {
              "props": {
                "pageProps": {
                  "state": {
                    "data": {
                      "entity": {
                        "name": "Pagi Cerah",
                        "trackList": [
                          {
                            "uri": "spotify:track:1111111111111111111111",
                            "title": "Hati-Hati di Jalan",
                            "subtitle": "Tulus",
                            "duration": 242000
                          },
                          {
                            "uri": "spotify:episode:2222222222222222222222",
                            "title": "Podcast Episode",
                            "subtitle": "Host",
                            "duration": 600000
                          },
                          {
                            "uri": "spotify:track:3333333333333333333333",
                            "title": "lovely (with Khalid)",
                            "subtitle": "Billie Eilish,\u00A0Khalid",
                            "duration": 200000
                          }
                        ]
                      }
                    },
                    "settings": { "session": { "accessToken": "anon-token" } }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val playlist = reader { html(embedHtml) }.fetchPlaylist("37i9dQZF1DXcBWIGoYBM5M")

        assertEquals("Pagi Cerah", playlist.name)
        assertTrue(playlist.complete)
        assertEquals(2, playlist.tracks.size)
        assertEquals(
            SpotifyTrack("1111111111111111111111", "Hati-Hati di Jalan", listOf("Tulus"), durationMs = 242_000L),
            playlist.tracks[0],
        )
        assertEquals(
            SpotifyTrack("3333333333333333333333", "lovely (with Khalid)", listOf("Billie Eilish", "Khalid"), durationMs = 200_000L),
            playlist.tracks[1],
        )
    }

    @Test
    fun pagesBeyondHundredTracksViaPartnerQueryAndRefreshesRotatedHash() = runTest {
        val embedTracks = buildEmbedTrackList(100)
        val embedHtml = wrapNextData(
            """
            {
              "props": {
                "pageProps": {
                  "state": {
                    "data": { "entity": { "name": "Besar", "trackList": $embedTracks } },
                    "settings": { "session": { "accessToken": "anon-token" } }
                  }
                }
              }
            }
            """.trimIndent(),
        )
        val freshHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

        val reader = reader { request ->
            val url = request.url.toString()
            when {
                url.contains("/embed/playlist/") -> html(embedHtml)
                url == "https://open.spotify.com/" -> html(
                    """<script src="https://open.spotifycdn.com/cdn/build/web-player/web-player.123.js"></script>""",
                )
                url.contains("web-player.123.js") -> respond(
                    """something("fetchPlaylist","query","$freshHash")""",
                    HttpStatusCode.OK,
                )
                url.contains("api-partner.spotify.com") -> {
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    val hash = body["extensions"]!!.jsonObject["persistedQuery"]!!.jsonObject["sha256Hash"]!!.jsonPrimitive.content
                    if (hash != freshHash) {
                        json("{}", HttpStatusCode.BadRequest)
                    } else {
                        val offset = body["variables"]!!.jsonObject["offset"]!!.jsonPrimitive.int
                        val count = if (offset == 0) 100 else 25
                        json(partnerResponse(offset = offset, count = count, total = 125))
                    }
                }
                else -> error("Unexpected URL: $url")
            }
        }

        val playlist = reader.fetchPlaylist("37i9dQZF1DXcBWIGoYBM5M")

        assertEquals("Besar", playlist.name)
        assertTrue(playlist.complete)
        assertEquals(125, playlist.tracks.size)
        assertEquals("Album Partner", playlist.tracks.first().album)
        assertEquals("Lagu 124", playlist.tracks.last().title)
    }

    @Test
    fun fallsBackToWebApiAndThenEmbedTracksIfPagingApisFail() = runTest {
        val embedTracks = buildEmbedTrackList(100)
        val embedHtml = wrapNextData(
            """
            {
              "props": {
                "pageProps": {
                  "state": {
                    "data": { "entity": { "name": "Fallback", "trackList": $embedTracks } },
                    "settings": { "session": { "accessToken": "anon-token" } }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val webApiFallback = reader { request ->
            val url = request.url.toString()
            when {
                url.contains("/embed/playlist/") -> html(embedHtml)
                url.contains("api-partner.spotify.com") -> json("{}", HttpStatusCode.InternalServerError)
                url.contains("api.spotify.com/v1/playlists/") -> json(webApiResponse(startIndex = 100, count = 15))
                else -> error("Unexpected URL: $url")
            }
        }.fetchPlaylist("37i9dQZF1DXcBWIGoYBM5M")

        assertTrue(webApiFallback.complete)
        assertEquals(115, webApiFallback.tracks.size)

        val embedOnly = reader { request ->
            val url = request.url.toString()
            when {
                url.contains("/embed/playlist/") -> html(embedHtml)
                else -> json("{}", HttpStatusCode.Forbidden)
            }
        }.fetchPlaylist("37i9dQZF1DXcBWIGoYBM5M")

        assertFalse(embedOnly.complete)
        assertEquals(100, embedOnly.tracks.size)
    }

    @Test
    fun reportsIndonesianErrorsForNotFoundChangedFormatOrEmptyPlaylist() = runTest {
        val notFound = assertFailsWith<PlaylistImportException> {
            reader { html("Not found", HttpStatusCode.NotFound) }.fetchPlaylist("37i9dQZF1DXcBWIGoYBM5M")
        }
        assertTrue(notFound.userMessage.contains("publik", ignoreCase = true))

        val brokenHtml = assertFailsWith<PlaylistImportException> {
            reader { html("<html><body>No next data</body></html>") }.fetchPlaylist("37i9dQZF1DXcBWIGoYBM5M")
        }
        assertTrue(brokenHtml.userMessage.contains("Format", ignoreCase = true))

        val emptyHtml = wrapNextData(
            """{"props":{"pageProps":{"state":{"data":{"entity":{"name":"Kosong","trackList":[]}}}}}}""",
        )
        val empty = assertFailsWith<PlaylistImportException> {
            reader { html(emptyHtml) }.fetchPlaylist("37i9dQZF1DXcBWIGoYBM5M")
        }
        assertTrue(empty.userMessage.contains("kosong", ignoreCase = true))
    }

    private fun wrapNextData(json: String): String =
        """<html><head><script id="__NEXT_DATA__" type="application/json">$json</script></head><body></body></html>"""

    private fun buildEmbedTrackList(count: Int): String = buildJsonArray {
        repeat(count) { i ->
            add(
                buildJsonObject {
                    put("uri", "spotify:track:trackid${i.toString().padStart(15, '0')}")
                    put("title", "Lagu $i")
                    put("subtitle", "Artis $i")
                    put("duration", 180_000 + i * 1_000)
                },
            )
        }
    }.toString()

    private fun partnerResponse(offset: Int, count: Int, total: Int): String = buildJsonObject {
        putJsonObject("data") {
            putJsonObject("playlistV2") {
                putJsonObject("content") {
                    put("totalCount", total)
                    putJsonArray("items") {
                        repeat(count) { i ->
                            val idx = offset + i
                            add(
                                buildJsonObject {
                                    putJsonObject("itemV2") {
                                        putJsonObject("data") {
                                            put("__typename", "Track")
                                            put("uri", "spotify:track:trackid${idx.toString().padStart(15, '0')}")
                                            put("name", "Lagu $idx")
                                            putJsonObject("artists") {
                                                putJsonArray("items") {
                                                    add(
                                                        buildJsonObject {
                                                            putJsonObject("profile") { put("name", "Artis $idx") }
                                                        },
                                                    )
                                                }
                                            }
                                            putJsonObject("albumOfTrack") { put("name", "Album Partner") }
                                            putJsonObject("trackDuration") { put("totalMilliseconds", 180_000L + idx * 1_000) }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }.toString()

    private fun webApiResponse(startIndex: Int, count: Int): String = buildJsonObject {
        put("next", JsonNull)
        putJsonArray("items") {
            repeat(count) { i ->
                val idx = startIndex + i
                add(
                    buildJsonObject {
                        putJsonObject("track") {
                            put("type", "track")
                            put("uri", "spotify:track:trackid${idx.toString().padStart(15, '0')}")
                            put("name", "Lagu $idx")
                            put("duration_ms", 200_000L)
                            putJsonArray("artists") {
                                add(buildJsonObject { put("name", "Artis $idx") })
                            }
                            putJsonObject("album") { put("name", "Album Web") }
                        }
                    },
                )
            }
        }
    }.toString()
}
