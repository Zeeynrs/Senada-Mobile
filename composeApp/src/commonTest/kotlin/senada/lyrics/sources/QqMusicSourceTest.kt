package senada.lyrics.sources

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
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

class QqMusicSourceTest {
    private val track = Track(
        id = "yt:track:demo2",
        title = "Morning Light",
        artists = listOf("Demo Artist"),
        durationMs = 200_000L,
        source = "ytmusic",
    )
    private val requests = mutableListOf<HttpRequestData>()

    private fun source(reply: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): QqMusicSource =
        QqMusicSource(
            HttpClient(
                MockEngine { request ->
                    requests += request
                    reply(request)
                },
            ),
        )

    private fun requestBody(request: HttpRequestData): String =
        (request.body as TextContent).text

    @Test
    fun decryptsAndParsesWordTimedQrcWithMetadataDuetAndBackingVocals() = runTest {
        val src = source { request ->
            val body = requestBody(request)
            when {
                body.contains("DoSearchForQQMusicDesktop") -> respond(SEARCH_RESPONSE)
                body.contains("GetPlayLyricInfo") && body.contains("\"qrc\":1") -> respond(
                    """{"code":0,"req":{"code":0,"data":{"lyric":"${ZlibAndQrcCryptoTest.QRC_ENCRYPTED_HEX}"}}}""",
                )
                else -> error("Unexpected QQ request: $body")
            }
        }

        val candidates = src.search(track)
        assertEquals(1, candidates.size)
        val cand = candidates.single()
        assertEquals("qqmusic", cand.sourceId)
        assertTrue(cand.wordSynced)
        assertEquals("Morning Light", cand.title)
        assertEquals("Demo Artist", cand.artist)
        assertEquals(200_000L, cand.durationMs)
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
        assertEquals("v2", cand.lines[2].agent)
        assertEquals("星光", cand.lines[2].text)

        assertEquals(2, requests.size)
        for (req in requests) {
            assertEquals("u.y.qq.com", req.url.host)
            assertEquals(CN_LYRICS_USER_AGENT, req.headers[HttpHeaders.UserAgent])
        }
    }

    @Test
    fun fallsBackToLineOnlyLrcWhenEncryptedLrcOrBase64LrcReturned() = runTest {
        // Case 1: QRC endpoint returns encrypted line-only LRC.
        val encLrcSource = source { request ->
            val body = requestBody(request)
            if (body.contains("DoSearchForQQMusicDesktop")) {
                respond(SEARCH_RESPONSE)
            } else {
                respond("""{"code":0,"req":{"code":0,"data":{"lyric":"${ZlibAndQrcCryptoTest.QQ_ENCRYPTED_LRC_HEX}"}}}""")
            }
        }
        val cand1 = encLrcSource.search(track).single()
        assertFalse(cand1.wordSynced)
        assertEquals(listOf("Hello world", "Second line"), cand1.lines.map { it.text })
        assertEquals("Morning Light", cand1.title)
        assertEquals("Demo Artist", cand1.artist)
        assertEquals(200_000L, cand1.durationMs)

        // Case 2: QRC endpoint returns empty lyric -> falls back to plain base64 LRC request.
        requests.clear()
        val lrcB64 = Base64.Default.encode("[00:01.50]Line one\n[00:04.00]Line two".encodeToByteArray())
        val fallbackSource = source { request ->
            val body = requestBody(request)
            when {
                body.contains("DoSearchForQQMusicDesktop") -> respond(SEARCH_RESPONSE)
                body.contains("\"qrc\":1") -> respond("""{"code":0,"req":{"code":0,"data":{"lyric":""}}}""")
                else -> respond("""{"code":0,"req":{"code":0,"data":{"lyric":"$lrcB64"}}}""")
            }
        }
        val cand2 = fallbackSource.search(track).single()
        assertFalse(cand2.wordSynced)
        assertEquals(listOf("Line one", "Line two"), cand2.lines.map { it.text })
        assertEquals(3, requests.size)
    }

    @Test
    fun notFoundAndInstrumentalReturnEmptyList() = runTest {
        val emptySearch = source {
            respond("""{"code":0,"req":{"code":0,"data":{"body":{"song":{"list":[]}}}}}""")
        }
        assertEquals(emptyList(), emptySearch.search(track))

        val http404 = source { respond("", HttpStatusCode.NotFound) }
        assertEquals(emptyList(), http404.search(track))

        val instrumentalB64 = Base64.Default.encode("[00:00.00]此歌曲为没有填词的纯音乐，请您欣赏".encodeToByteArray())
        val instrumental = source { request ->
            val body = requestBody(request)
            when {
                body.contains("DoSearchForQQMusicDesktop") -> respond(SEARCH_RESPONSE)
                body.contains("\"qrc\":1") -> respond("""{"code":0,"req":{"code":0,"data":{"lyric":""}}}""")
                else -> respond("""{"code":0,"req":{"code":0,"data":{"lyric":"$instrumentalB64"}}}""")
            }
        }
        assertEquals(emptyList(), instrumental.search(track))
    }

    @Test
    fun httpErrorThrowsLyricsSourceException() = runTest {
        val searchErr = source { respond("err", HttpStatusCode.BadGateway) }
        val ex1 = assertFailsWith<LyricsSourceException> { searchErr.search(track) }
        assertEquals("qqmusic", ex1.sourceId)

        val lyricErr = source { request ->
            if (requestBody(request).contains("DoSearchForQQMusicDesktop")) {
                respond(SEARCH_RESPONSE)
            } else {
                respond("err", HttpStatusCode.ServiceUnavailable)
            }
        }
        val ex2 = assertFailsWith<LyricsSourceException> { lyricErr.search(track) }
        assertEquals("qqmusic", ex2.sourceId)
    }

    @Test
    fun parseQrcPreservesTrailingTextAndAppliesOffset() {
        val qrc = """
            [offset:+100]
            [1100,2000]Hello (1100,500)world(1600,600)!
        """.trimIndent()
        val line = QqMusicSource.parseQrc(qrc).single()
        assertEquals(1_000L, line.startMs)
        assertEquals("Hello world!", line.text)
        assertEquals(
            listOf(
                LyricWord(1_000L, 500L, "Hello "),
                LyricWord(1_500L, 600L, "world!"),
            ),
            line.words,
        )
        assertWordsFoundInOrder(line.text, line.words)
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
        val SEARCH_RESPONSE = """
            {
              "code": 0,
              "req": {
                "code": 0,
                "data": {
                  "body": {
                    "song": {
                      "list": [
                        {
                          "id": 9991,
                          "mid": "000wrong",
                          "name": "Morning Light (Live)",
                          "interval": 270,
                          "singer": [{"name": "Demo Artist"}]
                        },
                        {
                          "id": 9992,
                          "mid": "000match",
                          "name": "Morning Light",
                          "interval": 200,
                          "singer": [{"name": "Demo Artist"}]
                        }
                      ]
                    }
                  }
                }
              }
            }
        """.trimIndent()
    }
}
