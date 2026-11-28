package senada.lyrics

internal class KaraokeWord(
    val startMs: Long,
    val endMs: Long,
    val graphemeOffsets: IntArray,
) {
    val graphemeCount: Int get() = graphemeOffsets.size - 1

    fun graphemesAt(positionMs: Long): Double = when {
        positionMs < startMs -> 0.0
        positionMs >= endMs -> graphemeCount.toDouble()
        else -> graphemeCount * (positionMs - startMs).toDouble() / (endMs - startMs)
    }
}

internal class KaraokeTiming(
    val text: String,
    val words: List<KaraokeWord>,
    val isEstimated: Boolean,
    val offsets: IntArray = graphemeOffsets(text),
) {
    private val sweepSpans = buildList {
        var startMs = words.firstOrNull()?.startMs ?: return@buildList
        for ((index, word) in words.withIndex()) {
            val next = words.getOrNull(index + 1)
            val nextStartMs = next?.startMs?.coerceAtLeast(startMs)
            // Hand the single head to the next cue at its start even if the source cues overlap.
            val endMs = word.endMs.coerceIn(startMs, nextStartMs ?: Long.MAX_VALUE)
            val start = offsets.boundaryAtOrAfter(word.graphemeOffsets.first())
            val end = start + word.graphemeCount
            val nextStart = next?.let { offsets.boundaryAtOrAfter(it.graphemeOffsets.first()) } ?: offsets.lastIndex
            val hasGap = nextStartMs != null && nextStartMs - endMs > SHORT_GAP_MS
            val prefix = if (index == 0) start else 0
            val tail = if (hasGap) 0 else nextStart - end

            val timePerGrapheme = (endMs - startMs).toDouble() /
                (word.graphemeCount + (prefix + tail) * SPACE_TIME_WEIGHT)
            val wordStartMs = startMs + prefix * SPACE_TIME_WEIGHT * timePerGrapheme
            val wordEndMs = endMs - tail * SPACE_TIME_WEIGHT * timePerGrapheme
            if (prefix > 0) add(SweepSpan(startMs.toDouble(), wordStartMs, 0, start))
            add(SweepSpan(wordStartMs, wordEndMs, start, end))
            if (tail > 0) add(SweepSpan(wordEndMs, endMs.toDouble(), end, nextStart))
            if (nextStartMs != null && nextStartMs > endMs) {
                add(SweepSpan(endMs.toDouble(), nextStartMs.toDouble(), end + tail, nextStart, ease = hasGap))
            }
            startMs = nextStartMs ?: endMs
        }
    }

    /**
     * How lit a line timed only by line is, 0..1, or null for word-timed lines.
     *
     * Without word timing there is nothing to sweep by: a guessed sweep stretched across the gap to the
     * next line and was still running after the line had been sung. Such a line lights up whole instead.
     */
    fun lineLightAt(positionMs: Long): Float? {
        if (!isEstimated) return null
        val startMs = words.firstOrNull()?.startMs ?: return 1f
        val t = ((positionMs - startMs).toFloat() / LINE_LIGHT_UP_MS).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun headAt(positionMs: Long): Float {
        if (sweepSpans.isEmpty() || positionMs < sweepSpans.first().startMs) return 0f
        val span = sweepSpans.firstOrNull { positionMs < it.endMs } ?: return offsets.lastIndex.toFloat()
        return span.headAt(positionMs)
    }

    fun activeWordIndex(positionMs: Long): Int {
        // Some TTML backing vocals overlap. Emphasize the word that started last.
        var active = -1
        var latestStart = Long.MIN_VALUE
        for (index in words.indices) {
            val word = words[index]
            if (positionMs >= word.startMs && positionMs < word.endMs && word.startMs >= latestStart) {
                active = index
                latestStart = word.startMs
            }
        }
        return active
    }
}

private class SweepSpan(
    val startMs: Double,
    val endMs: Double,
    private val start: Int,
    private val end: Int,
    private val ease: Boolean = false,
) {
    fun headAt(positionMs: Long): Float {
        val fraction = (positionMs - startMs) / (endMs - startMs)
        val progress = if (ease) fraction * fraction * (3.0 - 2.0 * fraction) else fraction
        return (start + (end - start) * progress).toFloat()
    }
}

internal fun karaokeTiming(line: LyricLine, nextStartMs: Long?): KaraokeTiming {
    val text = line.text.ifBlank { "♪" }
    val offsets = graphemeOffsets(text)
    val sourceWords = buildList {
        var cursor = 0
        for (word in line.words) {
            val token = word.text.trim()
            if (token.isEmpty()) continue
            val start = text.indexOf(token, startIndex = cursor)
            if (start < 0) continue
            cursor = start + token.length
            // A provider may place a cue inside a grapheme. Its first code unit owns the whole grapheme.
            val first = offsets.boundaryAtOrAfter(start)
            val end = offsets.boundaryAtOrAfter(cursor)
            if (end > first) add(KaraokeWord(word.startMs, word.endMs, offsets.copyOfRange(first, end + 1)))
        }
    }
    if (sourceWords.isNotEmpty()) return KaraokeTiming(text, sourceWords, isEstimated = false, offsets = offsets)

    val tokens = buildList {
        var index = 0
        while (index < offsets.lastIndex) {
            if (text[offsets[index]].isWhitespace()) {
                index++
                continue
            }
            val start = index++
            while (index < offsets.lastIndex && !text[offsets[index]].isWhitespace()) index++
            add(offsets.copyOfRange(start, index + 1))
        }
    }
    val totalGraphemes = tokens.sumOf { it.size - 1 }
    val availableEnd = listOfNotNull(line.endMs, nextStartMs).filter { it > line.startMs }.minOrNull()
    val durationMs = availableEnd?.minus(line.startMs)?.coerceAtMost(MAX_ESTIMATED_DURATION_MS)
        ?: DEFAULT_ESTIMATED_DURATION_MS
    var graphemes = 0
    var startMs = line.startMs
    val estimatedWords = tokens.map { token ->
        graphemes += token.size - 1
        val endMs = line.startMs + (durationMs * graphemes + totalGraphemes / 2) / totalGraphemes
        KaraokeWord(startMs, endMs, token).also { startMs = endMs }
    }
    return KaraokeTiming(text, estimatedWords, isEstimated = true, offsets = offsets)
}

internal fun IntArray.boundaryAtOrAfter(offset: Int): Int {
    var low = 0
    var high = lastIndex
    while (low < high) {
        val mid = low + (high - low) / 2
        if (this[mid] < offset) low = mid + 1 else high = mid
    }
    return low
}

internal expect fun graphemeOffsets(text: String): IntArray

private const val DEFAULT_ESTIMATED_DURATION_MS = 4_000L
private const val MAX_ESTIMATED_DURATION_MS = 8_000L
private const val SHORT_GAP_MS = 30L
private const val LINE_LIGHT_UP_MS = 200f
private const val SPACE_TIME_WEIGHT = 0.4
