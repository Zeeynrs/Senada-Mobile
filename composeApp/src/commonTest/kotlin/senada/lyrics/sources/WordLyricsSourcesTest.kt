package senada.lyrics.sources

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import senada.lyrics.LyricWord
import senada.model.Track

class WordLyricsSourcesTest {
    private val track = Track(
        "yt:track:abcdefghijk",
        "  Window (Official Music Video) ",
        listOf(" Blue  Sky - Topic ", "", "Cloud"),
        91_700,
        source = "ytmusic",
    )

    @Test
    fun betterLyricsPreservesTtmlWordTimingAndVoices() = runTest {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div><p begin="1.200" end="3.000" ttm:agent="v1"><span begin="1.200" end="1.520">Blue</span> <span begin="1.700" end="2.300">sky</span><span ttm:role="x-bg" ttm:agent="v2" begin="1.800" end="2.400">(<span begin="1.800" end="2.200">Oh</span>)</span></p></div></body>
            </tt>
        """.trimIndent()
        client { request ->
            assertEquals("api.betterlyrics.org", request.url.host)
            assertEquals("/getLyrics", request.url.encodedPath)
            assertEquals("Window", request.url.parameters["s"])
            assertEquals("Blue Sky, Cloud", request.url.parameters["a"])
            assertEquals("92", request.url.parameters["d"])
            respond(buildJsonObject { put("ttml", ttml) }.toString())
        }.use { http ->
            val candidate = BetterLyricsSource(http).search(track).single()
            val line = candidate.lines.single()
            assertTrue(candidate.wordSynced)
            assertEquals("Blue sky", line.text)
            assertEquals(listOf(LyricWord(1200, 320, "Blue "), LyricWord(1700, 600, "sky")), line.words)
            assertEquals("v1", line.agent)
            assertEquals("v2", line.background?.agent)
            assertEquals(listOf(LyricWord(1800, 400, "Oh")), line.background?.words)
        }
    }

    @Test
    fun betterLyricsMarksLineOnlyTtml() = runTest {
        client {
            respond("""{"ttml":"<tt><body><div><p begin='4.000' end='5.000'>Sky</p></div></body></tt>"}""")
        }.use { http ->
            val candidate = BetterLyricsSource(http).search(track).single()
            assertFalse(candidate.wordSynced)
            assertEquals("Sky", candidate.lines.single().text)
            assertEquals(4000L, candidate.lines.single().startMs)
        }
    }

    @Test
    fun betterLyricsUsesBoiduHostFallbackWhenPrimaryRequiresAKey() = runTest {
        val requestedHosts = mutableListOf<String>()
        client { request ->
            requestedHosts += request.url.host
            when (request.url.host) {
                "api.betterlyrics.org" -> respond("", HttpStatusCode.Unauthorized)
                "lyrics-api.boidu.dev" ->
                    respond("""{"ttml":"<tt><body><p begin='4.000'>Sky</p></body></tt>"}""")
                else -> error("Unexpected lyrics host")
            }
        }.use { http ->
            val lyrics = BetterLyricsSource(http).search(track).single()
            assertEquals(listOf("api.betterlyrics.org", "lyrics-api.boidu.dev"), requestedHosts)
            assertEquals("Sky", lyrics.lines.single().text)
        }
    }

    @Test
    fun betterLyricsReturnsNoCandidatesOnNotFound() = runTest {
        client { respond("", HttpStatusCode.NotFound) }.use { http ->
            assertTrue(BetterLyricsSource(http).search(track).isEmpty())
        }
    }

    @Test
    fun betterLyricsReportsHttpErrorsEvenWithClientValidationEnabled() = runTest {
        client(expectSuccess = true) { respond("", HttpStatusCode.Unauthorized) }.use { http ->
            assertEquals("betterlyrics", assertFailsWith<LyricsSourceException> {
                BetterLyricsSource(http).search(track)
            }.sourceId)
        }
    }

    @Test
    fun lyricsPlusMergesSyllablesIntoWordsAndKeepsBackingVocals() = runTest {
        client { request ->
            assertEquals("lyricsplus.binimum.org", request.url.host)
            assertEquals("/v2/lyrics/get", request.url.encodedPath)
            assertEquals("Window", request.url.parameters["title"])
            assertEquals("Blue Sky, Cloud", request.url.parameters["artist"])
            assertEquals("91.7", request.url.parameters["duration"])
            assertEquals("abcdefghijk", request.url.parameters["id"])
            respond("""
                {"type":"Word","metadata":{"title":"Window","artist":"Blue Sky"},"duration":91.7,
                 "syncedLyrics":"[00:01.00]Fallback",
                 "lyrics":[{"time":1000,"duration":1400,"text":"Hello sky","element":{"singer":"v1"},
                 "syllabus":[{"time":1000,"duration":120,"text":"Hel"},
                             {"time":1140,"duration":220,"text":"lo"},
                             {"time":1460,"duration":300,"text":"sky"},
                             {"time":1500,"duration":300,"text":"(Oh)","isBackground":true}]}]}
            """.trimIndent())
        }.use { http ->
            val candidate = LyricsPlusSource(http).search(track).single()
            val line = candidate.lines.single()
            assertTrue(candidate.wordSynced)
            assertEquals("Window", candidate.title)
            assertEquals("Blue Sky", candidate.artist)
            assertEquals(91_700L, candidate.durationMs)
            assertEquals("Hello sky", line.text)
            assertEquals("v1", line.agent)
            assertEquals(listOf(LyricWord(1000, 360, "Hello "), LyricWord(1460, 300, "sky")), line.words)
            assertEquals("Oh", line.background?.text)
            assertEquals(listOf(LyricWord(1500, 300, "Oh")), line.background?.words)
        }
    }

    @Test
    fun lyricsPlusUsesMirrorForASeparateBackingVocalRow() = runTest {
        client { request ->
            when (request.url.host) {
                "lyricsplus.binimum.org" -> respond("", HttpStatusCode.ServiceUnavailable)
                "lyricsplus.prjktla.my.id" -> respond("""
                    {"type":"Word","lyrics":[
                      {"time":1000,"text":"Hello sky","element":{"singer":"v1"},"syllabus":[
                        {"time":1000,"duration":200,"text":"Hel"},
                        {"time":1200,"duration":200,"text":"lo "},
                        {"time":1500,"duration":300,"text":"sky"}]},
                      {"time":1500,"text":"(Oh)","element":{"singer":"v2"},"syllabus":[
                        {"time":1500,"duration":250,"text":"(Oh)","isBackground":true}]}]}
                """.trimIndent())
                else -> error("Unexpected lyrics host")
            }
        }.use { http ->
            val line = LyricsPlusSource(http).search(track).single().lines.single()
            assertEquals("Hello sky", line.text)
            assertEquals("Oh", line.background?.text)
            assertEquals("v2", line.background?.agent)
        }
    }

    @Test
    fun lyricsPlusMarksLineOnlyJson() = runTest {
        client { respond("""{"type":"Line","lyrics":[{"time":1400,"duration":250,"text":"Sky","element":{"singer":"v2"}}]}""") }
            .use { http ->
                val candidate = LyricsPlusSource(http).search(track).single()
                assertFalse(candidate.wordSynced)
                assertEquals(1400L, candidate.lines.single().startMs)
                assertEquals(1650L, candidate.lines.single().endMs)
                assertEquals("v2", candidate.lines.single().agent)
            }
    }

    @Test
    fun lyricsPlusReturnsNoCandidatesWhenBothMirrorsMiss() = runTest {
        client { respond("", HttpStatusCode.NotFound) }.use { http ->
            assertTrue(LyricsPlusSource(http).search(track).isEmpty())
        }
    }

    @Test
    fun lyricsPlusReportsHttpErrorsWhenNoMirrorReturnsLyrics() = runTest {
        client { respond("", HttpStatusCode.BadGateway) }.use { http ->
            assertEquals("lyricsplus", assertFailsWith<LyricsSourceException> {
                LyricsPlusSource(http).search(track)
            }.sourceId)
        }
    }

    @Test
    fun simpMusicUsesVideoIdAndPrefersRichLyricsForTheMatchingDuration() = runTest {
        client { request ->
            assertEquals("api-lyrics.simpmusic.org", request.url.host)
            assertEquals("/v1/abcdefghijk", request.url.encodedPath)
            respond("""
                {"type":"success","data":[
                  {"videoId":"abcdefghijk","durationSeconds":160,"syncedLyrics":"[00:01.00]Wrong"},
                  {"videoId":"differentid","durationSeconds":92,"syncedLyrics":"[00:01.00]Wrong"},
                  {"videoId":"abcdefghijk","durationSeconds":92,"songTitle":"Window","artistName":"Blue Sky",
                   "richSyncLyrics":"[00:01.00]<00:01.00>Blue <00:01.45>sky<00:01.80>",
                   "syncedLyrics":"[00:01.00]Fallback"}]}
            """.trimIndent())
        }.use { http ->
            val candidate = SimpMusicSource(http).search(track).single()
            assertTrue(candidate.wordSynced)
            assertEquals(92_000L, candidate.durationMs)
            assertEquals("Blue sky", candidate.lines.single().text)
            assertEquals(
                listOf(LyricWord(1000, 450, "Blue "), LyricWord(1450, 350, "sky")),
                candidate.lines.single().words,
            )
        }
    }

    @Test
    fun simpMusicFallsBackToLineSyncedLrc() = runTest {
        client {
            respond("""{"type":"success","data":[{"richSyncLyrics":"","syncedLyrics":"[00:02.00]Sky"}]}""")
        }.use { http ->
            val candidate = SimpMusicSource(http).search(track).single()
            assertFalse(candidate.wordSynced)
            assertEquals(2000L, candidate.lines.single().startMs)
            assertEquals("Sky", candidate.lines.single().text)
        }
    }

    @Test
    fun simpMusicReturnsNoCandidatesOnNotFound() = runTest {
        client { respond("", HttpStatusCode.NotFound) }.use { http ->
            assertTrue(SimpMusicSource(http).search(track).isEmpty())
        }
    }

    @Test
    fun simpMusicSkipsTracksWithoutAYouTubeId() = runTest {
        client { error("Non-YouTube track must not query the service") }.use { http ->
            assertTrue(SimpMusicSource(http).search(track.copy(id = "file:placeholder")).isEmpty())
        }
    }

    @Test
    fun simpMusicReportsHttpErrors() = runTest {
        client { respond("", HttpStatusCode.Forbidden) }.use { http ->
            assertEquals("simpmusic", assertFailsWith<LyricsSourceException> {
                SimpMusicSource(http).search(track)
            }.sourceId)
        }
    }

    private fun client(
        expectSuccess: Boolean = false,
        reply: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine { request ->
        assertEquals(
            "Senada Mobile/1.0.2 (https://github.com/Zeeynrs/Senada-Mobile)",
            request.headers[HttpHeaders.UserAgent],
        )
        reply(request)
    }) {
        this.expectSuccess = expectSuccess
    }
}
