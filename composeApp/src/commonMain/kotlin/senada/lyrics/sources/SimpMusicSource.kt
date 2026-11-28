// Portions adapted from Mochy Music (https://github.com/realidkroo/Mochy-Music, commit 3071023), GPL-3.0.
package senada.lyrics.sources

import io.ktor.client.HttpClient
import kotlin.math.abs
import kotlinx.serialization.json.JsonObject
import senada.lyrics.LrcParser
import senada.model.Track

internal class SimpMusicSource(private val http: HttpClient) : LyricsSource {
    override val id = "simpmusic"

    override suspend fun search(track: Track): List<LyricsCandidate> {
        val videoId = track.youtubeVideoId()?.takeIf(VIDEO_ID::matches) ?: return emptyList()
        val durationMs = track.durationMs?.takeIf { it > 0L }
        return http.getLyricsJson(id, "https://api-lyrics.simpmusic.org/v1/$videoId") { response ->
            if (response.lyricsString("type") != "success" && !response.lyricsFlag("success")) {
                return@getLyricsJson emptyList()
            }
            response.lyricsArray("data")
                .filter { it.lyricsString("videoId").isNullOrBlank() || it.lyricsString("videoId") == videoId }
                .mapNotNull(::parseCandidate)
                .filter { durationMs == null || it.durationMs == null || abs(it.durationMs - durationMs) <= 10_000L }
                .sortedWith(
                    compareBy<LyricsCandidate> {
                        if (durationMs != null && it.durationMs != null) abs(it.durationMs - durationMs)
                        else if (durationMs == null) 0L else Long.MAX_VALUE
                    }.thenByDescending { it.wordSynced },
                )
        }.orEmpty()
    }

    private fun parseCandidate(entry: JsonObject): LyricsCandidate? {
        val lines = sequenceOf("richSyncLyrics", "syncedLyrics")
            .mapNotNull(entry::lyricsString)
            .map(LrcParser::parse)
            .firstOrNull { parsed -> parsed.any { it.text.isNotBlank() || it.background != null } }
            ?: return null
        return LyricsCandidate(
            sourceId = id,
            lines = lines,
            wordSynced = lines.hasWordTiming(),
            title = entry.lyricsString("songTitle"),
            artist = entry.lyricsString("artistName"),
            durationMs = entry.lyricsDouble("durationSeconds")?.lyricsDurationMs(),
        )
    }

    private companion object {
        val VIDEO_ID = Regex("[A-Za-z0-9_-]+")
    }
}
