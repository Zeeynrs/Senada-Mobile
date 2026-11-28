// Portions adapted from Mochy Music (https://github.com/realidkroo/Mochy-Music, commit 3071023), GPL-3.0.
package senada.lyrics.sources

import io.ktor.client.HttpClient
import kotlin.math.roundToLong
import senada.lyrics.TtmlParser
import senada.model.Track

internal class BetterLyricsSource(private val http: HttpClient) : LyricsSource {
    override val id = "betterlyrics"

    override suspend fun search(track: Track): List<LyricsCandidate> {
        val query = track.lyricsQuery()
        if (query.title.isBlank() || query.artist.isBlank()) return emptyList()
        val lines = http.getLyricsJsonFromMirrors(
            id,
            ENDPOINTS,
            mapOf(
                "s" to query.title,
                "a" to query.artist,
                "d" to query.durationSeconds?.roundToLong()?.takeIf { it > 0 },
            ),
        ) { response -> response.lyricsString("ttml")?.let(TtmlParser::parse)?.takeIf { it.isNotEmpty() } }
            ?: return emptyList()
        return listOf(LyricsCandidate(id, lines, lines.hasWordTiming()))
    }

    private companion object {
        val ENDPOINTS = listOf(
            "https://api.betterlyrics.org/getLyrics",
            "https://lyrics-api.boidu.dev/getLyrics",
        )
    }
}
