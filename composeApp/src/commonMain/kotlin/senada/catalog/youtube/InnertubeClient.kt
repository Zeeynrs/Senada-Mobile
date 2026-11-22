package senada.catalog.youtube

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import senada.catalog.CatalogException
import senada.catalog.CatalogException.Kind

/** Identitas client Innertube yang dikirim ke YouTube. */
internal class InnertubeClientInfo(
    val name: String,
    val version: String,
    val id: Int,
    val userAgent: String,
    val webOrigin: String? = null,
    val device: Map<String, String> = emptyMap(),
)

/**
 * Akses Innertube tanpa akun, sama seperti Senada desktop (`senada/ytmusic.py` dan `senada/streams.py`).
 * Pencarian memakai client web YouTube Music. Alamat audio diminta dengan client visionOS, yang memberi
 * URL langsung tanpa tantangan JavaScript dan tanpa PO token. Detail uji ada di
 * `docs/superpowers/research/2026-11-21-evaluasi-sumber-audio.md`.
 */
internal class InnertubeClient(
    private val http: HttpClient,
    private val hl: String = "id",
    private val gl: String = "ID",
) {
    private val visitorLock = Mutex()
    private var visitorData: String? = null

    suspend fun search(query: String, params: String = SONGS_FILTER): JsonObject = post(
        "$MUSIC_API/search",
        WEB_REMIX,
        buildJsonObject {
            put("query", query)
            put("params", params)
        },
    )

    suspend fun searchContinuation(token: String): JsonObject =
        post("$MUSIC_API/search", WEB_REMIX, JsonObject(emptyMap()), continuation = token)

    suspend fun searchVideos(query: String): JsonObject = post(
        "$MUSIC_API/search",
        WEB_REMIX,
        buildJsonObject {
            put("query", query)
            put("params", VIDEOS_FILTER)
        },
    )

    suspend fun browse(browseId: String): JsonObject = post(
        "$MUSIC_API/browse",
        WEB_REMIX,
        buildJsonObject { put("browseId", browseId) },
    )

    suspend fun browseContinuation(token: String): JsonObject = post(
        "$MUSIC_API/browse",
        WEB_REMIX,
        buildJsonObject { put("continuation", token) },
    )
    suspend fun player(videoId: String): JsonObject {
        // Tanpa visitorData, banyak lagu dijawab LOGIN_REQUIRED.
        if (visitorData == null) {
            visitorLock.withLock {
                if (visitorData == null) post("$MUSIC_API/visitor_id", WEB_REMIX, JsonObject(emptyMap()))
            }
        }
        return post(
            "$YOUTUBE_API/player",
            VISIONOS,
            buildJsonObject {
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            },
        )
    }

    private suspend fun post(
        url: String,
        client: InnertubeClientInfo,
        extra: JsonObject,
        continuation: String? = null,
    ): JsonObject {
        val visitor = visitorData
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", client.name)
                    put("clientVersion", client.version)
                    client.device.forEach { (key, value) -> put(key, value) }
                    put("hl", hl)
                    put("gl", gl)
                    visitor?.let { put("visitorData", it) }
                }
            }
            extra.forEach { (key, value) -> put(key, value) }
        }

        val text = try {
            val response = http.post(url) {
                parameter("prettyPrint", "false")
                continuation?.let {
                    parameter("ctoken", it)
                    parameter("continuation", it)
                    parameter("type", "next")
                }
                header(HttpHeaders.UserAgent, client.userAgent)
                header("X-YouTube-Client-Name", client.id.toString())
                header("X-YouTube-Client-Version", client.version)
                client.webOrigin?.let {
                    header(HttpHeaders.Origin, it)
                    header(HttpHeaders.Referrer, "$it/")
                }
                visitor?.let { header("X-Goog-Visitor-Id", it) }
                setBody(TextContent(body.toString(), ContentType.Application.Json))
            }
            val status = response.status.value
            if (status == 429) throw CatalogException(Kind.RateLimited, "Innertube HTTP 429")
            if (!response.status.isSuccess()) throw CatalogException(Kind.Network, "Innertube HTTP $status")
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: CatalogException) {
            throw e
        } catch (e: Exception) {
            throw CatalogException(Kind.Network, "Innertube request failed: ${e.message}", e)
        }

        val json = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw CatalogException(Kind.Unreadable, "Innertube reply is not JSON", e)
        }
        // Lanjutan hasil pencarian hanya berjalan dengan visitorData yang sama, jadi simpan yang pertama.
        if (visitorData == null) {
            json.string("responseContext", "visitorData")?.let { visitorData = it }
        }
        return json
    }

    companion object {
        private const val MUSIC_API = "https://music.youtube.com/youtubei/v1"
        private const val YOUTUBE_API = "https://www.youtube.com/youtubei/v1"

        /** Filter tab "Lagu" di pencarian YouTube Music. */
        internal const val SONGS_FILTER = "EgWKAQIIAWoQEAMQBBAJEAoQBRAREBAQFA%3D%3D"

        /** Filter tab "Artis" di pencarian YouTube Music. */
        internal const val ARTISTS_FILTER = "EgWKAQIgAWoQEAMQBBAJEAoQBRAREBAQFQ%3D%3D"

        /** Filter tab "Album" di pencarian YouTube Music. */
        internal const val ALBUMS_FILTER = "EgWKAQIYAWoQEAMQBBAJEAoQBRAREBAQFQ%3D%3D"

        /** Filter tab "Video" di pencarian YouTube Music (cadangan pencocokan). */
        private const val VIDEOS_FILTER = "EgWKAQIQAWoQEAMQBBAJEAoQBRAREBAQFQ%3D%3D"

        val WEB_REMIX = InnertubeClientInfo(
            name = "WEB_REMIX",
            version = "1.20240101.01.00",
            id = 67,
            userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0 Safari/537.36",
            webOrigin = "https://music.youtube.com",
        )

        val VISIONOS = InnertubeClientInfo(
            name = "VISIONOS",
            version = "1.02",
            id = 101,
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/26.0 Safari/605.1.15",
            device = mapOf(
                "deviceMake" to "Apple",
                "deviceModel" to "RealityDevice17,1",
                "osName" to "visionOS",
                "osVersion" to "26.5.23O471",
            ),
        )
    }
}
