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
) {
    fun activeWordIndex(positionMs: Long): Int {
        // Some TTML backing vocals overlap. Keep filling both, and emphasize the word that started last.
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
    if (sourceWords.isNotEmpty()) return KaraokeTiming(text, sourceWords, isEstimated = false)

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
    return KaraokeTiming(text, estimatedWords, isEstimated = true)
}

private fun IntArray.boundaryAtOrAfter(offset: Int): Int {
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
