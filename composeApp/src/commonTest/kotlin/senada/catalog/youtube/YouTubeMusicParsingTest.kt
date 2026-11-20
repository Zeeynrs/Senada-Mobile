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
