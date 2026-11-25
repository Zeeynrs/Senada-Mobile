package senada.lyrics

import kotlin.math.roundToLong

internal data class LyricWord(
    val startMs: Long,
    val durationMs: Long,
    val text: String,
) {
    val endMs: Long get() = startMs + durationMs.coerceAtLeast(0L)
}

internal data class LyricLine(
    val startMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val endMs: Long? = null,
    val romanization: String? = null,
)

internal object LrcParser {
    private val lineTimestamp = Regex("""\[([0-9]+):([0-5]?[0-9])(?:[.:]([0-9]+))?\]""")
    private val wordTimestamp = Regex("""<([0-9]+):([0-5]?[0-9])(?:[.:]([0-9]+))?>""")
    private val metadata = Regex("""\[[a-zA-Z#]+:[^\]]*\]""")
    private val offset = Regex("""\[offset:\s*([+-]?[0-9]+)\s*\]""", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<LyricLine> {
        val trimmed = text.trimStart()
        if (trimmed.startsWith("<tt", ignoreCase = true) ||
            (trimmed.startsWith("<?xml", ignoreCase = true) && trimmed.contains("<tt", ignoreCase = true))
        ) {
            return TtmlParser.parse(text)
        }
        return parseLrc(text)
    }

    private fun parseLrc(text: String): List<LyricLine> {
        val offsetMs = offset.findAll(text)
            .mapNotNull { it.groupValues[1].toLongOrNull() }
            .lastOrNull() ?: 0L

        data class RawParsedLine(
            val startMs: Long,
            val text: String,
            val words: List<LyricWord>,
            val endMs: Long?,
            val hasOpenLastWord: Boolean,
        )

        val rawLines = buildList {
            for (raw in text.lineSequence()) {
                val line = metadata.replace(raw, "")
                val stamps = lineTimestamp.findAll(line).mapNotNull { match ->
                    parseTimestampMs(match.groupValues[1], match.groupValues[2], match.groupValues[3], offsetMs)
                }.toList()
                if (stamps.isEmpty()) continue

                val body = lineTimestamp.replace(line, "").trim()
                val wordTags = wordTimestamp.findAll(body).toList()
                if (wordTags.isEmpty()) {
                    for (startMs in stamps) {
                        add(RawParsedLine(startMs, body, emptyList(), null, hasOpenLastWord = false))
                    }
                    continue
                }

                val baseLineStart = stamps.first()
                val rawWords = buildRawWords(body, wordTags, baseLineStart, offsetMs)
                val lineText = rawWords.words.joinToString("") { it.text }.trim()
                for (stampMs in stamps) {
                    val shift = stampMs - baseLineStart
                    val shiftedWords = rawWords.words.map { w ->
                        w.copy(startMs = shiftTimestamp(w.startMs, shift))
                    }
                    val shiftedEnd = rawWords.explicitEndMs?.let { shiftTimestamp(it, shift) }
                    add(
                        RawParsedLine(
                            startMs = stampMs,
                            text = lineText,
                            words = shiftedWords,
                            endMs = shiftedEnd,
                            hasOpenLastWord = rawWords.hasOpenLastWord,
                        ),
                    )
                }
            }
        }.sortedBy { it.startMs }

        return rawLines.mapIndexed { index, current ->
            if (current.words.isEmpty() || !current.hasOpenLastWord) {
                LyricLine(current.startMs, current.text, current.words, current.endMs)
            } else {
                val nextStart = rawLines.drop(index + 1).firstOrNull { it.startMs > current.startMs }?.startMs
                val finalizedWords = finalizeOpenLastWord(current.words, nextStart)
                val resolvedEnd = current.endMs ?: finalizedWords.lastOrNull()?.endMs
                LyricLine(current.startMs, current.text, finalizedWords, resolvedEnd)
            }
        }
    }

    private data class RawWordResult(
        val words: List<LyricWord>,
        val explicitEndMs: Long?,
        val hasOpenLastWord: Boolean,
    )

    private fun buildRawWords(
        body: String,
        wordTags: List<MatchResult>,
        baseLineStart: Long,
        offsetMs: Long,
    ): RawWordResult {
        val segments = mutableListOf<Pair<Long, String>>()
        var cursor = 0
        var activeWordStart = baseLineStart
        var trailingStamp: Long? = null

        val leading = body.substring(0, wordTags.first().range.first)
        if (leading.isNotEmpty()) {
            segments += activeWordStart to leading
        }

        for (i in wordTags.indices) {
            val tag = wordTags[i]
            val parsedTime = parseTimestampMs(tag.groupValues[1], tag.groupValues[2], tag.groupValues[3], offsetMs)
            if (parsedTime != null) {
                activeWordStart = parsedTime
            }
            val textStart = tag.range.last + 1
            val textEnd = wordTags.getOrNull(i + 1)?.range?.first ?: body.length
            val segment = body.substring(textStart, textEnd)
            if (segment.isNotEmpty()) {
                segments += activeWordStart to segment
                trailingStamp = null
            } else if (i == wordTags.lastIndex && parsedTime != null) {
                trailingStamp = parsedTime
            }
            cursor = textEnd
        }
        if (cursor < body.length) {
            val rest = body.substring(cursor)
            if (rest.isNotEmpty()) segments += activeWordStart to rest
        }

        if (segments.isEmpty()) {
            return RawWordResult(emptyList(), trailingStamp, hasOpenLastWord = false)
        }

        val words = mutableListOf<LyricWord>()
        for (i in segments.indices) {
            val (start, segText) = segments[i]
            val trimmedText = if (i == segments.lastIndex) segText.trimEnd() else segText
            if (trimmedText.isEmpty()) continue
            val nextTime = when {
                i + 1 < segments.size -> segments[i + 1].first
                trailingStamp != null -> trailingStamp
                else -> null
            }
            val duration = nextTime?.let { (it - start).coerceAtLeast(0L) } ?: -1L
            words += LyricWord(start, duration, trimmedText)
        }
        if (words.isNotEmpty()) {
            val last = words.last()
            val cleanedLast = last.text.trimEnd()
            if (cleanedLast.isEmpty()) {
                words.removeAt(words.lastIndex)
            } else if (cleanedLast != last.text) {
                words[words.lastIndex] = last.copy(text = cleanedLast)
            }
        }
        val hasOpenLast = words.lastOrNull()?.durationMs == -1L
        return RawWordResult(words, trailingStamp, hasOpenLastWord = hasOpenLast)
    }

    private fun finalizeOpenLastWord(words: List<LyricWord>, nextLineStartMs: Long?): List<LyricWord> {
        if (words.isEmpty()) return words
        val last = words.last()
        if (last.durationMs >= 0L) return words
        val timedSamples = words.dropLast(1).filter { it.durationMs > 0L && it.text.trim().isNotEmpty() }
        val avgMsPerChar = if (timedSamples.isNotEmpty()) {
            timedSamples.sumOf { it.durationMs }.toDouble() / timedSamples.sumOf { it.text.trim().length }.coerceAtLeast(1)
        } else {
            180.0
        }
        val gapToNext = nextLineStartMs?.let { (it - last.startMs).coerceAtLeast(0L) }
        val estimatedDuration = when {
            gapToNext != null && gapToNext <= 4_000L -> gapToNext
            gapToNext != null -> (last.text.trim().length * avgMsPerChar).roundToLong().coerceIn(200L, minOf(4_000L, gapToNext))
            else -> (last.text.trim().length * avgMsPerChar).roundToLong().coerceIn(200L, 4_000L)
        }
        return words.dropLast(1) + last.copy(durationMs = estimatedDuration)
    }

    private fun parseTimestampMs(
        minuteText: String,
        secondText: String,
        fractionText: String,
        offsetMs: Long,
    ): Long? {
        val minutes = minuteText.toLongOrNull() ?: return null
        val seconds = secondText.toLongOrNull() ?: return null
        val fracMs = if (fractionText.isEmpty()) 0L else fractionText.take(3).padEnd(3, '0').toLongOrNull() ?: return null
        val remainder = seconds * 1_000L + fracMs
        if (minutes > (Long.MAX_VALUE - remainder) / 60_000L) return null
        val rawMs = minutes * 60_000L + remainder
        return applyOffset(rawMs, offsetMs)
    }

    private fun applyOffset(startMs: Long, offsetMs: Long): Long =
        if (offsetMs < 0L && startMs > Long.MAX_VALUE + offsetMs) {
            Long.MAX_VALUE
        } else {
            (startMs - offsetMs).coerceAtLeast(0L)
        }

    private fun shiftTimestamp(ts: Long, shift: Long): Long = when {
        shift > 0L && ts > Long.MAX_VALUE - shift -> Long.MAX_VALUE
        shift < 0L && ts < -shift -> 0L
        else -> (ts + shift).coerceAtLeast(0L)
    }
}

internal object TtmlParser {
    private val transliterationTag = metadataTag("transliteration")
    private val referenceTextTag = metadataTag("text")
    private val pTagRegex = Regex("""<p\b([^>]*)>(.*?)</p>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val spanOrCloseRegex = Regex("""<(/?)span\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val attrRegex = Regex("""([a-zA-Z0-9_:-]+)\s*=\s*"([^"]*)"|([a-zA-Z0-9_:-]+)\s*=\s*'([^']*)'""")
    private val xmlTagRegex = Regex("""<[^>]+>""")
    private val clockRegex = Regex("""^(?:(\d+):)?(?:(\d+):)?(\d+(?:\.\d+)?)s?$""")
    private val msRegex = Regex("""^(\d+(?:\.\d+)?)ms$""", RegexOption.IGNORE_CASE)
    private val timingAttrRegex = Regex("""\b(?:[a-zA-Z0-9_-]+:)?timing\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("""\s+""")

    fun parse(ttml: String): List<LyricLine> {
        val timingMode = timingAttrRegex.find(ttml)?.groupValues?.get(1)?.lowercase()
        if (timingMode == "none") return emptyList()

        val romanizations = romanizationsByLineKey(ttml)
        val lines = mutableListOf<LyricLine>()
        for (pMatch in pTagRegex.findAll(ttml)) {
            val attrs = parseAttrs(pMatch.groupValues[1])
            val inner = pMatch.groupValues[2]
            val pBegin = parseClockMs(attrs["begin"])
            val pEnd = parseClockMs(attrs["end"]) ?: attrs["dur"]?.let { d ->
                val durMs = parseClockMs(d)
                if (pBegin != null && durMs != null) pBegin + durMs else null
            }

            val words = parseSpans(inner)
            val lineStart = pBegin ?: words.firstOrNull()?.startMs ?: continue
            val plainText = if (words.isNotEmpty()) {
                words.joinToString("") { it.text }.trim()
            } else {
                plainXmlText(inner)
            }
            if (plainText.isEmpty()) continue
            val lineEnd = pEnd ?: words.lastOrNull()?.endMs
            lines += LyricLine(lineStart, plainText, words, lineEnd, attrs["key"]?.let(romanizations::get))
        }
        return lines.sortedBy { it.startMs }
    }

    private fun romanizationsByLineKey(ttml: String): Map<String, String> = buildMap {
        for (transliteration in transliterationTag.findAll(ttml)) {
            val lang = parseAttrs(transliteration.groupValues[1])["lang"]
            if (lang != null && lang.split('-').none { it.equals("Latn", ignoreCase = true) }) continue
            for (text in referenceTextTag.findAll(transliteration.groupValues[2])) {
                val key = parseAttrs(text.groupValues[1])["for"] ?: continue
                val value = plainXmlText(text.groupValues[2])
                if (hasLatinLetters(value) && key !in this) {
                    put(key, value)
                }
            }
        }
    }

    private fun metadataTag(name: String) = Regex(
        """<(?:[\w.-]+:)?$name(?=[\s>])([^>]*)>(.*?)</(?:[\w.-]+:)?$name\s*>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    private fun plainXmlText(text: String): String =
        whitespace.replace(unescapeXml(xmlTagRegex.replace(text, "")).trim(), " ")

    private fun parseSpans(inner: String): List<LyricWord> {
        val matches = spanOrCloseRegex.findAll(inner).toList()
        if (matches.isEmpty()) return emptyList()

        data class SpanFrame(val beginMs: Long?, val endMs: Long?, var hasChildSpan: Boolean = false)

        val stack = ArrayDeque<SpanFrame>()
        val words = mutableListOf<LyricWord>()
        var cursor = 0
        var bgDepth = 0

        for (m in matches) {
            val rawText = unescapeXml(inner.substring(cursor, m.range.first))
            if (rawText.isNotEmpty()) {
                val frame = stack.lastOrNull()
                if (frame != null && !frame.hasChildSpan && frame.beginMs != null) {
                    val trimmed = rawText.trim()
                    if (trimmed.isNotEmpty()) {
                        val dur = ((frame.endMs ?: frame.beginMs) - frame.beginMs).coerceAtLeast(0L)
                        val text = if (bgDepth > 0 && words.lastOrNull()?.text?.endsWith("(") != true) {
                            "($rawText"
                        } else {
                            rawText
                        }
                        words += LyricWord(frame.beginMs, dur, text)
                    }
                } else if (words.isNotEmpty()) {
                    val tailSpace = if (rawText.isBlank()) " " else rawText
                    val last = words.last()
                    words[words.lastIndex] = last.copy(text = last.text + tailSpace)
                }
            }

            val isClose = m.groupValues[1] == "/"
            if (!isClose) {
                stack.lastOrNull()?.let { parent ->
                    if (!parent.hasChildSpan) {
                        parent.hasChildSpan = true
                        bgDepth++
                        if (words.isNotEmpty() && !words.last().text.endsWith(" ")) {
                            val last = words.last()
                            words[words.lastIndex] = last.copy(text = last.text + " ")
                        }
                    }
                }
                val attrs = parseAttrs(m.groupValues[2])
                val begin = parseClockMs(attrs["begin"])
                val end = parseClockMs(attrs["end"]) ?: attrs["dur"]?.let { d ->
                    val durMs = parseClockMs(d)
                    if (begin != null && durMs != null) begin + durMs else null
                }
                stack.addLast(SpanFrame(begin, end))
            } else {
                val popped = if (stack.isNotEmpty()) stack.removeLast() else null
                if (popped?.hasChildSpan == true && bgDepth > 0) {
                    bgDepth--
                    if (words.isNotEmpty()) {
                        val last = words.last()
                        val cleaned = last.text.trimEnd()
                        words[words.lastIndex] = last.copy(text = "$cleaned)")
                    }
                }
            }
            cursor = m.range.last + 1
        }

        if (words.isNotEmpty()) {
            val last = words.last()
            val cleaned = last.text.trimEnd()
            if (cleaned.isEmpty()) words.removeAt(words.lastIndex)
            else if (cleaned != last.text) words[words.lastIndex] = last.copy(text = cleaned)
        }
        return words
    }

    private fun parseAttrs(raw: String): Map<String, String> = buildMap {
        for (m in attrRegex.findAll(raw)) {
            val key = (m.groupValues[1].ifEmpty { m.groupValues[3] }).substringAfterLast(':').lowercase()
            val value = if (m.groupValues[1].isNotEmpty()) m.groupValues[2] else m.groupValues[4]
            put(key, unescapeXml(value))
        }
    }

    internal fun parseClockMs(value: String?): Long? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        msRegex.matchEntire(text)?.let { m ->
            return m.groupValues[1].toDoubleOrNull()?.roundToLong()?.coerceAtLeast(0L)
        }
        val match = clockRegex.matchEntire(text) ?: return null
        val a = match.groupValues[1]
        val b = match.groupValues[2]
        val sec = match.groupValues[3].toDoubleOrNull() ?: return null
        val hours = if (b.isNotEmpty()) a.toLongOrNull() ?: 0L else 0L
        val minutes = if (b.isNotEmpty()) b.toLongOrNull() ?: 0L else a.toLongOrNull() ?: 0L
        return ((hours * 3600.0 + minutes * 60.0 + sec) * 1000.0).roundToLong().coerceAtLeast(0L)
    }

    private fun unescapeXml(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}

/** Indeks terakhir yang sudah mulai, atau -1 sebelum baris pertama. */
internal fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    var low = 0
    var high = lines.size
    while (low < high) {
        val mid = low + (high - low) / 2
        if (lines[mid].startMs <= positionMs) low = mid + 1 else high = mid
    }
    return low - 1
}
