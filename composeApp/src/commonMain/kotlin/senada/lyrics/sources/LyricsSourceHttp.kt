package senada.lyrics.sources

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import senada.lyrics.LyricLine

private const val LYRICS_SOURCE_USER_AGENT = "Senada Mobile/1.0.2 (https://github.com/Zeeynrs/Senada-Mobile)"

internal suspend fun <T> HttpClient.getLyricsJson(
    sourceId: String,
    endpoint: String,
    query: Map<String, Any?> = emptyMap(),
    read: (JsonObject) -> T?,
): T? {
    try {
        val response = get(endpoint) {
            expectSuccess = false
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.UserAgent, LYRICS_SOURCE_USER_AGENT)
            timeout {
                requestTimeoutMillis = 8_000
                connectTimeoutMillis = 3_000
                socketTimeoutMillis = 8_000
            }
            query.forEach { (name, value) -> parameter(name, value) }
        }
        if (response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.NoContent) return null
        if (!response.status.isSuccess()) {
            throw LyricsSourceException(sourceId, "Lyrics request returned HTTP ${response.status.value}")
        }
        val body = response.bodyAsText().takeIf { it.isNotBlank() } ?: return null
        val json = Json.parseToJsonElement(body)
        return if (json == JsonNull) null else read(json as JsonObject)
    } catch (e: CancellationException) {
        throw e
    } catch (e: LyricsSourceException) {
        throw e
    } catch (e: Exception) {
        throw LyricsSourceException(sourceId, "Could not fetch or read lyrics", e)
    }
}

internal suspend fun <T> HttpClient.getLyricsJsonFromMirrors(
    sourceId: String,
    endpoints: List<String>,
    query: Map<String, Any?>,
    read: (JsonObject) -> T?,
): T? {
    var failure: LyricsSourceException? = null
    for (endpoint in endpoints) {
        val result = try {
            getLyricsJson(sourceId, endpoint, query, read)
        } catch (e: LyricsSourceException) {
            if (failure == null) failure = e
            null
        }
        if (result != null) return result
    }
    failure?.let { throw it }
    return null
}

internal fun JsonObject.lyricsString(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.lyricsLong(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.lyricsDouble(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

internal fun JsonObject.lyricsFlag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

internal fun JsonObject.lyricsObject(key: String): JsonObject? = (this[key].takeUnless { it == JsonNull }) as JsonObject?

internal fun JsonObject.lyricsArray(key: String): List<JsonObject> =
    (this[key].takeUnless { it == JsonNull } as JsonArray?)?.map { it as JsonObject }.orEmpty()

internal fun List<LyricLine>.hasWordTiming(): Boolean {
    val vocals = mapNotNull { line ->
        line.takeIf { it.text.isNotBlank() } ?: line.background?.takeIf { it.text.isNotBlank() }
    }
    return vocals.count { line -> line.words.any { it.durationMs > 0 && it.text.isNotBlank() } } > vocals.size / 2
}
