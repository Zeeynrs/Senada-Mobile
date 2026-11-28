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
    val agent: String? = null,
    val background: LyricLine? = null,
)

private val whitespaceRun = Regex("""\s+""")

private fun Char.isBackingBracket(): Boolean = this == '(' || this == ')' || this == '[' || this == ']'

internal fun stripBackingBrackets(text: String): String =
    whitespaceRun.replace(text.filterNot(Char::isBackingBracket).trim(), " ")

internal fun stripBackingBrackets(words: List<LyricWord>): List<LyricWord> {
    if (words.isEmpty()) return emptyList()
    val cleaned = ArrayList<LyricWord>(words.size)
    for (word in words) {
        val bare = whitespaceRun.replace(word.text.filterNot(Char::isBackingBracket), " ")
        if (bare.isBlank()) {
            if (bare.isNotEmpty() && cleaned.isNotEmpty() && !cleaned.last().text.endsWith(' ')) {
                val last = cleaned.last()
                cleaned[cleaned.lastIndex] = last.copy(text = last.text + " ")
            }
            continue
        }
        val normalized = if (cleaned.isEmpty()) bare.trimStart() else bare
        cleaned += if (normalized == word.text) word else word.copy(text = normalized)
    }
    trimLastWord(cleaned)
    return cleaned
}

private fun trimLastWord(words: MutableList<LyricWord>) {
    if (words.isEmpty()) return
    val last = words.last()
    val cleaned = last.text.trimEnd()
    if (cleaned.isEmpty()) {
        words.removeAt(words.lastIndex)
    } else if (cleaned != last.text) {
        words[words.lastIndex] = last.copy(text = cleaned)
    }
}

private fun mergeBackground(existing: LyricLine?, added: LyricLine): LyricLine {
    if (existing == null) return added
    val mergedWords = if (existing.words.isNotEmpty() && added.words.isNotEmpty()) {
        val left = existing.words.toMutableList()
        val last = left.last()
        if (!last.text.endsWith(' ')) {
            left[left.lastIndex] = last.copy(text = last.text + " ")
        }
        left + added.words
    } else {
        existing.words + added.words
    }
    val mergedEnd = listOfNotNull(existing.endMs, added.endMs).maxOrNull()
    return LyricLine(
        startMs = minOf(existing.startMs, added.startMs),
        text = "${existing.text} ${added.text}".trim(),
        words = mergedWords,
        endMs = mergedEnd,
        agent = existing.agent ?: added.agent,
    )
}

private fun splitInlineBackingVocal(line: LyricLine): LyricLine {
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

    val leadText = whitespaceRun.replace(lead.toString().trim(), " ")
    val backingText = whitespaceRun.replace(backing.toString().trim(), " ")
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
            } else if (!text[i].isBackingBracket()) {
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
                    for (i in start until end) if (inBracket[i] && !text[i].isBackingBracket()) append(text[i])
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

internal object LrcParser {
    private val lineTimestamp = Regex("""\[([0-9]+):([0-5]?[0-9])(?:[.:]([0-9]+))?\]""")
    private val wordTimestamp = Regex("""<([0-9]+):([0-5]?[0-9])(?:[.:]([0-9]+))?>""")
    private val metadata = Regex("""\[[a-zA-Z#]+:[^\]]*\]""")
    private val offset = Regex("""\[offset:\s*([+-]?[0-9]+)\s*\]""", RegexOption.IGNORE_CASE)
    private val agentTag = Regex("""\{agent:([^}]+)\}""", RegexOption.IGNORE_CASE)
    private val backgroundTag = Regex("""^\s*\{bg\}\s*""", RegexOption.IGNORE_CASE)

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
            val agent: String?,
            val isBackground: Boolean,
        )

        val rawLines = buildList {
            for (raw in text.lineSequence()) {
                val line = metadata.replace(raw, "")
                val stamps = lineTimestamp.findAll(line).mapNotNull { match ->
                    parseTimestampMs(match.groupValues[1], match.groupValues[2], match.groupValues[3], offsetMs)
                }.toList()
                if (stamps.isEmpty()) continue

                var body = lineTimestamp.replace(line, "").trim()
                val agentMatch = agentTag.find(body)
                val agent = agentMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
                if (agentMatch != null) {
                    body = body.replaceFirst(agentTag, "").trim()
                }
                val isBackground = backgroundTag.containsMatchIn(body)
                if (isBackground) {
                    body = body.replaceFirst(backgroundTag, "").trim()
                }

                val wordTags = wordTimestamp.findAll(body).toList()
                if (wordTags.isEmpty()) {
                    for (startMs in stamps) {
                        add(
                            RawParsedLine(
                                startMs = startMs,
                                text = body,
                                words = emptyList(),
                                endMs = null,
                                hasOpenLastWord = false,
                                agent = agent,
                                isBackground = isBackground,
                            ),
                        )
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
                            agent = agent,
                            isBackground = isBackground,
                        ),
                    )
                }
            }
        }.sortedWith(compareBy<RawParsedLine>({ it.startMs }, { it.isBackground }))

        val finalized = rawLines.mapIndexed { index, current ->
            val words: List<LyricWord>
            val resolvedEnd: Long?
            if (current.words.isEmpty() || !current.hasOpenLastWord) {
                words = current.words
                resolvedEnd = current.endMs
            } else {
                val nextStart = rawLines.drop(index + 1)
                    .firstOrNull { it.startMs > current.startMs && (current.isBackground || !it.isBackground) }
                    ?.startMs
                words = finalizeOpenLastWord(current.words, nextStart)
                resolvedEnd = current.endMs ?: words.lastOrNull()?.endMs
            }
            current.isBackground to LyricLine(
                startMs = current.startMs,
                text = current.text,
                words = words,
                endMs = resolvedEnd,
                agent = current.agent,
            )
        }

        val result = mutableListOf<LyricLine>()
        for ((isBackground, parsedLine) in finalized) {
            if (isBackground) {
                val bgText = stripBackingBrackets(parsedLine.text)
                val bgWords = stripBackingBrackets(parsedLine.words)
                if (bgText.isEmpty()) continue
                val bgLine = LyricLine(
                    startMs = bgWords.firstOrNull()?.startMs ?: parsedLine.startMs,
                    text = bgText,
                    words = bgWords,
                    endMs = parsedLine.endMs ?: bgWords.lastOrNull()?.endMs,
                    agent = parsedLine.agent,
                )
                if (result.isNotEmpty()) {
                    val parent = result.last()
                    result[result.lastIndex] = parent.copy(background = mergeBackground(parent.background, bgLine))
                } else {
                    result += LyricLine(
                        startMs = bgLine.startMs,
                        text = "",
                        endMs = bgLine.endMs,
                        agent = bgLine.agent,
                        background = bgLine,
                    )
                }
            } else {
                result += splitInlineBackingVocal(parsedLine)
            }
        }
        return result
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
    private val divOrPTagRegex = Regex(
        """<div\b([^>]*?)(/?)>|</div\s*>|<p\b([^>]*)>(.*?)</p>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
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
        val divAgents = ArrayDeque<String?>()
        val lines = mutableListOf<LyricLine>()
        for (match in divOrPTagRegex.findAll(ttml)) {
            val rawTag = match.value
            when {
                rawTag.startsWith("</div", ignoreCase = true) -> {
                    if (divAgents.isNotEmpty()) divAgents.removeLast()
                }
                rawTag.startsWith("<div", ignoreCase = true) -> {
                    if (match.groupValues[2] != "/") {
                        val divAttrs = parseAttrs(match.groupValues[1])
                        val ownAgent = divAttrs["agent"]?.trim()?.takeIf { it.isNotEmpty() }
                        divAgents.addLast(ownAgent ?: divAgents.lastOrNull())
                    }
                }
                else -> {
                    val attrs = parseAttrs(match.groupValues[3])
                    val inner = match.groupValues[4]
                    val pBegin = parseClockMs(attrs["begin"])
                    val pEnd = parseSpanEnd(attrs, pBegin)
                    val lineAgent = attrs["agent"]?.trim()?.takeIf { it.isNotEmpty() } ?: divAgents.lastOrNull()

                    val parsed = parseParagraphContent(inner, pBegin, pEnd)
                    val lineStart = pBegin ?: parsed.words.firstOrNull()?.startMs ?: parsed.background?.startMs ?: continue
                    if (parsed.text.isEmpty() && parsed.background == null) continue
                    val lineEnd = pEnd ?: parsed.words.lastOrNull()?.endMs ?: parsed.background?.endMs
                    val rawLine = LyricLine(
                        startMs = lineStart,
                        text = parsed.text,
                        words = parsed.words,
                        endMs = lineEnd,
                        romanization = attrs["key"]?.let(romanizations::get),
                        agent = lineAgent,
                        background = parsed.background,
                    )
                    lines += if (parsed.usedLegacyNestedBg) rawLine else splitInlineBackingVocal(rawLine)
                }
            }
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

    private fun parseSpanEnd(attrs: Map<String, String>, beginMs: Long?): Long? =
        parseClockMs(attrs["end"]) ?: attrs["dur"]?.let { d ->
            val durMs = parseClockMs(d)
            if (beginMs != null && durMs != null) beginMs + durMs else null
        }

    private data class ParsedParagraph(
        val text: String,
        val words: List<LyricWord>,
        val background: LyricLine?,
        val usedLegacyNestedBg: Boolean,
    )

    private fun parseParagraphContent(inner: String, pBegin: Long?, pEnd: Long?): ParsedParagraph {
        val matches = spanOrCloseRegex.findAll(inner).toList()
        if (matches.isEmpty()) {
            return ParsedParagraph(plainXmlText(inner), emptyList(), background = null, usedLegacyNestedBg = false)
        }

        data class SpanFrame(
            val beginMs: Long?,
            val endMs: Long?,
            val isBgRoot: Boolean,
            val isIgnoredRoot: Boolean,
            var hasChildSpan: Boolean = false,
        )

        val stack = ArrayDeque<SpanFrame>()
        val mainWords = mutableListOf<LyricWord>()
        val bgWords = mutableListOf<LyricWord>()
        val mainPlain = StringBuilder()
        val bgPlain = StringBuilder()
        var cursor = 0
        var xBgDepth = 0
        var ignoredDepth = 0
        var legacyBgDepth = 0
        var usedLegacyNestedBg = false
        var bgSpanStartMs: Long? = null
        var bgSpanEndMs: Long? = null
        var bgAgent: String? = null

        fun appendTailSpace(target: MutableList<LyricWord>, rawText: String) {
            if (target.isEmpty()) return
            val tailSpace = if (rawText.isBlank()) " " else rawText
            val last = target.last()
            if (tailSpace == " " && last.text.endsWith(' ')) return
            target[target.lastIndex] = last.copy(text = last.text + tailSpace)
        }

        for (m in matches) {
            val rawText = unescapeXml(inner.substring(cursor, m.range.first))
            if (rawText.isNotEmpty() && ignoredDepth == 0) {
                val frame = stack.lastOrNull()
                if (xBgDepth > 0) {
                    bgPlain.append(rawText)
                    if (frame != null && !frame.isBgRoot && !frame.hasChildSpan && frame.beginMs != null) {
                        if (rawText.trim().isNotEmpty()) {
                            val dur = ((frame.endMs ?: frame.beginMs) - frame.beginMs).coerceAtLeast(0L)
                            bgWords += LyricWord(frame.beginMs, dur, rawText)
                        }
                    } else {
                        appendTailSpace(bgWords, rawText)
                    }
                } else {
                    mainPlain.append(rawText)
                    if (frame != null && !frame.hasChildSpan && frame.beginMs != null) {
                        if (rawText.trim().isNotEmpty()) {
                            val dur = ((frame.endMs ?: frame.beginMs) - frame.beginMs).coerceAtLeast(0L)
                            val wordText = if (legacyBgDepth > 0 && mainWords.lastOrNull()?.text?.endsWith("(") != true) {
                                "($rawText"
                            } else {
                                rawText
                            }
                            mainWords += LyricWord(frame.beginMs, dur, wordText)
                        }
                    } else {
                        appendTailSpace(mainWords, rawText)
                    }
                }
            }

            val isClose = m.groupValues[1] == "/"
            val rawAttrs = m.groupValues[2]
            val isSelfClosing = !isClose && rawAttrs.trimEnd().endsWith('/')
            if (!isClose && !isSelfClosing) {
                val attrs = parseAttrs(rawAttrs)
                val role = attrs["role"]?.lowercase()
                val begin = parseClockMs(attrs["begin"])
                val end = parseSpanEnd(attrs, begin)
                val spanAgent = attrs["agent"]?.trim()?.takeIf { it.isNotEmpty() }
                val isIgnoredRoot = ignoredDepth == 0 && (role == "x-translation" || role == "x-roman")
                val isBgRoot = ignoredDepth == 0 && !isIgnoredRoot && xBgDepth == 0 && role == "x-bg"

                when {
                    isIgnoredRoot || ignoredDepth > 0 -> ignoredDepth++
                    isBgRoot -> {
                        xBgDepth++
                        if (bgPlain.isNotEmpty() && !bgPlain.last().isWhitespace()) bgPlain.append(' ')
                        if (bgWords.isNotEmpty() && !bgWords.last().text.endsWith(' ')) {
                            val last = bgWords.last()
                            bgWords[bgWords.lastIndex] = last.copy(text = last.text + " ")
                        }
                        if (begin != null) bgSpanStartMs = bgSpanStartMs?.let { minOf(it, begin) } ?: begin
                        if (end != null) bgSpanEndMs = bgSpanEndMs?.let { maxOf(it, end) } ?: end
                        if (bgAgent == null && spanAgent != null) bgAgent = spanAgent
                    }
                    xBgDepth > 0 -> {
                        stack.lastOrNull()?.hasChildSpan = true
                        xBgDepth++
                    }
                    else -> {
                        stack.lastOrNull()?.let { parent ->
                            if (!parent.hasChildSpan) {
                                parent.hasChildSpan = true
                                legacyBgDepth++
                                usedLegacyNestedBg = true
                                if (mainWords.isNotEmpty() && !mainWords.last().text.endsWith(" ")) {
                                    val last = mainWords.last()
                                    mainWords[mainWords.lastIndex] = last.copy(text = last.text + " ")
                                }
                            }
                        }
                    }
                }
                stack.addLast(SpanFrame(begin, end, isBgRoot = isBgRoot, isIgnoredRoot = isIgnoredRoot))
            } else if (isClose) {
                val popped = if (stack.isNotEmpty()) stack.removeLast() else null
                when {
                    ignoredDepth > 0 -> ignoredDepth--
                    xBgDepth > 0 -> xBgDepth--
                    popped?.hasChildSpan == true && legacyBgDepth > 0 -> {
                        legacyBgDepth--
                        if (mainWords.isNotEmpty()) {
                            val last = mainWords.last()
                            val cleaned = last.text.trimEnd()
                            mainWords[mainWords.lastIndex] = last.copy(text = "$cleaned)")
                        }
                    }
                }
            }
            cursor = m.range.last + 1
        }

        if (cursor < inner.length && ignoredDepth == 0) {
            val tail = unescapeXml(inner.substring(cursor))
            if (tail.isNotEmpty()) {
                if (xBgDepth > 0) {
                    bgPlain.append(tail)
                } else {
                    mainPlain.append(tail)
                    appendTailSpace(mainWords, tail)
                }
            }
        }

        trimLastWord(mainWords)
        val cleanedBgWords = stripBackingBrackets(bgWords)
        val mainText = if (mainWords.isNotEmpty()) {
            mainWords.joinToString("") { it.text }.trim()
        } else {
            plainXmlText(mainPlain.toString())
        }
        val bgText = if (cleanedBgWords.isNotEmpty()) {
            stripBackingBrackets(cleanedBgWords.joinToString("") { it.text })
        } else {
            stripBackingBrackets(plainXmlText(bgPlain.toString()))
        }
        val background = if (bgText.isNotEmpty()) {
            val bgStart = bgSpanStartMs ?: cleanedBgWords.firstOrNull()?.startMs ?: pBegin
            val bgEnd = bgSpanEndMs ?: cleanedBgWords.lastOrNull()?.endMs ?: pEnd
            bgStart?.let { start ->
                LyricLine(
                    startMs = start,
                    text = bgText,
                    words = cleanedBgWords,
                    endMs = bgEnd,
                    agent = bgAgent,
                )
            }
        } else {
            null
        }
        return ParsedParagraph(mainText, mainWords, background, usedLegacyNestedBg)
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
