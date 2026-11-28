package senada.lyrics.sources

import kotlin.math.roundToLong
import senada.model.Track

internal data class TrackQuery(
    val title: String,
    val artist: String,
    val durationSeconds: Double?,
)

private val spaces = Regex("""\s+""")
private const val VIDEO_LABEL = """official (?:audio|(?:music )?video)|(?:official )?lyric(?:s)? video"""
private val videoSuffix = Regex(
    """\s*(?:\(\s*(?:$VIDEO_LABEL)\s*\)|\[\s*(?:$VIDEO_LABEL)\s*\]|\s[-–—]\s+(?:$VIDEO_LABEL))$""",
    RegexOption.IGNORE_CASE,
)
private val topicSuffix = Regex("""\s+[-–—]\s+Topic$""", RegexOption.IGNORE_CASE)

internal fun Track.lyricsQuery(): TrackQuery {
    val originalTitle = spaces.replace(title.trim(), " ")
    // Keep recording qualifiers such as Live, Remix and Acoustic for timing accuracy.
    var queryTitle = originalTitle
    while (true) {
        val suffix = videoSuffix.find(queryTitle) ?: break
        queryTitle = queryTitle.substring(0, suffix.range.first).trimEnd()
    }
    val queryArtist = artists.asSequence()
        .map { spaces.replace(it.trim(), " ") }
        .map { topicSuffix.replace(it, "") }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(", ")
    return TrackQuery(
        title = queryTitle.ifBlank { originalTitle },
        artist = queryArtist,
        durationSeconds = durationMs?.takeIf { it > 0L }?.div(1_000.0),
    )
}

internal fun Double.lyricsDurationMs(): Long? =
    takeIf { it.isFinite() && it > 0.0 }?.let { (it * 1_000).roundToLong() }
