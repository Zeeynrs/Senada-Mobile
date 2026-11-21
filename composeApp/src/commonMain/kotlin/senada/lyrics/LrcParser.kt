package senada.lyrics

internal data class LyricLine(val startMs: Long, val text: String)

internal object LrcParser {
    fun parse(text: String): List<LyricLine> = emptyList()
}

internal fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int = -1
