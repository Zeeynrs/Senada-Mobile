package senada.lyrics.sources

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import senada.lyrics.LyricWord
import senada.lyrics.karaokeTiming
import senada.model.Track

class NetEaseSourceTest {
    private val track = Track(
        id = "yt:track:demo1",
        title = "Morning Light",
        artists = listOf("Demo Artist"),
        durationMs = 200_000L,
        source = "ytmusic",
    )
    private val requests = mutableListOf<HttpRequestData>()

    private fun source(reply: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): NetEaseSource =
        NetEaseSource(
            HttpClient(
                MockEngine { request ->
                    requests += request
                    reply(request)
                },
            ),
        )

    @Test
    fun parsesWordTimedYrcStripsCreditsAndPreservesMetadataDuetAndBackingVocals() = runTest {
        val yrc = """
            {"t":0,"c":[{"tx":"作词: Author"}]}
            [0,1000](0,1000,0)作词 : Author
            [2000,2500](2000,600,0)Hello (2600,700,0)world (3300,500,0)(ooh (3800,700,0)yeah)
            [5000,2000](5000,200,0)男：(5200,800,0)夜(6000,1000,0)空
            [7500,1800](7500,200,0)女：(7700,700,0)星(8400,900,0)光
        """.trimIndent()
        val lyricJson = buildJsonObject {
            put("code", 200)
            put("yrc", buildJsonObject { put("lyric", JsonPrimitive(yrc)) })
            put("lrc", buildJsonObject { put("lyric", JsonPrimitive("[00:02.00]Hello world")) })
        }.toString()

        val src = source { request ->
            when (request.url.encodedPath) {
                "/api/search/get" -> respond(
                    """
                    {
                      "code": 200,
                      "result": {
                        "songs": [
                          {
                            "id": 1001,
                            "name": "Morning Light (Live)",
                            "duration": 265000,
                            "artists": [{"name": "Demo Artist"}],
                            "alias": []
                          },
                          {
                            "id": 1002,
                            "name": "Morning Light",
                            "duration": 200400,
                            "artists": [{"name": "Demo Artist"}],
                            "alias": []
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                )
                "/api/song/lyric" -> {
                    assertEquals("1002", request.url.parameters["id"])
                    assertEquals("1", request.url.parameters["lv"])
                    assertEquals("1", request.url.parameters["yv"])
                    respond(lyricJson)
                }
                else -> error("Unexpected path: ${request.url}")
            }
        }

        val candidates = src.search(track)
        assertEquals(1, candidates.size)
        val cand = candidates.single()
        assertEquals("netease", cand.sourceId)
        assertTrue(cand.wordSynced)
        assertEquals("Morning Light", cand.title)
        assertEquals("Demo Artist", cand.artist)
        assertEquals(200_400L, cand.durationMs)
        assertEquals(3, cand.lines.size)

        val line0 = cand.lines[0]
        assertEquals(2_000L, line0.startMs)
        assertEquals("Hello world", line0.text)
        assertEquals(
            listOf(
                LyricWord(2_000L, 600L, "Hello "),
                LyricWord(2_600L, 700L, "world"),
            ),
            line0.words,
        )
        assertWordsFoundInOrder(line0.text, line0.words)
        assertFalse(karaokeTiming(line0, cand.lines[1].startMs).isEstimated)

        val bg = assertNotNull(line0.background)
        assertEquals("ooh yeah", bg.text)
        assertEquals(
            listOf(
                LyricWord(3_300L, 500L, "ooh "),
                LyricWord(3_800L, 700L, "yeah"),
            ),
            bg.words,
        )
        assertWordsFoundInOrder(bg.text, bg.words)

        assertEquals("v1", cand.lines[1].agent)
        assertEquals("夜空", cand.lines[1].text)
        assertEquals(listOf(LyricWord(5_200L, 800L, "夜"), LyricWord(6_000L, 1_000L, "空")), cand.lines[1].words)
        assertWordsFoundInOrder(cand.lines[1].text, cand.lines[1].words)

        assertEquals("v2", cand.lines[2].agent)
        assertEquals("星光", cand.lines[2].text)
        assertEquals(listOf(LyricWord(7_700L, 700L, "星"), LyricWord(8_400L, 900L, "光")), cand.lines[2].words)

        assertEquals(2, requests.size)
        for (req in requests) {
            assertEquals(CN_LYRICS_USER_AGENT, req.headers[HttpHeaders.UserAgent])
        }
    }

    @Test
    fun fallsBackToLineOnlyLrcWithWordSyncedFalse() = runTest {
        val src = source { request ->
            when (request.url.encodedPath) {
                "/api/search/get" -> respond(
                    """
                    {
                      "code": 200,
                      "result": {
                        "songs": [
                          {
                            "id": 2001,
                            "name": "Morning Light",
                            "duration": 199000,
                            "artists": [{"name": "Demo Artist"}]
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                )
                "/api/song/lyric" -> respond(
                    """
                    {
                      "code": 200,
                      "lrc": {"lyric": "[00:00.00]作词 : Author\n[00:02.50]Hello world\n[00:05.00]Second line"}
                    }
                    """.trimIndent(),
                )
                else -> error("Unexpected path: ${request.url}")
            }
        }

        val cand = src.search(track).single()
        assertFalse(cand.wordSynced)
        assertEquals("Morning Light", cand.title)
        assertEquals("Demo Artist", cand.artist)
        assertEquals(199_000L, cand.durationMs)
        assertEquals(listOf("Hello world", "Second line"), cand.lines.map { it.text })
        assertTrue(cand.lines.all { it.words.isEmpty() })
    }

    @Test
    fun notFoundAndInstrumentalReturnEmptyList() = runTest {
        val emptySearch = source { respond("""{"code":200,"result":{"songCount":0}}""") }
        assertEquals(emptyList(), emptySearch.search(track))

        val http404 = source { respond("", HttpStatusCode.NotFound) }
        assertEquals(emptyList(), http404.search(track))

        val instrumental = source { request ->
            if (request.url.encodedPath == "/api/search/get") {
                respond("""{"code":200,"result":{"songs":[{"id":3001,"name":"Morning Light","duration":200000,"artists":[{"name":"Demo Artist"}]}]}}""")
            } else {
                respond("""{"code":200,"lrc":{"lyric":"[00:00.00]纯音乐，请欣赏"}}""")
            }
        }
        assertEquals(emptyList(), instrumental.search(track))
    }

    @Test
    fun httpErrorThrowsLyricsSourceException() = runTest {
        val searchErr = source { respond("oops", HttpStatusCode.ServiceUnavailable) }
        val ex1 = assertFailsWith<LyricsSourceException> { searchErr.search(track) }
        assertEquals("netease", ex1.sourceId)

        val lyricErr = source { request ->
            if (request.url.encodedPath == "/api/search/get") {
                respond("""{"code":200,"result":{"songs":[{"id":4001,"name":"Morning Light","duration":200000,"artists":[{"name":"Demo Artist"}]}]}}""")
            } else {
                respond("boom", HttpStatusCode.InternalServerError)
            }
        }
        val ex2 = assertFailsWith<LyricsSourceException> { lyricErr.search(track) }
        assertEquals("netease", ex2.sourceId)
    }

    @Test
    fun worksWhenHttpTimeoutPluginIsInstalled() = runTest {
        val client = HttpClient(
            MockEngine { request ->
                requests += request
                if (request.url.encodedPath == "/api/search/get") {
                    respond("""{"code":200,"result":{"songs":[{"id":5001,"name":"Morning Light","duration":200000,"artists":[{"name":"Demo Artist"}]}]}}""")
                } else {
                    respond("""{"code":200,"yrc":{"lyric":"[1000,1000](1000,500,0)Hello (1500,500,0)world"}}""")
                }
            },
        ) {
            install(HttpTimeout)
        }
        val cand = NetEaseSource(client).search(track).single()
        assertTrue(cand.wordSynced)
        assertEquals("Hello world", cand.lines.single().text)
    }

    private fun assertWordsFoundInOrder(lineText: String, words: List<LyricWord>) {
        var cursor = 0
        for (word in words) {
            val token = word.text.trim()
            assertTrue(token.isNotEmpty())
            val found = lineText.indexOf(token, startIndex = cursor)
            assertTrue(found >= 0, "Expected '$token' in '$lineText' from index $cursor")
            cursor = found + token.length
        }
    }
}
