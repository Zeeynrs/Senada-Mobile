package senada.lyrics

internal data class LyricLine(val startMs: Long, val text: String)

internal object LrcParser {
    private val timestamp = Regex("""\[([0-9]+):([0-5]?[0-9])(?:\.([0-9]{1,3}))?\]""")
    private val metadata = Regex("""\[[a-zA-Z]+:[^\]]*\]""")

    fun parse(text: String): List<LyricLine> = buildList {
        for (raw in text.lineSequence()) {
            val line = metadata.replace(raw, "")
            val words = timestamp.replace(line, "").trim()
            for (match in timestamp.findAll(line)) {
                val (minuteText, secondText, fraction) = match.destructured
                val minutes = minuteText.toLongOrNull() ?: continue
                val remainder = secondText.toLong() * 1000 + fraction.padEnd(3, '0').toLong()
                if (minutes <= (Long.MAX_VALUE - remainder) / 60_000) {
                    add(LyricLine(minutes * 60_000 + remainder, words))
                }
            }
        }
    }.sortedBy { it.startMs }
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
