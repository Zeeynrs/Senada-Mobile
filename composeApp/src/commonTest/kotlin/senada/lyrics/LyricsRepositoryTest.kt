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
        val decoratedTrack = track.copy(title = "${track.title} (Official Audio)")
        val repo = repository { respond("""{"syncedLyrics":"[00:01.25]Jendela biru"}""") }
        val expected = LyricsDocument.Synced(listOf(LyricLine(1250, "Jendela biru")))
        assertEquals(expected, repo.get(decoratedTrack))
        assertEquals(expected, repo.get(track.copy(title = "Judul baru")))
        val request = requests.single()
        assertEquals("lrclib.net", request.url.host)
        assertEquals("/api/get", request.url.encodedPath)
        assertEquals(decoratedTrack.title, request.url.parameters["track_name"])
        assertEquals("Rintik", request.url.parameters["artist_name"])
        assertEquals("92", request.url.parameters["duration"])
        assertEquals("Senada Mobile/1.0.0 (https://github.com/Zeeynrs/Senada-Mobile)", request.headers[HttpHeaders.UserAgent])
    }

    @Test
    fun clearingCacheFetchesLyricsAgainForTheSameTrack() = runTest {
        val repo = repository {
            if (requests.size == 1) respond("""{"plainLyrics":"Jendela biru"}""")
            else respond("""{"plainLyrics":"Jendela jingga"}""")
        }
        val original = LyricsDocument.Plain(listOf("Jendela biru"))
        assertEquals(original, repo.get(track))
        assertEquals(original, repo.get(track))
        assertEquals(1, requests.size)

        repo.clearCache()

        assertEquals(LyricsDocument.Plain(listOf("Jendela jingga")), repo.get(track))
        assertEquals(listOf("/api/get", "/api/get"), requests.map { it.url.encodedPath })
    }

    @Test
    fun emptyExactMatchSearchesAndPrefersSyncedOverPlainAndInstrumental() = runTest {
        val decoratedTrack = track.copy(title = "${track.title} [feat. Cahaya]", durationMs = null)
        val repo = repository { request ->
            if (request.url.encodedPath == "/api/get") respond("""{"syncedLyrics":"[ar:Kosong]","plainLyrics":null}""")
            else respond("""[{"instrumental":true},{"plainLyrics":"Pagi"},{"syncedLyrics":"[00:02]Embun"}]""")
        }
        assertEquals(LyricsDocument.Synced(listOf(LyricLine(2000, "Embun"))), repo.get(decoratedTrack))
        assertEquals(listOf("/api/get", "/api/search"), requests.map { it.url.encodedPath })
        for (request in requests) {
            assertNull(request.url.parameters["duration"])
            assertEquals(decoratedTrack.title, request.url.parameters["track_name"])
            assertEquals("Rintik", request.url.parameters["artist_name"])
        }
    }

    @Test
    fun titleRetryRemovesOnlyRecognizedSuffixes() = runTest {
        val examples = listOf(
            "Jendela & Awan (feat. Fresly Nikijuluw)" to "Jendela & Awan",
            "Jendela & Awan [FEAT. X]" to "Jendela & Awan",
            "Jendela & Awan Ft. X" to "Jendela & Awan",
            "Jendela & Awan (Official Audio)" to "Jendela & Awan",
            "Jendela & Awan (Official Music Video)" to "Jendela & Awan",
            "Jendela & Awan (Lyric Video)" to "Jendela & Awan",
            "Jendela & Awan (Remastered 2011)" to "Jendela & Awan",
            "Jendela & Awan (Live)" to "Jendela & Awan",
            "Jendela & Awan - Remastered" to "Jendela & Awan",
            "Jendela & Awan (Bagian Dua) ft. X [Official Audio]" to "Jendela & Awan (Bagian Dua)",
            "Hati-Hati di Jalan" to "Hati-Hati di Jalan",
            "Tulus Hati Luhur Budi" to "Tulus Hati Luhur Budi",
            "Jendela - Awan (Bagian Dua)" to "Jendela - Awan (Bagian Dua)",
            "Jendela (Live) di Atap" to "Jendela (Live) di Atap",
            "(Live)" to "(Live)",
        )
        val repo = repository { request ->
            if (request.url.encodedPath == "/api/get") respond("{}", HttpStatusCode.NotFound)
            else respond("[]")
        }

        for ((index, example) in examples.withIndex()) {
            val (originalTitle, cleanTitle) = example
            requests.clear()
            assertEquals(LyricsDocument.Unavailable, repo.get(track.copy(id = "title:$index", title = originalTitle)))
            val expected = buildList {
                add("/api/get" to originalTitle)
                add("/api/search" to originalTitle)
                if (cleanTitle != originalTitle) {
                    add("/api/get" to cleanTitle)
                    add("/api/search" to cleanTitle)
                }
            }
            assertEquals(expected, requests.map { it.url.encodedPath to it.url.parameters["track_name"] }, originalTitle)
        }
    }

    @Test
    fun unavailableOriginalResultsUseTheCleanTitleAndCacheTheMatchById() = runTest {
        val decoratedTrack = track.copy(title = "${track.title} (Lyric Video)")
        val repo = repository { request ->
            when (request.url.parameters["track_name"] to request.url.encodedPath) {
                decoratedTrack.title to "/api/get" -> respond("""{"syncedLyrics":"[ar:Rintik]"}""")
                decoratedTrack.title to "/api/search" -> respond("""[{"plainLyrics":" "}]""")
                track.title to "/api/get" -> respond("{}", HttpStatusCode.NotFound)
                track.title to "/api/search" -> respond("""[{"plainLyrics":"Bintang jatuh"}]""")
                else -> error("Permintaan tak terduga: ${request.url}")
            }
        }

        val expected = LyricsDocument.Plain(listOf("Bintang jatuh"))
        assertEquals(expected, repo.get(decoratedTrack))
        assertEquals(expected, repo.get(track.copy(title = "Judul baru")))
        assertEquals(listOf("/api/get", "/api/search", "/api/get", "/api/search"), requests.map { it.url.encodedPath })
        assertEquals(
            listOf(decoratedTrack.title, decoratedTrack.title, track.title, track.title),
            requests.map { it.url.parameters["track_name"] },
        )
        assertEquals(List(4) { "Rintik" }, requests.map { it.url.parameters["artist_name"] })
        assertEquals(listOf("92", null, "92", null), requests.map { it.url.parameters["duration"] })
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
    fun connectionFailureHasAUserMessageAndCanBeRetried() = runTest {
        val repo = repository {
            if (requests.size == 1) throw IllegalStateException("offline")
            respond("""{"plainLyrics":"Langit jingga"}""")
        }
        val error = assertFailsWith<LyricsException> { repo.get(track) }
        assertEquals("Cannot connect to the lyrics service. Check your internet connection.", error.userMessage)
        assertEquals("Tidak dapat terhubung ke layanan lirik. Periksa koneksi internet.", error.userMessage(senada.app.AppLanguage.Indonesian))
        assertEquals(LyricsDocument.Plain(listOf("Langit jingga")), repo.get(track))
    }

    @Test
    fun unreadableSearchReplyIsAnError() = runTest {
        val repo = repository { request ->
            if (request.url.encodedPath == "/api/get") respond("{}") else respond("<html>")
        }
        val error = assertFailsWith<LyricsException> { repo.get(track) }
        assertEquals("Lyrics response could not be read.", error.userMessage)
        assertEquals("Balasan lirik tidak dapat dibaca.", error.userMessage(senada.app.AppLanguage.Indonesian))
    }

    @Test
    fun searchPrefersVersionAndDurationMatchedSyncedCandidateOverEarlierMismatch() = runTest {
        val repo = repository { request ->
            if (request.url.encodedPath == "/api/get") {
                respond("{}", HttpStatusCode.NotFound)
            } else {
                respond(
                    """
                    [
                      {
                        "trackName": "Jendela & Awan (Live)",
                        "artistName": "Rintik",
                        "duration": 140.0,
                        "syncedLyrics": "[00:35.00]Versi live terlambat"
                      },
                      {
                        "trackName": "Jendela & Awan",
                        "artistName": "Rintik",
                        "duration": 92.0,
                        "syncedLyrics": "[00:05.00]Versi studio cocok"
                      }
                    ]
                    """.trimIndent(),
                )
            }
        }
        assertEquals(LyricsDocument.Synced(listOf(LyricLine(5_000L, "Versi studio cocok"))), repo.get(track))
    }

    @Test
    fun mismatchedRecordingDurationFallsBackToPlainInsteadOfDesyncedTiming() = runTest {
        val liveTrack = track.copy(id = "yt:track:live", title = "Jendela & Awan (Live)", durationMs = 240_000L)
        val repo = repository { request ->
            if (request.url.encodedPath == "/api/get") {
                respond(
                    """
                    {
                      "trackName": "Jendela & Awan",
                      "artistName": "Rintik",
                      "duration": 160.0,
                      "syncedLyrics": "[00:10.00]Baris satu\n[00:20.00]Baris dua",
                      "plainLyrics": "Baris satu\nBaris dua"
                    }
                    """.trimIndent(),
                )
            } else {
                respond("[]")
            }
        }
        assertEquals(LyricsDocument.Plain(listOf("Baris satu", "Baris dua")), repo.get(liveTrack))
    }

    @Test
    fun timestampsBeyondTrackDurationRejectBadSyncAndUsePlainFallback() = runTest {
        val shortTrack = track.copy(id = "yt:track:short", durationMs = 20_000L)
        val repo = repository { request ->
            if (request.url.encodedPath == "/api/get") {
                respond(
                    """
                    {
                      "trackName": "Jendela & Awan",
                      "artistName": "Rintik",
                      "syncedLyrics": "[00:40.00]Di luar durasi\n[01:10.00]Jauh lewat"
                    }
                    """.trimIndent(),
                )
            } else {
                respond("[]")
            }
        }
        assertEquals(LyricsDocument.Plain(listOf("Di luar durasi", "Jauh lewat")), repo.get(shortTrack))
    }
}
