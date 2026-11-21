package senada.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import senada.model.Track

internal sealed interface LyricsDocument {
    data class Synced(val lines: List<LyricLine>) : LyricsDocument
    data class Plain(val lines: List<String>) : LyricsDocument
    data object Instrumental : LyricsDocument
    data object Unavailable : LyricsDocument
}

internal class LyricsException(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause)

internal class LyricsRepository(private val http: HttpClient) {
    private val cache = mutableMapOf<String, LyricsDocument>()
    private val cacheLock = Mutex()

    suspend fun clearCache() {
        cacheLock.withLock { cache.clear() }
    }

    suspend fun get(track: Track): LyricsDocument {
        if (track.source == "demo") return LyricsDocument.Unavailable
        return cacheLock.withLock {
            cache[track.id] ?: withContext(Dispatchers.Default) { fetch(track) }.also { cache[track.id] = it }
        }
    }

    private suspend fun fetch(track: Track): LyricsDocument {
        val original = fetchByTitle(track)
        if (original != LyricsDocument.Unavailable) return original

        val cleanedTitle = cleanTitle(track.title)
        return if (cleanedTitle == track.title) original else fetchByTitle(track.copy(title = cleanedTitle))
    }

    private suspend fun fetchByTitle(track: Track): LyricsDocument {
        if (track.artists.firstOrNull()?.isNotBlank() == true) {
            when (val exact = request("get", track)) {
                is JsonObject -> exact.document()?.let { return it }
                JsonNull -> Unit
                else -> throw LyricsException(UNREADABLE)
            }
        }
        val matches = when (val result = request("search", track)) {
            is JsonArray -> result
            JsonNull -> return LyricsDocument.Unavailable
            else -> throw LyricsException(UNREADABLE)
        }
        var fallback: LyricsDocument = LyricsDocument.Unavailable
        for (match in matches) {
            when (val doc = (match as? JsonObject)?.document()) {
                is LyricsDocument.Synced -> return doc
                is LyricsDocument.Plain -> if (fallback !is LyricsDocument.Plain) fallback = doc
                LyricsDocument.Instrumental -> if (fallback == LyricsDocument.Unavailable) fallback = doc
                else -> Unit
            }
        }
        return fallback
    }

    private suspend fun request(endpoint: String, track: Track): JsonElement {
        val body = try {
            val response = http.get("https://lrclib.net/api/$endpoint") {
                header(HttpHeaders.UserAgent, "Senada Mobile/0.1.0 (https://github.com/Zeeynrs/Senada-Mobile)")
                parameter("track_name", track.title)
                track.artists.firstOrNull()?.takeIf { it.isNotBlank() }?.let { parameter("artist_name", it) }
                if (endpoint == "get") {
                    val seconds = track.durationMs?.let { (it / 1000.0).roundToLong() }
                    if (seconds != null && seconds in 1L..3600L) parameter("duration", seconds)
                }
            }
            if (response.status == HttpStatusCode.NotFound) return JsonNull
            if (!response.status.isSuccess()) {
                throw LyricsException("LRCLIB gagal memuat lirik (HTTP ${response.status.value}).")
            }
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: LyricsException) {
            throw e
        } catch (e: Exception) {
            throw LyricsException("Tidak dapat terhubung ke LRCLIB. Periksa koneksi internet.", e)
        }
        if (body.isBlank()) return JsonNull
        return try {
            Json.parseToJsonElement(body)
        } catch (e: IllegalArgumentException) {
            throw LyricsException(UNREADABLE, e)
        }
    }
}

private const val UNREADABLE = "Balasan LRCLIB tidak dapat dibaca."
private const val TITLE_CREDIT = """(?:feat|ft)\.\s+[^\s()\[\]][^()\[\]]*"""
private const val TITLE_VERSION = """official (?:audio|(?:music )?video)|lyric video|remaster(?:ed)?(?:\s+[0-9]{4})?|live"""

private val titleSuffix = Regex(
    """(?:\(\s*(?:$TITLE_CREDIT|$TITLE_VERSION)\s*\)|\[\s*(?:$TITLE_CREDIT|$TITLE_VERSION)\s*\]""" +
        """|\s+-\s+(?:$TITLE_VERSION)|\s+$TITLE_CREDIT)\s*$""",
    RegexOption.IGNORE_CASE,
)

private fun cleanTitle(title: String): String {
    var cleaned = title
    while (true) {
        val suffix = titleSuffix.find(cleaned) ?: return cleaned
        cleaned = cleaned.substring(0, suffix.range.first).trimEnd()
        if (cleaned.isBlank()) return title
    }
}

private fun JsonObject.document(): LyricsDocument? {
    fun lyrics(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    lyrics("syncedLyrics")?.let { raw ->
        val lines = LrcParser.parse(raw)
        if (lines.any { it.text.isNotEmpty() }) return LyricsDocument.Synced(lines)
    }
    lyrics("plainLyrics")?.trim()?.takeIf { it.isNotEmpty() }?.let { return LyricsDocument.Plain(it.lines()) }
    return if ((this["instrumental"] as? JsonPrimitive)?.booleanOrNull == true) LyricsDocument.Instrumental else null
}
