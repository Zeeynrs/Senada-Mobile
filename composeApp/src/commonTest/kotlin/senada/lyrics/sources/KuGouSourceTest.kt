package senada.lyrics.sources

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import senada.lyrics.LyricWord
import senada.lyrics.karaokeTiming
import senada.model.Track

class KuGouSourceTest {
    private val track = Track(
        id = "yt:track:demo3",
        title = "Morning Light",
        artists = listOf("Demo Artist"),
        durationMs = 200_000L,
        source = "ytmusic",
    )
    private val requests = mutableListOf<HttpRequestData>()

    private fun source(reply: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): KuGouSource =
        KuGouSource(
            HttpClient(
                MockEngine { request ->
                    requests += request
                    reply(request)
                },
            ),
        )

    @Test
    fun downloadsAndDecryptsWordTimedKrcWithMetadataDuetAndBackingVocals() = runTest {
        val src = source { request ->
            when (request.url.encodedPath) {
                "/api/v3/search/song" -> respond(SONG_SEARCH_RESPONSE)
                "/search" -> {
                    assertEquals("hash_match", request.url.parameters["hash"])
                    respond(CANDIDATES_RESPONSE)
                }
                "/download" -> {
                    assertEquals("krc", request.url.parameters["fmt"])
                    assertEquals("5501", request.url.parameters["id"])
                    assertEquals("KEY_MATCH", request.url.parameters["accesskey"])
                    respond("""{"status":200,"fmt":"krc","content":"${ZlibAndQrcCryptoTest.KRC_BASE64}"}""")
                }
                else -> error("Unexpected path: ${request.url}")
            }
        }

        val candidates = src.search(track)
        assertEquals(1, candidates.size)
        val cand = candidates.single()
        assertEquals("kugou", cand.sourceId)
        assertTrue(cand.wordSynced)
        assertEquals("Morning Light", cand.title)
        assertEquals("Demo Artist", cand.artist)
        assertEquals(200_100L, cand.durationMs)
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

        assertEquals("v1", cand.lines[1].agent)
        assertEquals("夜空", cand.lines[1].text)
        assertEquals(listOf(LyricWord(5_200L, 800L, "夜"), LyricWord(6_000L, 1_000L, "空")), cand.lines[1].words)

        assertEquals("v2", cand.lines[2].agent)
        assertEquals("星光", cand.lines[2].text)
        assertEquals(listOf(LyricWord(7_700L, 700L, "星"), LyricWord(8_400L, 900L, "光")), cand.lines[2].words)

        assertEquals(3, requests.size)
        for (req in requests) {
            assertEquals(CN_LYRICS_USER_AGENT, req.headers[HttpHeaders.UserAgent])
        }
    }

    @Test
    fun fallsBackToLineOnlyLrcWhenKrcUnavailableAndSupportsKeywordSearchFallback() = runTest {
        val lrcB64 = Base64.Default.encode("[00:00.00]作词：Author\n[00:02.00]Hello world\n[00:05.00]Line two".encodeToByteArray())
        val src = source { request ->
            when (request.url.encodedPath) {
                "/api/v3/search/song" -> respond("""{"status":1,"errcode":0,"data":{"info":[]}}""")
                "/search" -> {
                    assertNotNull(request.url.parameters["keyword"])
                    assertEquals("200000", request.url.parameters["duration"])
                    respond(CANDIDATES_RESPONSE)
                }
                "/download" -> when (request.url.parameters["fmt"]) {
                    "krc" -> respond("""{"status":200,"fmt":"krc","content":""}""")
                    "lrc" -> respond("""{"status":200,"fmt":"lrc","content":"$lrcB64"}""")
                    else -> error("Unexpected fmt")
                }
                else -> error("Unexpected path: ${request.url}")
            }
        }

        val cand = src.search(track).single()
        assertFalse(cand.wordSynced)
        assertEquals("Morning Light", cand.title)
        assertEquals("Demo Artist", cand.artist)
        assertEquals(200_100L, cand.durationMs)
        assertEquals(listOf("Hello world", "Line two"), cand.lines.map { it.text })
        assertTrue(cand.lines.all { it.words.isEmpty() })
    }

    @Test
    fun notFoundReturnsEmptyList() = runTest {
        val empty = source { request ->
            when (request.url.encodedPath) {
                "/api/v3/search/song" -> respond("""{"status":1,"errcode":0,"data":{"info":[]}}""")
                "/search" -> respond("""{"status":200,"errcode":200,"candidates":[]}""")
                else -> error("Unexpected path: ${request.url}")
            }
        }
        assertEquals(emptyList(), empty.search(track))

        val http404 = source { respond("", HttpStatusCode.NotFound) }
        assertEquals(emptyList(), http404.search(track))
    }

    @Test
    fun httpErrorThrowsLyricsSourceException() = runTest {
        val searchErr = source { respond("err", HttpStatusCode.InternalServerError) }
        val ex1 = assertFailsWith<LyricsSourceException> { searchErr.search(track) }
        assertEquals("kugou", ex1.sourceId)

        val downloadErr = source { request ->
            when (request.url.encodedPath) {
                "/api/v3/search/song" -> respond(SONG_SEARCH_RESPONSE)
                "/search" -> respond(CANDIDATES_RESPONSE)
                "/download" -> respond("err", HttpStatusCode.BadGateway)
                else -> error("Unexpected path: ${request.url}")
            }
        }
        val ex2 = assertFailsWith<LyricsSourceException> { downloadErr.search(track) }
        assertEquals("kugou", ex2.sourceId)
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

    private companion object {
        val SONG_SEARCH_RESPONSE = """
            {
              "status": 1,
              "errcode": 0,
              "data": {
                "info": [
                  {
                    "songname": "Morning Light (Live)",
                    "singername": "Demo Artist",
                    "duration": 265,
                    "hash": "hash_live"
                  },
                  {
                    "songname": "Morning Light",
                    "singername": "Demo Artist",
                    "duration": 200,
                    "hash": "hash_match"
                  }
                ]
              }
            }
        """.trimIndent()

        val CANDIDATES_RESPONSE = """
            {
              "status": 200,
              "errcode": 200,
              "candidates": [
                {
                  "id": "5501",
                  "accesskey": "KEY_MATCH",
                  "song": "Morning Light",
                  "singer": "Demo Artist",
                  "duration": 200100,
                  "score": 60,
                  "krctype": 1
                }
              ]
            }
        """.trimIndent()
    }
}
