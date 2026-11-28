package senada.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import senada.app.AppLanguage
import senada.app.AppStrings
import senada.app.UiText
import senada.local.LOCAL_FILE_SOURCE
import senada.lyrics.sources.LyricsCandidate
import senada.lyrics.sources.LyricsSource
import senada.model.Track
import senada.resources.Res
import senada.resources.error_lyrics_http
import senada.resources.error_lyrics_network
import senada.resources.error_lyrics_unreadable

internal sealed interface LyricsDocument {
    data class Synced(val lines: List<LyricLine>) : LyricsDocument
    data class Plain(val lines: List<String>, val romanizations: Map<Int, String> = emptyMap()) : LyricsDocument
    data object Instrumental : LyricsDocument
    data object Unavailable : LyricsDocument
}

internal class LyricsException(
    internal val uiText: UiText,
    cause: Throwable? = null,
) : Exception(uiText.resolve(), cause) {
    constructor(userMessage: String, cause: Throwable? = null) : this(UiText.Raw(userMessage), cause)

    fun userMessage(language: AppLanguage = AppStrings.currentLanguage): String = uiText.resolve(language)

    val userMessage: String
        get() = userMessage()
}

internal class LyricsRepository(
    private val http: HttpClient,
    /** Extra providers, best first. Word-timed lyrics from them are tried before LRCLIB. */
    private val wordSources: List<LyricsSource> = emptyList(),
) {
    private data class LyricsCacheKey(val trackId: String, val durationMs: Long?)

    private val cache = mutableMapOf<LyricsCacheKey, LyricsDocument>()
    private val cacheLock = Mutex()
    private val romanizationCache = mutableMapOf<String, List<LyricLine>>()
    private val romanizationLock = Mutex()

    suspend fun clearCache() {
        cacheLock.withLock { cache.clear() }
        romanizationLock.withLock { romanizationCache.clear() }
    }

    suspend fun get(track: Track): LyricsDocument {
        // File impor hanya punya nama file; mencari dengan judul saja mudah salah lagu.
        if (track.source == "demo" || track.source == LOCAL_FILE_SOURCE) return LyricsDocument.Unavailable
        val key = LyricsCacheKey(track.id, track.lyricsDurationMs)
        return cacheLock.withLock {
            cache[key] ?: withContext(Dispatchers.Default) { fetch(track) }.also { cache[key] = it }
        }
    }

    suspend fun withRomanization(track: Track, document: LyricsDocument): LyricsDocument {
        val needsSource = when (document) {
            is LyricsDocument.Synced -> document.lines.any {
                it.romanization == null && needsRomanizationFromSource(it.text)
            }
            is LyricsDocument.Plain -> document.lines.withIndex().any {
                it.index !in document.romanizations && needsRomanizationFromSource(it.value)
            }
            else -> false
        }
        if (!needsSource || track.artists.firstOrNull().isNullOrBlank()) return document
        return romanizationLock.withLock {
            // A downloaded document can differ from the current online reference, so
            // cache provider lines by track and match against the current document each time.
            val provider = romanizationCache[track.id]
                ?: fetchRomanizations(track).also {
                    if (it.isNotEmpty()) romanizationCache[track.id] = it
                }
            attachRomanizations(document, provider)
        }
    }

    private suspend fun fetchRomanizations(track: Track): List<LyricLine> = withContext(Dispatchers.Default) {
        withTimeoutOrNull(3_000) {
            try {
                val response = http.get("https://api.betterlyrics.org/getLyrics") {
                    parameter("s", track.title)
                    parameter("a", track.artists.first())
                    track.durationMs?.takeIf { it > 0 }?.let { parameter("d", (it / 1000.0).roundToLong()) }
                }
                if (!response.status.isSuccess()) return@withTimeoutOrNull emptyList()
                val json = Json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                val ttml = (json?.get("ttml") as? JsonPrimitive)?.takeIf { it.isString }?.content
                ttml?.let(TtmlParser::parse).orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        }.orEmpty()
    }

    private suspend fun fetch(track: Track): LyricsDocument {
        // LRCLIB is nearly always timed by line only. Karaoke needs word timing, so ask the other sources first.
        val sourced = searchSources(track)
        sourced.best(track, wordTimed = true)?.let { return it }
        val lrclib = try {
            fetchLrclib(track)
        } catch (error: LyricsException) {
            return sourced.best(track, wordTimed = false) ?: throw error
        }
        if (lrclib is LyricsDocument.Synced) return lrclib
        return sourced.best(track, wordTimed = false) ?: lrclib
    }

    private suspend fun searchSources(track: Track): List<LyricsCandidate> {
        if (wordSources.isEmpty() || track.artists.firstOrNull().isNullOrBlank()) return emptyList()
        return coroutineScope {
            wordSources.map { source ->
                async {
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        try {
                            source.search(track)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // One provider being down or refusing must never hide lyrics from the others.
                            emptyList()
                        }
                    }.orEmpty()
                }
            }.awaitAll().flatten()
        }
    }

    private suspend fun fetchLrclib(track: Track): LyricsDocument {
        val original = fetchByTitle(queryTrack = track, referenceTrack = track)
        if (original != LyricsDocument.Unavailable) return original

        val cleanedTitle = cleanTitle(track.title)
        return if (cleanedTitle == track.title) {
            original
        } else {
            fetchByTitle(queryTrack = track.copy(title = cleanedTitle), referenceTrack = track)
        }
    }

    private suspend fun fetchByTitle(queryTrack: Track, referenceTrack: Track): LyricsDocument {
        val exact = if (queryTrack.artists.firstOrNull().isNullOrBlank()) {
            null
        } else {
            when (val result = request("get", queryTrack)) {
                is JsonObject -> result.evaluateCandidate(referenceTrack)
                JsonNull -> null
                else -> throw LyricsException(UiText.Resource(Res.string.error_lyrics_unreadable))
            }
        }
        val exactPlainFallback = when (exact) {
            is CandidateEvaluation.CompatibleSynced -> return exact.document
            is CandidateEvaluation.Other -> {
                val document = exact.document
                if (document !is LyricsDocument.Plain || referenceTrack.lyricsDurationMs == null) return document
                document
            }
            else -> null
        }
        var plainFallback = exactPlainFallback ?: (exact as? CandidateEvaluation.IncompatibleSynced)?.plainFallback
        val matches = try {
            when (val result = request("search", queryTrack)) {
                is JsonArray -> result
                JsonNull -> return plainFallback ?: LyricsDocument.Unavailable
                else -> throw LyricsException(UiText.Resource(Res.string.error_lyrics_unreadable))
            }
        } catch (error: LyricsException) {
            return exactPlainFallback ?: throw error
        }
        var bestSynced: CandidateEvaluation.CompatibleSynced? = null
        var instrumentalFallback = false

        for (match in matches) {
            val obj = match as? JsonObject ?: continue
            when (val candidate = obj.evaluateCandidate(referenceTrack)) {
                is CandidateEvaluation.CompatibleSynced -> {
                    val current = bestSynced
                    if (current == null || compareSyncedCandidates(candidate, current) > 0) {
                        bestSynced = candidate
                    }
                }
                is CandidateEvaluation.IncompatibleSynced -> {
                    if (plainFallback == null) plainFallback = candidate.plainFallback
                }
                is CandidateEvaluation.Other -> when (val doc = candidate.document) {
                    is LyricsDocument.Plain -> if (plainFallback == null) plainFallback = doc
                    LyricsDocument.Instrumental -> instrumentalFallback = true
                    else -> Unit
                }
                null -> Unit
            }
        }
        return bestSynced?.document
            ?: plainFallback
            ?: if (instrumentalFallback) LyricsDocument.Instrumental else LyricsDocument.Unavailable
    }

    private suspend fun request(endpoint: String, track: Track): JsonElement {
        val body = try {
            val response = http.get("https://lrclib.net/api/$endpoint") {
                header(HttpHeaders.UserAgent, "Senada Mobile/1.0.2 (https://github.com/Zeeynrs/Senada-Mobile)")
                parameter("track_name", track.title)
                track.artists.firstOrNull()?.takeIf { it.isNotBlank() }?.let { parameter("artist_name", it) }
                if (endpoint == "get") {
                    val seconds = track.durationMs?.let { (it / 1000.0).roundToLong() }
                    if (seconds != null && seconds in 1L..3600L) parameter("duration", seconds)
                }
            }
            if (response.status == HttpStatusCode.NotFound) return JsonNull
            if (!response.status.isSuccess()) {
                throw LyricsException(UiText.Resource(Res.string.error_lyrics_http, response.status.value))
            }
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: LyricsException) {
            throw e
        } catch (e: Exception) {
            throw LyricsException(UiText.Resource(Res.string.error_lyrics_network), e)
        }
        if (body.isBlank()) return JsonNull
        return try {
            Json.parseToJsonElement(body)
        } catch (e: IllegalArgumentException) {
            throw LyricsException(UiText.Resource(Res.string.error_lyrics_unreadable), e)
        }
    }
}

/** The best of [this] (already in source order) that fits [track], as a synced document. */
private fun List<LyricsCandidate>.best(track: Track, wordTimed: Boolean): LyricsDocument.Synced? {
    var best: CandidateEvaluation.CompatibleSynced? = null
    for (candidate in this) {
        if (candidate.wordSynced != wordTimed || candidate.lines.none { it.text.isNotEmpty() }) continue
        val delta = track.lyricsDurationMs?.let { trackMs -> candidate.durationMs?.takeIf { it > 0L }?.let { abs(trackMs - it) } }
        if (!isTimingCompatible(track, candidate.title, delta, candidate.lines)) continue
        val evaluated = CandidateEvaluation.CompatibleSynced(
            LyricsDocument.Synced(candidate.lines),
            delta,
            scoreCandidate(track, candidate.title, candidate.artist, candidate.lines),
        )
        // Strictly better only: on a full tie the earlier, preferred source keeps its place.
        val current = best
        if (current == null || compareSyncedCandidates(evaluated, current) > 0) best = evaluated
    }
    return best?.document
}

private const val SOURCE_TIMEOUT_MS = 4_000L

private sealed interface CandidateEvaluation {
    data class CompatibleSynced(
        val document: LyricsDocument.Synced,
        val durationDeltaMs: Long?,
        val metadataScore: Int,
    ) : CandidateEvaluation
    data class IncompatibleSynced(val plainFallback: LyricsDocument.Plain?) : CandidateEvaluation
    data class Other(val document: LyricsDocument) : CandidateEvaluation
}

private const val PREFERRED_SYNCED_DURATION_DELTA_MS = 2_000L
private const val MAX_SYNCED_DURATION_DELTA_MS = 5_000L
private const val SYNCED_END_GRACE_MS = 3_000L
private const val LATEST_FIRST_LINE_FRACTION = 0.60

private fun compareSyncedCandidates(
    first: CandidateEvaluation.CompatibleSynced,
    second: CandidateEvaluation.CompatibleSynced,
): Int {
    fun durationBucket(deltaMs: Long?): Int = when {
        deltaMs == null -> 0
        deltaMs <= PREFERRED_SYNCED_DURATION_DELTA_MS -> 2
        else -> 1
    }

    val bucketOrder = durationBucket(first.durationDeltaMs).compareTo(durationBucket(second.durationDeltaMs))
    if (bucketOrder != 0) return bucketOrder

    val metadataOrder = first.metadataScore.compareTo(second.metadataScore)
    if (metadataOrder != 0) return metadataOrder

    return compareValues(second.durationDeltaMs, first.durationDeltaMs)
}

private val Track.lyricsDurationMs: Long?
    get() = durationMs?.takeIf { it > 0L }

private const val TITLE_CREDIT = """(?:feat|ft)\.\s+[^\s()\[\]][^()\[\]]*"""
private const val TITLE_VERSION = """official (?:audio|(?:music )?video)|lyric video|remaster(?:ed)?(?:\s+[0-9]{4})?|live"""

private val titleSuffix = Regex(
    """(?:\(\s*(?:$TITLE_CREDIT|$TITLE_VERSION)\s*\)|\[\s*(?:$TITLE_CREDIT|$TITLE_VERSION)\s*\]""" +
        """|\s+-\s+(?:$TITLE_VERSION)|\s+$TITLE_CREDIT)\s*$""",
    RegexOption.IGNORE_CASE,
)

private val TIMING_VERSION_REGEXES = mapOf(
    "live" to Regex("""\blive\b|\bkonser\b|\bconcert\b|\bunplugged\b""", RegexOption.IGNORE_CASE),
    "remix" to Regex("""\bremix(?:ed)?\b|\brmx\b|\bbootleg\b|\bmash ?up\b|\bvip mix\b""", RegexOption.IGNORE_CASE),
    "acoustic" to Regex("""\bacoustic\b|\bakustik\b|\bunplugged\b""", RegexOption.IGNORE_CASE),
    "cover" to Regex("""\bcover(?:ed)?\b|\btribute\b""", RegexOption.IGNORE_CASE),
    "karaoke" to Regex("""\bkaraoke\b|\bminus one\b|\bbacking track\b""", RegexOption.IGNORE_CASE),
    "slowed" to Regex("""\bslowed\b""", RegexOption.IGNORE_CASE),
    "sped" to Regex("""\bsped ?up\b|\bspeed ?up\b|\bnightcore\b""", RegexOption.IGNORE_CASE),
    "extended" to Regex("""\bextended\b""", RegexOption.IGNORE_CASE),
    "radio" to Regex("""\bradio edit\b""", RegexOption.IGNORE_CASE),
    "demo" to Regex("""\bdemo\b""", RegexOption.IGNORE_CASE),
)

// Kotlin/Native resolves the single-letter N alias as IsN, which its regex engine does not define.
private val NON_ALNUM = Regex("""[^\p{L}\p{Nd}\p{Nl}\p{No}]+""")

private fun recordingVersionTags(title: String): Set<String> = buildSet {
    for ((tag, regex) in TIMING_VERSION_REGEXES) {
        if (regex.containsMatchIn(title)) add(tag)
    }
}

private fun normalizeMatchText(text: String): String =
    NON_ALNUM.replace(text.lowercase(), " ").trim()

private fun cleanTitle(title: String): String {
    var cleaned = title
    while (true) {
        val suffix = titleSuffix.find(cleaned) ?: return cleaned
        cleaned = cleaned.substring(0, suffix.range.first).trimEnd()
        if (cleaned.isBlank()) return title
    }
}

private fun JsonObject.evaluateCandidate(track: Track): CandidateEvaluation? {
    fun str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val candidateTitle = str("trackName")
    val candidateArtist = str("artistName")
    val candidateDurationMs = (this["duration"] as? JsonPrimitive)?.doubleOrNull
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.let { (it * 1_000.0).roundToLong() }
        ?.takeIf { it > 0L }
    val trackDurationMs = track.lyricsDurationMs
    val durationDeltaMs = if (trackDurationMs != null && candidateDurationMs != null) {
        abs(trackDurationMs - candidateDurationMs)
    } else null
    val plainDoc = str("plainLyrics")?.trim()?.takeIf { it.isNotEmpty() }?.let { LyricsDocument.Plain(it.lines()) }

    str("syncedLyrics")?.let { raw ->
        val lines = LrcParser.parse(raw)
        if (lines.any { it.text.isNotEmpty() }) {
            val compatible = isTimingCompatible(track, candidateTitle, durationDeltaMs, lines)
            if (!compatible) {
                val fallback = plainDoc ?: lines.filter { it.text.isNotEmpty() }
                    .takeIf { it.isNotEmpty() }
                    ?.let { nonEmpty ->
                        LyricsDocument.Plain(
                            nonEmpty.map { it.text },
                            nonEmpty.mapIndexedNotNull { index, line ->
                                line.romanization?.let { index to it }
                            }.toMap(),
                        )
                    }
                return CandidateEvaluation.IncompatibleSynced(fallback)
            }
            return CandidateEvaluation.CompatibleSynced(
                LyricsDocument.Synced(lines),
                durationDeltaMs,
                scoreCandidate(track, candidateTitle, candidateArtist, lines),
            )
        }
    }
    plainDoc?.let { return CandidateEvaluation.Other(it) }
    return if ((this["instrumental"] as? JsonPrimitive)?.booleanOrNull == true) {
        CandidateEvaluation.Other(LyricsDocument.Instrumental)
    } else {
        null
    }
}

private fun attachRomanizations(
    document: LyricsDocument,
    provider: List<LyricLine>,
): LyricsDocument {
    fun key(text: String): String = text.filterNot { it.isWhitespace() }.lowercase()

    val byOriginal = provider.filter { it.romanization != null }
        .groupBy({ key(it.text) }, { it.romanization })
        .mapValues { (_, values) -> values.distinct().singleOrNull() }
    if (byOriginal.isEmpty()) return document
    return when (document) {
        is LyricsDocument.Synced -> document.copy(lines = document.lines.map { line ->
            if (line.romanization != null || !needsRomanizationFromSource(line.text)) line
            else line.copy(romanization = byOriginal[key(line.text)])
        })
        is LyricsDocument.Plain -> document.copy(romanizations = buildMap {
            putAll(document.romanizations)
            document.lines.forEachIndexed { index, text ->
                if (index !in this && needsRomanizationFromSource(text)) {
                    byOriginal[key(text)]?.let { put(index, it) }
                }
            }
        })
        else -> document
    }
}

private fun isTimingCompatible(
    track: Track,
    candidateTitle: String?,
    durationDeltaMs: Long?,
    lines: List<LyricLine>,
): Boolean {
    if (candidateTitle != null) {
        val trackVersions = recordingVersionTags(track.title)
        val candidateVersions = recordingVersionTags(candidateTitle)
        if (trackVersions != candidateVersions) return false
    }
    val trackDuration = track.lyricsDurationMs ?: return true
    if (durationDeltaMs != null && durationDeltaMs > MAX_SYNCED_DURATION_DELTA_MS) return false
    val nonEmpty = lines.filter { it.text.isNotEmpty() }
    if (nonEmpty.isNotEmpty()) {
        if (nonEmpty.first().startMs > trackDuration * LATEST_FIRST_LINE_FRACTION) return false
        if (nonEmpty.any { it.startMs - trackDuration > SYNCED_END_GRACE_MS }) return false
    }
    return true
}

private fun scoreCandidate(
    track: Track,
    candidateTitle: String?,
    candidateArtist: String?,
    lines: List<LyricLine>,
): Int {
    var score = 0
    if (lines.any { it.words.isNotEmpty() }) score += 20
    if (candidateTitle != null) {
        if (recordingVersionTags(track.title) == recordingVersionTags(candidateTitle)) score += 40
        if (normalizeMatchText(cleanTitle(track.title)) == normalizeMatchText(cleanTitle(candidateTitle))) score += 20
    }
    val primaryArtist = track.artists.firstOrNull()?.let(::normalizeMatchText)?.takeIf { it.isNotEmpty() }
    val normalizedCandidateArtist = candidateArtist?.let(::normalizeMatchText)?.takeIf { it.isNotEmpty() }
    if (primaryArtist != null && normalizedCandidateArtist != null) {
        score += when {
            normalizedCandidateArtist == primaryArtist -> 30
            primaryArtist in normalizedCandidateArtist || normalizedCandidateArtist in primaryArtist -> 15
            else -> -25
        }
    }
    return score
}
