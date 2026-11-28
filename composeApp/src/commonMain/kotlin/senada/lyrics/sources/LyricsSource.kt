package senada.lyrics.sources

import senada.lyrics.LyricLine
import senada.model.Track

/** One lyrics provider. Implementations never throw for "not found"; they return an empty list. */
internal interface LyricsSource {
    /** Stable id, e.g. "betterlyrics", "lyricsplus", "simpmusic", "netease", "qqmusic", "kugou". */
    val id: String

    /** Candidates for [track], best first. Network/parse failures are thrown as [LyricsSourceException]. */
    suspend fun search(track: Track): List<LyricsCandidate>
}

internal data class LyricsCandidate(
    val sourceId: String,
    val lines: List<LyricLine>,
    /** True when at least most lines carry per-word timing. */
    val wordSynced: Boolean,
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
)

internal class LyricsSourceException(val sourceId: String, message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** YouTube Music video id from a Senada track id such as `yt:track:<videoId>`, or null. */
internal fun Track.youtubeVideoId(): String? =
    id.removePrefix("yt:track:").takeIf { it != id && it.isNotBlank() }
