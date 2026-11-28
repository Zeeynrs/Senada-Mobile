// Portions adapted from Mochy Music (https://github.com/realidkroo/Mochy-Music, commit 3071023), GPL-3.0.
package senada.lyrics.sources

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlin.io.encoding.Base64
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import senada.lyrics.LyricLine
import senada.lyrics.LyricWord
import senada.lyrics.stripBackingBrackets
import senada.model.Track

internal const val CN_LYRICS_USER_AGENT = "Senada Mobile/1.0.2 (https://github.com/Zeeynrs/Senada-Mobile)"
internal const val CN_SOURCE_CONNECT_TIMEOUT_MS = 5_000L
internal const val CN_SOURCE_REQUEST_TIMEOUT_MS = 10_000L
internal const val CN_MAX_LYRIC_FETCH_ATTEMPTS = 3

private val LenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private val LenientBase64 = Base64.Mime.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

internal fun HttpRequestBuilder.applyCnSourceDefaults(http: HttpClient) {
    header(HttpHeaders.UserAgent, CN_LYRICS_USER_AGENT)
    if (http.pluginOrNull(HttpTimeout) != null) {
        timeout {
            connectTimeoutMillis = CN_SOURCE_CONNECT_TIMEOUT_MS
            requestTimeoutMillis = CN_SOURCE_REQUEST_TIMEOUT_MS
        }
    }
}

internal suspend inline fun <T> withCnSourceRequest(
    sourceId: String,
    timeoutMs: Long = CN_SOURCE_REQUEST_TIMEOUT_MS,
    crossinline block: suspend () -> T,
): T = try {
    withContext(Dispatchers.Default) {
        withTimeout(timeoutMs) { block() }
    }
} catch (e: TimeoutCancellationException) {
    throw LyricsSourceException(sourceId, "Request timed out", e)
} catch (e: CancellationException) {
    throw e
} catch (e: LyricsSourceException) {
    throw e
} catch (e: Exception) {
    throw LyricsSourceException(sourceId, "Network request failed: ${e.message ?: e::class.simpleName}", e)
}

internal fun parseCnJsonObject(sourceId: String, body: String): JsonObject {
    if (body.isBlank()) {
        throw LyricsSourceException(sourceId, "Empty JSON response")
    }
    val element = try {
        LenientJson.parseToJsonElement(body)
    } catch (e: Exception) {
        throw LyricsSourceException(sourceId, "Malformed JSON response", e)
    }
    return element as? JsonObject
        ?: throw LyricsSourceException(sourceId, "Expected JSON object response")
}

internal fun decodeBase64OrNull(encoded: String): ByteArray? {
    val trimmed = encoded.trim()
    if (trimmed.isEmpty()) return null
    return runCatching { LenientBase64.decode(trimmed) }.getOrNull()
}

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.strOrNumber(key: String): String? =
    (this[key] as? JsonPrimitive)?.content

internal fun JsonObject.longVal(key: String): Long? =
    (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toLongOrNull() }

internal fun JsonObject.intVal(key: String): Int? =
    (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.content.toIntOrNull() }

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonElement.asJsonObjectOrNull(): JsonObject? = this as? JsonObject

internal data class CnSearchHit<T>(
    val titles: List<String>,
    val artists: List<String>,
    val durationMs: Long?,
    val ref: T,
)

private val DECORATIONS_REGEX = Regex("""\s*[(（\[【「『《].*?[)）\]】」』》]""")
private val WHITESPACE_RUN = Regex("""\s+""")

private val VERSION_TAG_REGEXES = mapOf(
    "live" to Regex("""\blive\b|\bkonser\b|\bconcert\b|\bunplugged\b""", RegexOption.IGNORE_CASE),
    "remix" to Regex("""\bremix(?:ed)?\b|\brmx\b|\bbootleg\b|\bmash ?up\b""", RegexOption.IGNORE_CASE),
    "acoustic" to Regex("""\bacoustic\b|\bakustik\b|\bunplugged\b""", RegexOption.IGNORE_CASE),
    "cover" to Regex("""\bcover(?:ed)?\b|\btribute\b""", RegexOption.IGNORE_CASE),
    "karaoke" to Regex("""\bkaraoke\b|\bminus one\b|\bbacking track\b""", RegexOption.IGNORE_CASE),
    "slowed" to Regex("""\bslowed\b""", RegexOption.IGNORE_CASE),
    "sped" to Regex("""\bsped ?up\b|\bspeed ?up\b|\bnightcore\b""", RegexOption.IGNORE_CASE),
    "demo" to Regex("""\bdemo\b""", RegexOption.IGNORE_CASE),
)

private fun versionTags(text: String): Set<String> = buildSet {
    for ((tag, regex) in VERSION_TAG_REGEXES) {
        if (regex.containsMatchIn(text)) add(tag)
    }
}

internal fun stripTitleDecorations(text: String): String {
    val stripped = text.replace(DECORATIONS_REGEX, "").trim()
    return stripped.ifEmpty { text.trim() }
}

private fun normalizeAlnum(text: String): String = buildString(text.length) {
    for (ch in stripTitleDecorations(text).lowercase()) {
        if (ch.isLetterOrDigit()) append(ch)
    }
}

private fun normalizeRawAlnum(text: String): String = buildString(text.length) {
    for (ch in text.lowercase()) {
        if (ch.isLetterOrDigit()) append(ch)
    }
}

private fun normalizeWords(text: String): List<String> = buildList {
    val current = StringBuilder()
    for (ch in stripTitleDecorations(text).lowercase()) {
        if (ch.isLetterOrDigit()) {
            current.append(ch)
        } else if (current.isNotEmpty()) {
            add(current.toString())
            current.clear()
        }
    }
    if (current.isNotEmpty()) add(current.toString())
}

/**
 * Ranks search results for [track] best-first and drops clearly unrelated hits.
 */
internal fun <T> rankCnSearchHits(track: Track, hits: List<CnSearchHit<T>>): List<CnSearchHit<T>> {
    if (hits.isEmpty()) return emptyList()
    val wantedTitle = normalizeAlnum(track.title).ifEmpty { normalizeRawAlnum(track.title) }
    if (wantedTitle.isEmpty()) return emptyList()
    val wantedWords = normalizeWords(track.title).toSet()
    val wantedArtists = track.artists.mapNotNull { normalizeAlnum(it).ifEmpty { null } }
    val wantedVersions = versionTags(track.title)
    val trackDurationMs = track.durationMs?.takeIf { it > 0L }
    val maxDurationDiffMs = trackDurationMs?.let { maxOf(15_000L, (it * 0.12).roundToLong()) }

    return hits.mapIndexedNotNull { index, hit ->
        val titleScore = hit.titles
            .map { normalizeAlnum(it).ifEmpty { normalizeRawAlnum(it) } }
            .filter { it.isNotEmpty() }
            .maxOfOrNull { candidate ->
                when {
                    candidate == wantedTitle -> 40
                    candidate.length >= 2 && wantedTitle.length >= 2 &&
                        (candidate.contains(wantedTitle) || wantedTitle.contains(candidate)) -> 25
                    wantedWords.size >= 2 -> {
                        val candidateWords = hit.titles.flatMap(::normalizeWords).toSet()
                        val overlap = wantedWords.count { it in candidateWords }
                        if (overlap * 2 >= wantedWords.size) 15 else 0
                    }
                    else -> 0
                }
            } ?: 0
        if (titleScore == 0) return@mapIndexedNotNull null

        val hitDurationMs = hit.durationMs?.takeIf { it > 0L }
        var durationScore = 0
        if (trackDurationMs != null && hitDurationMs != null && maxDurationDiffMs != null) {
            val diffMs = abs(hitDurationMs - trackDurationMs)
            if (diffMs > maxDurationDiffMs) return@mapIndexedNotNull null
            durationScore = when {
                diffMs <= 3_000L -> 25
                diffMs <= 8_000L -> 15
                else -> 5
            } - (diffMs / 2_000L).toInt()
        }

        val candidateArtists = hit.artists.mapNotNull { normalizeAlnum(it).ifEmpty { null } }
        val artistScore = if (wantedArtists.isEmpty() || candidateArtists.isEmpty()) {
            0
        } else {
            val exact = wantedArtists.any { wanted -> candidateArtists.any { it == wanted } }
            val partial = !exact && wantedArtists.any { wanted ->
                candidateArtists.any { it.contains(wanted) || wanted.contains(it) }
            }
            when {
                exact -> 30
                partial -> 20
                else -> -10
            }
        }

        val candidatePrimaryTitle = hit.titles.firstOrNull().orEmpty()
        val versionScore = if (versionTags(candidatePrimaryTitle) == wantedVersions) 15 else -20
        val totalScore = titleScore + artistScore + durationScore + versionScore
        Triple(hit, totalScore, index)
    }.sortedWith(
        compareByDescending<Triple<CnSearchHit<T>, Int, Int>> { it.second }
            .thenBy { it.third },
    ).map { it.first }
}

/** True when at least half of non-blank lines carry per-word timing. */
internal fun hasWordSync(lines: List<LyricLine>): Boolean {
    val sung = lines.filter { it.text.isNotBlank() }
    if (sung.isEmpty()) return false
    val timed = sung.count { it.words.isNotEmpty() }
    return timed * 2 >= sung.size
}

private val CREDIT_REGEX = Regex("""^[^:：]{1,24}[:：].+$""")
private const val HEAD_CREDIT_WINDOW = 15
private const val TAIL_CREDIT_WINDOW = 6
private const val INSTRUMENTAL_MARKER = "纯音乐"

private val DUET_SPEAKER_REGEX = Regex("""^(男|女|合唱|合)\s*[:：]\s*(.+)$""")

private fun duetAgentForLabel(label: String): String? = when (label.trim()) {
    "男" -> "v1"
    "女" -> "v2"
    "合", "合唱" -> "v1000"
    else -> null
}

private fun LyricLine.isCreditLine(): Boolean {
    val trimmed = text.trim()
    if (!CREDIT_REGEX.matches(trimmed)) return false
    val label = trimmed.substringBefore(':').substringBefore('：').trim()
    if (duetAgentForLabel(label) != null) return false
    return label.split(' ').count { it.isNotEmpty() } <= 4
}

/**
 * Strips leading/trailing credit lines inserted by Chinese lyrics services while
 * preserving duet speaker prefixes (`男：`, `女：`, `合：`).
 */
internal fun List<LyricLine>.withoutCnCredits(): List<LyricLine> {
    if (isEmpty()) return emptyList()
    val headEnd = take(HEAD_CREDIT_WINDOW).indexOfLast { it.isCreditLine() }
    val body = if (headEnd >= 0) drop(headEnd + 1) else this
    val tailStart = (body.size - TAIL_CREDIT_WINDOW).coerceAtLeast(0)
    val tailCut = body.subList(tailStart, body.size).indexOfFirst { it.isCreditLine() }
    return if (tailCut == -1) body else body.subList(0, tailStart + tailCut)
}

/** True when there are real sung lyrics rather than empty lines or an instrumental notice. */
internal fun List<LyricLine>.hasSungLyrics(): Boolean {
    val sung = filter { it.text.isNotBlank() }
    return sung.isNotEmpty() && sung.none { INSTRUMENTAL_MARKER in it.text }
}

/** Extracts CJK duet speaker prefixes (`男：`, `女：`, `合：`) on LRC-parsed lines. */
internal fun List<LyricLine>.withCnDuetAgents(): List<LyricLine> = map { line ->
    if (line.agent != null || line.text.isBlank()) {
        line
    } else {
        val (strippedText, strippedWords, duetAgent) = extractDuetPrefix(line.text, line.words)
        if (duetAgent == null) {
            line
        } else {
            line.copy(text = strippedText, words = strippedWords, agent = duetAgent)
        }
    }
}

private val OFFSET_TAG_REGEX = Regex("""\[offset:\s*([+-]?[0-9]+)\s*]""", RegexOption.IGNORE_CASE)

internal fun parseOffsetTagMs(text: String): Long =
    OFFSET_TAG_REGEX.findAll(text)
        .mapNotNull { it.groupValues[1].toLongOrNull() }
        .lastOrNull() ?: 0L

internal fun applyLyricOffset(startMs: Long, offsetMs: Long): Long =
    if (offsetMs < 0L && startMs > Long.MAX_VALUE + offsetMs) {
        Long.MAX_VALUE
    } else {
        (startMs - offsetMs).coerceAtLeast(0L)
    }

/**
 * Normalizes a word-timed lyric line so:
 * - leading/trailing whitespace is trimmed cleanly,
 * - whitespace-only tokens attach a space to the preceding word instead of producing empty tokens,
 * - every word's `text.trim()` appears in order inside `line.text`,
 * - duet speaker prefixes (`男：`, `女：`, `合：`) map to `agent` (`v1`, `v2`, `v1000`),
 * - inline parenthetical/bracketed backing vocals split into `background`.
 */
internal fun buildTimedLyricLine(
    startMs: Long,
    durationMs: Long,
    rawWords: List<LyricWord>,
    fallbackText: String = "",
): LyricLine? {
    val cleanedWords = normalizeWords(rawWords)
    val baseText = if (cleanedWords.isNotEmpty()) {
        cleanedWords.joinToString("") { it.text }.trim()
    } else {
        fallbackText.trim()
    }
    if (baseText.isEmpty()) return null

    val (strippedText, strippedWords, duetAgent) = extractDuetPrefix(baseText, cleanedWords)
    if (strippedText.isEmpty()) return null

    val lastWordEnd = strippedWords.lastOrNull()?.endMs
    val computedEnd = when {
        durationMs > 0L -> maxOf(startMs + durationMs, lastWordEnd ?: startMs)
        else -> lastWordEnd
    }
    val line = LyricLine(
        startMs = startMs,
        text = strippedText,
        words = strippedWords,
        endMs = computedEnd,
        agent = duetAgent,
    )
    return splitInlineBacking(line)
}

private fun normalizeWords(rawWords: List<LyricWord>): List<LyricWord> {
    if (rawWords.isEmpty()) return emptyList()
    val out = ArrayList<LyricWord>(rawWords.size)
    for (word in rawWords) {
        val dur = word.durationMs.coerceAtLeast(0L)
        if (word.text.isBlank()) {
            if (word.text.isNotEmpty() && out.isNotEmpty() && !out.last().text.endsWith(' ')) {
                val prev = out.last()
                out[out.lastIndex] = prev.copy(text = prev.text + " ")
            }
            continue
        }
        val text = if (out.isEmpty()) word.text.trimStart() else word.text
        out += LyricWord(word.startMs, dur, text)
    }
    if (out.isNotEmpty()) {
        val last = out.last()
        val trimmed = last.text.trimEnd()
        if (trimmed.isEmpty()) {
            out.removeAt(out.lastIndex)
        } else if (trimmed != last.text) {
            out[out.lastIndex] = last.copy(text = trimmed)
        }
    }
    return out
}

private fun extractDuetPrefix(
    text: String,
    words: List<LyricWord>,
): Triple<String, List<LyricWord>, String?> {
    val match = DUET_SPEAKER_REGEX.matchEntire(text) ?: return Triple(text, words, null)
    val agent = duetAgentForLabel(match.groupValues[1]) ?: return Triple(text, words, null)
    val body = match.groupValues[2].trim()
    if (body.isEmpty()) return Triple(text, words, null)
    if (words.isEmpty()) return Triple(body, emptyList(), agent)

    val prefixLength = text.length - match.groupValues[2].length
    var remainingToStrip = prefixLength
    val adjusted = ArrayList<LyricWord>(words.size)
    for (word in words) {
        if (remainingToStrip <= 0) {
            adjusted += word
            continue
        }
        if (word.text.length <= remainingToStrip) {
            remainingToStrip -= word.text.length
        } else {
            val remainder = word.text.substring(remainingToStrip)
            remainingToStrip = 0
            if (remainder.isNotBlank()) {
                adjusted += word.copy(text = remainder.trimStart())
            }
        }
    }
    val normalized = normalizeWords(adjusted)
    val joined = normalized.joinToString("") { it.text }.trim()
    return Triple(joined.ifEmpty { body }, normalized, agent)
}

private fun Char.isBracketChar(): Boolean = this == '(' || this == ')' || this == '[' || this == ']'

private fun splitInlineBacking(line: LyricLine): LyricLine {
    if (line.background != null) return line
    val text = line.text
    if ('(' !in text && '[' !in text) return line

    val lead = StringBuilder(text.length)
    val backing = StringBuilder()
    val inBracket = BooleanArray(text.length)
    val openBrackets = ArrayDeque<Char>()

    for (index in text.indices) {
        val ch = text[index]
        when (ch) {
            '(', '[' -> {
                openBrackets.addLast(ch)
                inBracket[index] = true
            }
            ')', ']' -> {
                val expected = if (ch == ')') '(' else '['
                if (openBrackets.lastOrNull() != expected) return line
                openBrackets.removeLast()
                inBracket[index] = true
                if (openBrackets.isEmpty()) backing.append(' ')
            }
            else -> {
                if (openBrackets.isNotEmpty()) {
                    inBracket[index] = true
                    backing.append(ch)
                } else {
                    lead.append(ch)
                }
            }
        }
    }
    if (openBrackets.isNotEmpty()) return line

    val leadText = WHITESPACE_RUN.replace(lead.toString().trim(), " ")
    val backingText = WHITESPACE_RUN.replace(backing.toString().trim(), " ")
    if (leadText.isEmpty() || backingText.isEmpty()) return line

    val leadWords = mutableListOf<LyricWord>()
    val backingWords = mutableListOf<LyricWord>()
    var searchFrom = 0
    for (word in line.words) {
        val trimmed = word.text.trim()
        if (trimmed.isEmpty()) continue
        val start = text.indexOf(trimmed, startIndex = searchFrom)
        if (start < 0) continue
        val end = start + trimmed.length
        searchFrom = end

        var hasLeadChar = false
        var hasBackingChar = false
        for (i in start until end) {
            if (!inBracket[i]) {
                hasLeadChar = true
            } else if (!text[i].isBracketChar()) {
                hasBackingChar = true
            }
        }
        when {
            hasLeadChar && !hasBackingChar -> leadWords += word
            hasBackingChar && !hasLeadChar -> backingWords += word
            hasLeadChar && hasBackingChar -> {
                val leadPart = buildString {
                    for (i in start until end) if (!inBracket[i]) append(text[i])
                }
                val backingPart = buildString {
                    for (i in start until end) if (inBracket[i] && !text[i].isBracketChar()) append(text[i])
                }
                if (leadPart.isNotBlank()) leadWords += word.copy(text = leadPart)
                if (backingPart.isNotBlank()) backingWords += word.copy(text = backingPart)
            }
        }
    }

    val cleanedLeadWords = stripBackingBrackets(leadWords)
    val cleanedBackingWords = stripBackingBrackets(backingWords)
    val bgStartMs = cleanedBackingWords.firstOrNull()?.startMs ?: line.startMs
    val bgEndMs = cleanedBackingWords.lastOrNull()?.endMs ?: line.endMs
    return line.copy(
        text = leadText,
        words = cleanedLeadWords,
        background = LyricLine(
            startMs = bgStartMs,
            text = backingText,
            words = cleanedBackingWords,
            endMs = bgEndMs,
        ),
    )
}
