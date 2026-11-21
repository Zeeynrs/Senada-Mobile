package senada.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import senada.model.Track

class LyricsRepositoryTest {
    private val track = Track("yt:track:a", "Jendela & Awan", listOf("Rintik", "Pagi"), 91_700, source = "ytmusic")
    private val requests = mutableListOf<HttpRequestData>()

    private fun repository(reply: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        LyricsRepository(HttpClient(MockEngine { request ->
            requests += request
            reply(request)
        }))

    @Test
    fun exactMatchUsesTrackMetadataAndCachesSyncedLyricsById() = runTest {
        val repo = repository { respond("""{"syncedLyrics":"[00:01.25]Jendela biru"}""") }
        val expected = LyricsDocument.Synced(listOf(LyricLine(1250, "Jendela biru")))
        assertEquals(expected, repo.get(track))
        assertEquals(expected, repo.get(track.copy(title = "Judul baru")))
        val request = requests.single()
        assertEquals("lrclib.net", request.url.host)
        assertEquals("/api/get", request.url.encodedPath)
        assertEquals(track.title, request.url.parameters["track_name"])
        assertEquals("Rintik", request.url.parameters["artist_name"])
        assertEquals("92", request.url.parameters["duration"])
        assertEquals("Senada Mobile/0.1.0 (https://github.com/Zeeynrs/Senada-Mobile)", request.headers[HttpHeaders.UserAgent])
    }

    @Test
    fun emptyExactMatchSearchesAndPrefersSyncedOverPlainAndInstrumental() = runTest {
        val repo = repository { request ->
            if (request.url.encodedPath == "/api/get") respond("""{"syncedLyrics":"[ar:Kosong]","plainLyrics":null}""")
            else respond("""[{"instrumental":true},{"plainLyrics":"Pagi"},{"syncedLyrics":"[00:02]Embun"}]""")
        }
        assertEquals(LyricsDocument.Synced(listOf(LyricLine(2000, "Embun"))), repo.get(track.copy(durationMs = null)))
        assertEquals(listOf("/api/get", "/api/search"), requests.map { it.url.encodedPath })
        for (request in requests) {
            assertNull(request.url.parameters["duration"])
            assertEquals(track.title, request.url.parameters["track_name"])
            assertEquals("Rintik", request.url.parameters["artist_name"])
        }
    }

    @Test
    fun searchPrefersPlainToInstrumentalAndKeepsVerseBreaks() = runTest {
        val repo = repository { request ->
            if (request.url.encodedPath == "/api/get") respond("{}", HttpStatusCode.NotFound)
            else respond("""[{"instrumental":true},{"plainLyrics":"Terang\n\nRedup"}]""")
        }
        assertEquals(LyricsDocument.Plain(listOf("Terang", "", "Redup")), repo.get(track))
        assertNull(requests.last().url.parameters["duration"])
    }

    @Test
    fun instrumentalIsAvailableWithoutLyricsText() = runTest {
        val repo = repository { respond("""{"instrumental":true,"syncedLyrics":null,"plainLyrics":null}""") }
        assertEquals(LyricsDocument.Instrumental, repo.get(track))
        assertEquals(1, requests.size)
    }

    @Test
    fun unknownArtistUsesTitleSearchAndAcceptsAnInstrumentalMatch() = runTest {
        val repo = repository { respond("""[{"instrumental":true}]""") }
        assertEquals(LyricsDocument.Instrumental, repo.get(track.copy(artists = emptyList())))
        val request = requests.single()
        assertEquals("/api/search", request.url.encodedPath)
        assertEquals(track.title, request.url.parameters["track_name"])
        assertNull(request.url.parameters["artist_name"])
    }

    @Test
    fun missesAreCachedAndDemoTracksNeedNoNetwork() = runTest {
        val repo = repository { respond("{}", HttpStatusCode.NotFound) }
        assertEquals(LyricsDocument.Unavailable, repo.get(track.copy(source = "demo")))
        assertEquals(0, requests.size)
        repeat(2) { assertEquals(LyricsDocument.Unavailable, repo.get(track)) }
        assertEquals(listOf("/api/get", "/api/search"), requests.map { it.url.encodedPath })
    }

    @Test
    fun serverErrorsCanBeRetried() = runTest {
        val repo = repository {
            if (requests.size == 1) respond("{}", HttpStatusCode.ServiceUnavailable)
            else respond("""{"plainLyrics":"Langit jingga"}""")
        }
        assertFailsWith<LyricsException> { repo.get(track) }
        assertEquals(LyricsDocument.Plain(listOf("Langit jingga")), repo.get(track))
    }

    @Test
    fun connectionFailureHasAnIndonesianMessageAndCanBeRetried() = runTest {
        val repo = repository {
            if (requests.size == 1) throw IllegalStateException("offline")
            respond("""{"plainLyrics":"Langit jingga"}""")
        }
        val error = assertFailsWith<LyricsException> { repo.get(track) }
        assertEquals("Tidak dapat terhubung ke LRCLIB. Periksa koneksi internet.", error.userMessage)
        assertEquals(LyricsDocument.Plain(listOf("Langit jingga")), repo.get(track))
    }

    @Test
    fun unreadableSearchReplyIsAnError() = runTest {
        val repo = repository { request ->
            if (request.url.encodedPath == "/api/get") respond("{}") else respond("<html>")
        }
        val error = assertFailsWith<LyricsException> { repo.get(track) }
        assertEquals("Balasan LRCLIB tidak dapat dibaca.", error.userMessage)
    }
}
