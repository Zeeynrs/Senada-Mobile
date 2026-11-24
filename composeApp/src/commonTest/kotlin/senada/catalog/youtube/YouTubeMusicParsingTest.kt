package senada.catalog.youtube

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import senada.catalog.CatalogException
import senada.model.Track

class YouTubeMusicParsingTest {
    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun firstSearchPageReadsSongsAndTheNextPageToken() {
        val page = parseSearchPage(json(SEARCH_PAGE_1))

        assertEquals(
            listOf("Teh Hijau", "Jatuh Suka", "Hati-Hati di Jalan"),
            page.items.map { it.title },
        )
        val first = page.items.first()
        assertEquals("yt:track:5F28ye50-Kc", first.id)
        assertEquals(listOf("Tulus"), first.artists)
        assertEquals(213_000L, first.durationMs)
        assertEquals(YOUTUBE_MUSIC_SOURCE, first.source)
        assertTrue(first.artworkUrl!!.contains("=w544-h544"))
        assertEquals("PAGE_2_TOKEN", page.nextPageToken)
    }

    @Test
    fun continuationPageReadsEveryLinkedArtist() {
        val page = parseSearchPage(json(SEARCH_PAGE_2))

        assertEquals(2, page.items.size)
        val duet = page.items.last()
        assertEquals("Tulus Hati Luhur Budi", duet.title)
        assertEquals(listOf("Revina Alvira", "Rian"), duet.artists)
        assertEquals(298_000L, duet.durationMs)
        assertNull(page.nextPageToken)
    }

    @Test
    fun durationsAcceptDotsColonsAndHours() {
        assertEquals(213_000L, parseDuration("3.33"))
        assertEquals(213_000L, parseDuration("3:33"))
        assertEquals(3_723_000L, parseDuration("1:02:03"))
        assertNull(parseDuration("242 jt pemutaran"))
    }

    @Test
    fun playerPrefersAacAndReportsWhenItExpires() {
        val audio = parsePlayer(json(PLAYER_OK), nowMs = 1_000)

        assertTrue(audio.location.contains("itag=140"))
        assertEquals("audio/mp4", audio.mimeType)
        assertEquals(1_000 + 21_540_000L, audio.expiresAtEpochMs)
        assertTrue(audio.headers.isEmpty())
    }

    @Test
    fun playerReportsTheChosenStreamsOwnDuration() {
        assertEquals(213_089L, parsePlayer(json(PLAYER_OK), 0, saveData = false).durationMs)
        assertEquals(213_159L, parsePlayer(json(PLAYER_OK), 0, saveData = true).durationMs)
    }

    @Test
    fun playerReadsRelativeLoudnessFromTheSelectedAacFormat() {
        val reply = json(loudnessPlayerReply())

        assertEquals(5.5200005f, parsePlayer(reply, 0).loudnessDb)
        assertEquals(-3.7f, parsePlayer(reply, 0, saveData = true).loudnessDb)
    }

    @Test
    fun playerDoesNotBorrowLoudnessFromAnotherFormatOrUseAbsoluteLoudness() {
        for (value in listOf(null, "null", "\"6\"", "true", "{}", "1e999")) {
            val audio = parsePlayer(json(loudnessPlayerReply(value)), 0)
            assertEquals("https://example.invalid/standard", audio.location)
            assertNull(audio.loudnessDb, value)
        }
    }

    private fun loudnessPlayerReply(standardLoudness: String? = "5.5200005"): String {
        val standardField = standardLoudness?.let { ""","loudnessDb":$it""" }.orEmpty()
        return """
            {
              "playabilityStatus":{"status":"OK"},
              "playerConfig":{"audioConfig":{"perceptualLoudnessDb":-8.48,"enablePerFormatLoudness":true}},
              "streamingData":{"adaptiveFormats":[
                {"itag":139,"mimeType":"audio/mp4","url":"https://example.invalid/low","loudnessDb":-3.7},
                {"itag":140,"mimeType":"audio/mp4","url":"https://example.invalid/standard",
                 "trackAbsoluteLoudnessLkfs":-8.48$standardField}
              ]}
            }
        """.trimIndent()
    }

    @Test
    fun playerFallsBackToVideoLengthWhenTheStreamHasNoDuration() {
        val withoutApprox = PLAYER_OK.replace(Regex(""""approxDurationMs": "\d+","""), "")
        val reply = json(
            withoutApprox.replace(""""streamingData"""", """"videoDetails": {"lengthSeconds": "214"}, "streamingData""""),
        )
        assertEquals(214_000L, parsePlayer(reply, 0).durationMs)
    }

    @Test
    fun dataSaverSelectsLowBitrateAacWithStandardAacAsFallback() {
        assertTrue(parsePlayer(json(PLAYER_OK), 0, saveData = true).location.contains("itag=139"))
        assertTrue(parsePlayer(json(PLAYER_OK), 0, saveData = false).location.contains("itag=140"))
        assertEquals("https://example.invalid/standard",
            parsePlayer(json(PLAYER_WITHOUT_LOW_BITRATE), 0, saveData = true).location)
    }

    @Test
    fun artworkResizesOnlyGoogleImagePathsAndPreservesOptions() {
        val base = "https://lh3.googleusercontent.com/album"
        val suffix = "-l90-rj?token=a=w9-h9#cover"
        assertEquals("$base=w120-h120$suffix", resizeGoogleArtwork("$base=w544-h544$suffix", 120))
        assertEquals("$base=w120-h120", resizeGoogleArtwork(base, 120))
        assertEquals("$base=w120-h120?token=abc", resizeGoogleArtwork("$base?token=abc", 120))
        for (other in listOf("https://example.invalid/a=w544-h544", "file:///a=w544-h544",
            "https://googleusercontent.com.example.invalid/a=w544-h544", "$base=s120")) {
            assertEquals(other, resizeGoogleArtwork(other, 120))
        }
    }

    @Test
    fun loginRequiredMeansTheSongIsUnavailable() {
        val error = assertFailsWith<CatalogException> { parsePlayer(json(PLAYER_LOGIN_REQUIRED), nowMs = 0) }
        assertEquals(CatalogException.Kind.Unavailable, error.kind)
    }

    @Test
    fun botCheckMeansYouTubeIsLimitingRequests() {
        val reply = """{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Sign in to confirm you're not a bot"}}"""
        val error = assertFailsWith<CatalogException> { parsePlayer(json(reply), nowMs = 0) }
        assertEquals(CatalogException.Kind.RateLimited, error.kind)
    }

    @Test
    fun playerWithoutMp4AudioIsUnavailable() {
        val reply = """{"playabilityStatus":{"status":"OK"},"streamingData":{"adaptiveFormats":[
            {"itag":251,"url":"https://example.invalid/a","mimeType":"audio/webm; codecs=\"opus\""}]}}"""
        val error = assertFailsWith<CatalogException> { parsePlayer(json(reply), nowMs = 0) }
        assertEquals(CatalogException.Kind.Unavailable, error.kind)
    }

    @Test
    fun trackIdsMatchTheDesktopFormat() {
        assertEquals("yt:track:abc", youTubeTrackId("abc"))
        assertEquals("abc", videoIdOf(Track("yt:track:abc", "T", emptyList(), source = YOUTUBE_MUSIC_SOURCE)))
        assertNull(videoIdOf(Track("demo:track:hujan", "T", emptyList(), source = "demo")))
    }
}
