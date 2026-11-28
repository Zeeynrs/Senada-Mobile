package senada.lyrics

internal enum class LyricLineAlignment {
    Start,
    Center,
    End,
}

internal const val BACKING_VOCAL_WINDOW_MS = 500L

private const val DEFAULT_AGENT = "v1"
private const val GROUP_AGENT = "v1000"

internal fun defaultLineAlignment(alignment: LyricsAlignment): LyricLineAlignment = when (alignment) {
    LyricsAlignment.Left -> LyricLineAlignment.Start
    LyricsAlignment.Center -> LyricLineAlignment.Center
}

/**
 * Computes per-line horizontal alignment for a synced lyrics list.
 *
 * When a song has only one distinct voice across its main (non-background) lines,
 * every line keeps the user's configured [defaultAlignment]. When multiple voices are
 * present, the primary voice aligns to the start, secondary voices align to the end,
 * and group/chorus lines (`v1000`) center.
 */
internal fun resolveLineAlignments(
    lines: List<LyricLine>,
    defaultAlignment: LyricsAlignment = LyricsAlignment.Left,
): List<LyricLineAlignment> {
    if (lines.isEmpty()) return emptyList()
    val fallback = defaultLineAlignment(defaultAlignment)
    val mainLines = lines.filter { it.text.isNotBlank() || it.background == null }
    val distinctAgents = mainLines
        .map { it.agent?.trim()?.takeIf(String::isNotEmpty) ?: DEFAULT_AGENT }
        .distinct()
    if (distinctAgents.size <= 1) {
        return List(lines.size) { fallback }
    }
    val primaryAgent = if (DEFAULT_AGENT in distinctAgents) DEFAULT_AGENT else distinctAgents.first()
    return lines.map { line ->
        val agent = line.agent?.trim()?.takeIf(String::isNotEmpty) ?: primaryAgent
        when {
            agent.equals(GROUP_AGENT, ignoreCase = true) -> LyricLineAlignment.Center
            agent == primaryAgent -> LyricLineAlignment.Start
            else -> LyricLineAlignment.End
        }
    }
}

/**
 * Whether a parent line's [background] vocal should be displayed at [positionMs].
 *
 * Shown while its parent line is active, or within ±[BACKING_VOCAL_WINDOW_MS] of its
 * own sung span so trailing/leading ad-libs do not clip off abruptly.
 */
internal fun isBackgroundVocalVisible(
    parentActive: Boolean,
    background: LyricLine,
    parentEndMs: Long?,
    positionMs: Long,
): Boolean {
    if (parentActive) return true
    val startMs = background.startMs
    val endMs = background.endMs
        ?: background.words.lastOrNull()?.endMs
        ?: parentEndMs?.takeIf { it >= startMs }
        ?: startMs
    return positionMs >= startMs - BACKING_VOCAL_WINDOW_MS &&
        positionMs <= endMs + BACKING_VOCAL_WINDOW_MS
}
