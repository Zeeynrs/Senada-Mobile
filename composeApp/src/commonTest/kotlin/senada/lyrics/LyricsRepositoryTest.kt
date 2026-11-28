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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import senada.lyrics.sources.LyricsCandidate
import senada.lyrics.sources.LyricsSource
import senada.lyrics.sources.LyricsSourceException
import senada.model.Track

class LyricsRepositoryTest {
    private val track = Track("yt:track:a", "Jendela & Awan", listOf("Rintik", "Pagi"), 91_700, source = "ytmusic")
    private val requests = mutableListOf<HttpRequestData>()

    private fun repository(reply: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        LyricsRepository(HttpClient(MockEngine { request ->
            requests += request
            reply(request)
        }))

    private fun timedCandidate(duration: Double, lyrics: String, artist: String = track.artists.first()) = buildJsonObject {
        put("trackName", track.title)
        put("artistName", artist)
        put("duration", duration)
        put("syncedLyrics", lyrics)
    }

    @Test
    fun fetchesMatchingRomanizationWithoutReplacingTheOriginalTiming() = runTest {
        val ttml = """
            <tt xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
              <head><metadata><transliterations>
                <transliteration xml:lang="ja-Kana"><text for="L1">よる</text></transliteration>
                <itunes:transliteration xml:lang="ja-Latn">
                  <itunes:text for="L1"><span begin="10.0s">yoru</span></itunes:text>
                </itunes:transliteration>
              </transliterations></metadata></head>
              <body><div><p begin="10.0s" itunes:key="L1">夜</p></div></body>
            </tt>
        """.trimIndent()
        val repo = repository { request ->
            if (request.url.host == "lrclib.net") {
                respond("""{"syncedLyrics":"[00:08.00]<00:08.00>夜<00:09.00>\n[00:12.00]空"}""")
            }
            else respond(buildJsonObject { put("ttml", JsonPrimitive(ttml)) }.toString())
        }
        val original = repo.get(track) as LyricsDocument.Synced
        assertEquals(listOf("lrclib.net"), requests.map { it.url.host })
        val document = repo.withRomanization(track, original) as LyricsDocument.Synced

        assertEquals(listOf(8000L, 12000L), document.lines.map { it.startMs })
        assertEquals(original.lines.first().words, document.lines.first().words)
        assertEquals(listOf("yoru", null), document.lines.map { it.romanization })
        assertEquals(listOf("lrclib.net", "api.betterlyrics.org"), requests.map { it.url.host })
        assertEquals(document, repo.withRomanization(track, original))
        assertEquals(
            LyricsDocument.Plain(listOf("夜", "空"), mapOf(0 to "yoru")),
            repo.withRomanization(track, LyricsDocument.Plain(listOf("夜", "空"))),
        )
        assertEquals(2, requests.size)
    }

    @Test
    fun unavailableRomanizationKeepsTheOriginalLyrics() = runTest {
        val repo = repository { respond("", HttpStatusCode.Unauthorized) }
        val original = LyricsDocument.Plain(listOf("夜"))

        assertEquals(original, repo.withRomanization(track, original))
    }

    @Test
    fun hangulCanBeRomanizedWithoutAProviderRequest() = runTest {
        val repo = repository { error("Hangul should be romanized locally") }
        val original = LyricsDocument.Plain(listOf("감사합니다, hello!"))

        assertEquals(original, repo.withRomanization(track, original))
        assertEquals("gamsahamnida, hello!", lyricRomanization(original.lines.single()))
        assertEquals("silla", lyricRomanization("신라"))
        assertEquals("gachi", lyricRomanization("같이", "gachi"))
        assertNull(lyricRomanization("hello!"))
    }

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
        assertEquals("Senada Mobile/1.0.2 (https://github.com/Zeeynrs/Senada-Mobile)", request.headers[HttpHeaders.UserAgent])
    }

    @Test
    fun clearingCacheFetchesLyricsAgainForTheSameTrack() = runTest {
        val track = track.copy(durationMs = null)
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
        val repo = repository { request ->
            if (requests.size == 1) respond("{}", HttpStatusCode.ServiceUnavailable)
            else if (request.url.encodedPath == "/api/search") respond("[]")
            else respond("""{"plainLyrics":"Langit jingga"}""")
        }
        assertFailsWith<LyricsException> { repo.get(track) }
        assertEquals(LyricsDocument.Plain(listOf("Langit jingga")), repo.get(track))
    }

    @Test
    fun connectionFailureHasAUserMessageAndCanBeRetried() = runTest {
        val repo = repository { request ->
            if (requests.size == 1) throw IllegalStateException("offline")
            if (request.url.encodedPath == "/api/search") respond("[]")
            else respond("""{"plainLyrics":"Langit jingga"}""")
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
    fun rejectedExactMatchSearchesAndPicksTheOneSecondMatchOverNineSeconds() = runTest {
        val far = timedCandidate(100.7, "[00:05.00]<00:05.00>Far<00:06.00>")
        val near = timedCandidate(90.7, "[00:02.00]Near")
        val repo = repository { request ->
            if (request.url.encodedPath == "/api/get") respond(far.toString())
            else respond("[$far,$near]")
        }

        assertEquals(LyricsDocument.Synced(listOf(LyricLine(2_000, "Near"))), repo.get(track))
        assertEquals(listOf("/api/get", "/api/search"), requests.map { it.url.encodedPath })
    }

    @Test
    fun searchReturnsPlainWhenTheOnlySyncedCandidateIsNineSecondsOff() = runTest {
        val far = timedCandidate(100.7, "[00:05.00]Far")
        val repo = repository { request ->
            if (request.url.encodedPath == "/api/get") respond("", HttpStatusCode.NotFound)
            else respond("[$far]")
        }

        assertEquals(LyricsDocument.Plain(listOf("Far")), repo.get(track))
    }

    @Test
    fun searchAcceptsFourSecondsButPrefersOneSecondOverWordTiming() = runTest {
        val usable = timedCandidate(95.7, "[00:05.00]<00:05.00>Usable<00:06.00>")
        val near = timedCandidate(92.7, "[00:02.00]Near")
        var matches = "[$usable,$near]"
        val repo = repository { request ->
            if (request.url.encodedPath == "/api/get") respond("""{"plainLyrics":"Plain"}""")
            else respond(matches)
        }

        assertEquals(LyricsDocument.Synced(listOf(LyricLine(2_000, "Near"))), repo.get(track))
        assertEquals(listOf("/api/get", "/api/search"), requests.map { it.url.encodedPath })
        matches = "[$usable]"
        val accepted = repo.get(track.copy(id = "yt:track:b")) as LyricsDocument.Synced
        assertEquals("Usable", accepted.lines.single().text)
    }

    @Test
    fun searchPrefersTheRightArtistWithinThePreferredDurationRange() = runTest {
        val wrongArtist = timedCandidate(92.2, "[00:05.00]Wrong artist", artist = "Another artist")
        val rightArtist = timedCandidate(93.2, "[00:02.00]Right artist")
        val repo = repository { request ->
            if (request.url.encodedPath == "/api/get") respond("", HttpStatusCode.NotFound)
            else respond("[$wrongArtist,$rightArtist]")
        }

        assertEquals(LyricsDocument.Synced(listOf(LyricLine(2_000, "Right artist"))), repo.get(track))
    }

    @Test
    fun searchUsesTheClosestDurationWhenTheBucketAndMetadataTie() = runTest {
        val farther = timedCandidate(93.7, "[00:05.00]Farther")
        val closer = timedCandidate(92.2, "[00:02.00]Closer")
        val repo = repository { request ->
            if (request.url.encodedPath == "/api/get") respond("", HttpStatusCode.NotFound)
            else respond("[$farther,$closer]")
        }

        assertEquals(LyricsDocument.Synced(listOf(LyricLine(2_000, "Closer"))), repo.get(track))
    }

    @Test
    fun exactPlainLyricsRemainAvailableWhenSearchHasNoCompatibleSync() = runTest {
        val far = timedCandidate(100.7, "[00:05.00]Far")
        for (searchStatus in listOf(HttpStatusCode.OK, HttpStatusCode.ServiceUnavailable)) {
            requests.clear()
            val repo = repository { request ->
                if (request.url.encodedPath == "/api/get") respond("""{"plainLyrics":"Exact"}""")
                else respond("[$far]", searchStatus)
            }

            assertEquals(LyricsDocument.Plain(listOf("Exact")), repo.get(track))
            assertEquals(listOf("/api/get", "/api/search"), requests.map { it.url.encodedPath })
        }
    }

    @Test
    fun unknownDurationsStillRankSyncedLyricsByMetadataAndWordTiming() = runTest {
        val near = timedCandidate(92.7, "[00:02.00]Near")
        val timed = timedCandidate(100.7, "[01:40.00]<01:40.00>Timed<01:41.00>")
        val repo = repository { request ->
            if (request.url.encodedPath == "/api/get") respond("", HttpStatusCode.NotFound)
            else respond("[$near,$timed]")
        }

        for (duration in listOf(null, 0L)) {
            val result = repo.get(track.copy(id = "unknown:$duration", durationMs = duration)) as LyricsDocument.Synced
            assertEquals("Timed", result.lines.single().text)
        }
    }

    @Test
    fun durationChangeForTheSameTrackRechecksTheCachedLyricsChoice() = runTest {
        val far = timedCandidate(100.7, "[00:05.00]Far")
        val repo = repository { request ->
            if (request.url.encodedPath == "/api/get") respond(far.toString())
            else respond("[]")
        }

        val unknownDuration = track.copy(durationMs = null)
        val original = LyricsDocument.Synced(listOf(LyricLine(5_000, "Far")))
        assertEquals(original, repo.get(unknownDuration))
        assertEquals(original, repo.get(unknownDuration))
        assertEquals(LyricsDocument.Plain(listOf("Far")), repo.get(track))
        assertEquals(LyricsDocument.Plain(listOf("Far")), repo.get(track))
        assertEquals(listOf("/api/get", "/api/get", "/api/search"), requests.map { it.url.encodedPath })
    }

    @Test
    fun firstAndLastLineTimingLimitsAreInclusive() = runTest {
        val shortTrack = track.copy(durationMs = 20_000L)
        val cases = listOf(
            "[00:12.000]First\n[00:23.000]Last" to true,
            "[00:12.001]First\n[00:23.000]Last" to false,
            "[00:00.000]First\n[00:23.001]Last" to false,
        )
        for ((lrc, synced) in cases) {
            val candidate = timedCandidate(25.0, lrc)
            val repo = repository { request ->
                if (request.url.encodedPath == "/api/get") respond(candidate.toString())
                else respond("[]")
            }

            val result = repo.get(shortTrack)
            if (synced) {
                assertEquals(listOf(12_000L, 23_000L), (result as LyricsDocument.Synced).lines.map { it.startMs })
            } else {
                assertEquals(LyricsDocument.Plain(listOf("First", "Last")), result)
            }
        }
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

    private class FakeSource(
        override val id: String,
        private val result: () -> List<LyricsCandidate>,
    ) : LyricsSource {
        var calls = 0
        override suspend fun search(track: Track): List<LyricsCandidate> {
            calls++
            return result()
        }
    }

    private fun wordLine(startMs: Long, vararg words: String): LyricLine = LyricLine(
        startMs = startMs,
        text = words.joinToString(" "),
        words = words.mapIndexed { index, word -> LyricWord(startMs + index * 300L, 300L, word) },
    )

    private fun wordCandidate(sourceId: String, durationMs: Long?, firstWord: String = "Hujan") = LyricsCandidate(
        sourceId = sourceId,
        lines = listOf(wordLine(1_000L, firstWord, "turun"), wordLine(4_000L, "lagi", "pagi")),
        wordSynced = true,
        title = track.title,
        artist = track.artists.first(),
        durationMs = durationMs,
    )

    private fun repositoryWith(vararg sources: LyricsSource) = LyricsRepository(
        HttpClient(MockEngine { request ->
            requests += request
            // LRCLIB has nothing for this track: no exact match, an empty search.
            respond(if (request.url.encodedPath == "/api/get") "null" else "[]")
        }),
        wordSources = sources.toList(),
    )

    @Test
    fun wordTimedSourceWinsOverLrclibWhenItsDurationMatches() = runTest {
        val source = FakeSource("betterlyrics") { listOf(wordCandidate("betterlyrics", 92_000L)) }
        val document = repositoryWith(source).get(track) as LyricsDocument.Synced

        assertEquals("Hujan turun", document.lines.first().text)
        assertEquals(2, document.lines.first().words.size)
        assertEquals(emptyList(), requests.map { it.url.host })
    }

    @Test
    fun wordTimedCandidateFromAnotherRecordingIsSkipped() = runTest {
        val tooLong = FakeSource("betterlyrics") { listOf(wordCandidate("betterlyrics", 110_000L, firstWord = "Salah")) }
        val good = FakeSource("lyricsplus") { listOf(wordCandidate("lyricsplus", 91_000L)) }
        val document = repositoryWith(tooLong, good).get(track) as LyricsDocument.Synced

        assertEquals("Hujan turun", document.lines.first().text)
    }

    @Test
    fun closerDurationBucketBeatsSourceOrderAndSourceOrderBreaksTies() = runTest {
        val farther = FakeSource("betterlyrics") { listOf(wordCandidate("betterlyrics", 94_500L, firstWord = "Jauh")) }
        val closer = FakeSource("lyricsplus") { listOf(wordCandidate("lyricsplus", 91_900L, firstWord = "Dekat")) }
        val sameBucket = FakeSource("simpmusic") { listOf(wordCandidate("simpmusic", 92_500L, firstWord = "Kedua")) }

        assertEquals("Dekat turun", (repositoryWith(farther, closer).get(track) as LyricsDocument.Synced).lines.first().text)
        assertEquals("Dekat turun", (repositoryWith(closer, sameBucket).get(track.copy(id = "yt:track:b")) as LyricsDocument.Synced).lines.first().text)
    }

    @Test
    fun failingSourcesAreSkippedAndLineOnlySourcesBackUpLrclib() = runTest {
        val broken = FakeSource("simpmusic") { throw LyricsSourceException("simpmusic", "HTTP 403") }
        val lineOnly = FakeSource("netease") { listOf(wordCandidate("netease", 91_700L).copy(wordSynced = false)) }
        val document = repositoryWith(broken, lineOnly).get(track) as LyricsDocument.Synced

        // LRCLIB is asked first for line timing, and only when it has nothing is the line-only source used.
        assertEquals(listOf("lrclib.net"), requests.map { it.url.host }.distinct())
        assertEquals("Hujan turun", document.lines.first().text)
    }

    @Test
    fun wordCandidateWithoutDurationMustStillFitTheTrack() = runTest {
        val overEnd = wordCandidate("kugou", null).copy(lines = listOf(wordLine(200_000L, "Lewat", "akhir")))
        val source = FakeSource("kugou") { listOf(overEnd) }

        assertEquals(LyricsDocument.Unavailable, repositoryWith(source).get(track))
    }
}
