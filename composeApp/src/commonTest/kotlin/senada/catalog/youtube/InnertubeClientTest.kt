package senada.catalog.youtube

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
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import senada.catalog.CatalogException
import senada.model.Track

class InnertubeClientTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun innertube(reply: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        InnertubeClient(HttpClient(MockEngine { request -> requests += request; reply(request) }))

    private fun MockRequestHandleScope.json(text: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(text, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.jsonBody(): JsonObject =
        Json.parseToJsonElement((body as TextContent).text).jsonObject

    private val track = Track("yt:track:5F28ye50-Kc", "Teh Hijau", listOf("Tulus"), source = YOUTUBE_MUSIC_SOURCE)

    @Test
    fun searchAsksForSongsAndPagesWithTheSameVisitor() = runTest {
        val client = innertube { json(if (it.url.parameters["ctoken"] == null) SEARCH_PAGE_1 else SEARCH_PAGE_2) }
        val catalog = YouTubeMusicCatalog(client)

        val first = catalog.search("  tulus ")
        val second = catalog.search("tulus", first.nextPageToken)

        assertEquals(3, first.items.size)
        assertEquals(2, second.items.size)
        val (search, continuation) = requests
        assertEquals("tulus", search.jsonBody().string("query"))
        assertTrue(search.jsonBody().string("params")!!.isNotEmpty())
        assertEquals("PAGE_2_TOKEN", continuation.url.parameters["ctoken"])
        assertEquals("VISITOR_FROM_SEARCH", continuation.headers["X-Goog-Visitor-Id"])
        assertEquals("VISITOR_FROM_SEARCH", continuation.jsonBody().string("context", "client", "visitorData"))
    }

    @Test
    fun blankQueryDoesNotCallYouTube() = runTest {
        val page = YouTubeMusicCatalog(innertube { json(SEARCH_PAGE_1) }).search("   ")

        assertEquals(emptyList(), page.items)
        assertEquals(emptyList(), requests)
    }

    @Test
    fun playerFetchesAVisitorFirstAndUsesTheVisionOsClient() = runTest {
        val resolver = YouTubeMusicResolver(
            innertube { request ->
                if (request.url.encodedPath.endsWith("visitor_id")) {
                    json("""{"responseContext":{"visitorData":"VISITOR_NEW"}}""")
                } else {
                    json(PLAYER_OK)
                }
            },
            now = { 0 },
        )

        val audio = resolver.resolve(track)

        assertTrue(audio.location.contains("itag=140"))
        val (visitor, player) = requests
        assertTrue(visitor.url.encodedPath.endsWith("/visitor_id"))
        assertEquals("VISIONOS", player.jsonBody().string("context", "client", "clientName"))
        assertEquals("5F28ye50-Kc", player.jsonBody().string("videoId"))
        assertEquals("VISITOR_NEW", player.headers["X-Goog-Visitor-Id"])
    }

    @Test
    fun resolverUsesTheDataSaverChoiceForEachRequest() = runTest {
        val resolver = YouTubeMusicResolver(innertube { json(PLAYER_OK) })
        assertTrue(resolver.resolve(track, saveData = true).location.contains("itag=139"))
        assertTrue(resolver.resolve(track, saveData = false).location.contains("itag=140"))
    }

    @Test
    fun http429IsReportedAsRateLimiting() = runTest {
        val catalog = YouTubeMusicCatalog(innertube { json("{}", HttpStatusCode.TooManyRequests) })

        val error = assertFailsWith<CatalogException> { catalog.search("tulus") }
        assertEquals(CatalogException.Kind.RateLimited, error.kind)
    }

    @Test
    fun serverErrorsAndBrokenRepliesAreReported() = runTest {
        val down = YouTubeMusicCatalog(innertube { json("{}", HttpStatusCode.ServiceUnavailable) })
        assertEquals(CatalogException.Kind.Network, assertFailsWith<CatalogException> { down.search("a") }.kind)

        val broken = YouTubeMusicCatalog(innertube { json("<html>") })
        assertEquals(CatalogException.Kind.Unreadable, assertFailsWith<CatalogException> { broken.search("a") }.kind)
    }
}
