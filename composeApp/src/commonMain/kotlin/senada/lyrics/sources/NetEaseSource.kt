// Portions adapted from Mochy Music (https://github.com/realidkroo/Mochy-Music, commit 3071023), GPL-3.0.
package senada.lyrics.sources

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import senada.lyrics.LrcParser
import senada.lyrics.LyricLine
import senada.lyrics.LyricWord
import senada.model.Track

/**
 * NetEase Cloud Music lyrics source.
 *
 * Searches songs via `music.163.com/api/search/get` and fetches lyrics via
 * `music.163.com/api/song/lyric`, preferring word-timed YRC over line-timed LRC.
 */
internal class NetEaseSource(private val http: HttpClient) : LyricsSource {
    override val id: String = "netease"

    override suspend fun search(track: Track): List<LyricsCandidate> {
        if (track.title.isBlank()) return emptyList()
        val songs = searchSongs(track)
        if (songs.isEmpty()) return emptyList()

        val ranked = rankCnSearchHits(
            track,
            songs.map { song ->
                CnSearchHit(
                    titles = listOf(song.name) + song.aliases,
                    artists = song.artists,
                    durationMs = song.durationMs,
                    ref = song,
                )
            },
        )
        if (ranked.isEmpty()) return emptyList()

        val candidates = mutableListOf<LyricsCandidate>()
        for (hit in ranked.take(CN_MAX_LYRIC_FETCH_ATTEMPTS)) {
            val song = hit.ref
            val lines = fetchSongLyrics(song.id) ?: continue
            val candidate = LyricsCandidate(
                sourceId = id,
                lines = lines,
                wordSynced = hasWordSync(lines),
                title = song.name.ifBlank { null },
                artist = song.artists.joinToString(", ").ifBlank { null },
                durationMs = song.durationMs,
            )
            candidates += candidate
            if (candidate.wordSynced) break
        }
        return candidates.sortedByDescending { it.wordSynced }
    }

    private suspend fun searchSongs(track: Track): List<NetEaseSong> {
        val query = buildString {
            append(track.title.trim())
            val artist = track.artists.firstOrNull()?.trim().orEmpty()
            if (artist.isNotEmpty()) {
                append(' ')
                append(artist)
            }
        }
        val body = withCnSourceRequest(id) {
            val response = http.get(SEARCH_URL) {
                applyCnSourceDefaults(http)
                parameter("s", query)
                parameter("type", 1)
                parameter("limit", SEARCH_LIMIT)
            }
            if (response.status == HttpStatusCode.NotFound) return@withCnSourceRequest null
            if (!response.status.isSuccess()) {
                throw LyricsSourceException(id, "NetEase search failed with HTTP ${response.status.value}")
            }
            response.bodyAsText()
        } ?: return emptyList()

        val root = parseCnJsonObject(id, body)
        val code = root.intVal("code")
        if (code != null && code != 200 && code != 404) {
            throw LyricsSourceException(id, "NetEase search returned error code $code")
        }
        val songArray = root.obj("result")?.arr("songs") ?: return emptyList()
        return songArray.mapNotNull { element ->
            val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val songId = obj.longVal("id") ?: return@mapNotNull null
            val name = obj.str("name").orEmpty().trim()
            val durationMs = obj.longVal("duration")?.takeIf { it > 0L }
            val artists = obj.arr("artists").orEmpty().mapNotNull { artistEl ->
                artistEl.asJsonObjectOrNull()?.str("name")?.trim()?.takeIf { it.isNotEmpty() }
            }
            val aliases = obj.arr("alias").orEmpty().mapNotNull { aliasEl ->
                (aliasEl as? kotlinx.serialization.json.JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.content
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
            NetEaseSong(
                id = songId,
                name = name,
                artists = artists,
                aliases = aliases,
                durationMs = durationMs,
            )
        }
    }

    private suspend fun fetchSongLyrics(songId: Long): List<LyricLine>? {
        val body = withCnSourceRequest(id) {
            val response = http.get(LYRIC_URL) {
                applyCnSourceDefaults(http)
                parameter("id", songId)
                parameter("lv", 1)
                parameter("yv", 1)
            }
            if (response.status == HttpStatusCode.NotFound) return@withCnSourceRequest null
            if (!response.status.isSuccess()) {
                throw LyricsSourceException(id, "NetEase lyric fetch failed with HTTP ${response.status.value}")
            }
            response.bodyAsText()
        } ?: return null

        val root = parseCnJsonObject(id, body)
        val code = root.intVal("code")
        if (code != null && code != 200 && code != 404) {
            throw LyricsSourceException(id, "NetEase lyric fetch returned error code $code")
        }

        val yrcRaw = root.obj("yrc")?.str("lyric")
        if (!yrcRaw.isNullOrBlank()) {
            val yrcLines = parseYrc(yrcRaw).withoutCnCredits()
            if (yrcLines.hasSungLyrics()) return yrcLines
        }

        val lrcRaw = root.obj("lrc")?.str("lyric")
        if (!lrcRaw.isNullOrBlank()) {
            val lrcLines = LrcParser.parse(lrcRaw).withoutCnCredits().withCnDuetAgents()
            if (lrcLines.hasSungLyrics()) return lrcLines
        }
        return null
    }

    private data class NetEaseSong(
        val id: Long,
        val name: String,
        val artists: List<String>,
        val aliases: List<String>,
        val durationMs: Long?,
    )

    internal companion object {
        private const val SEARCH_URL = "https://music.163.com/api/search/get"
        private const val LYRIC_URL = "https://music.163.com/api/song/lyric"
        private const val SEARCH_LIMIT = 10

        private val YRC_LINE_REGEX = Regex("""^\[(\d+),(\d+)](.*)$""")
        private val YRC_WORD_REGEX = Regex("""\((\d+),(\d+),-?\d+\)(.*?)(?=\(\d+,\d+,-?\d+\)|$)""")

        /**
         * Parses NetEase's YRC word-timed format:
         * `[lineStartMs,lineDurationMs](wordStartMs,wordDurationMs,0)word ...`
         */
        internal fun parseYrc(yrc: String): List<LyricLine> {
            val offsetMs = parseOffsetTagMs(yrc)
            return yrc.lineSequence().mapNotNull { raw ->
                val match = YRC_LINE_REGEX.matchEntire(raw.trim()) ?: return@mapNotNull null
                val rawLineStart = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val lineDuration = match.groupValues[2].toLongOrNull() ?: 0L
                val body = match.groupValues[3]
                val lineStart = applyLyricOffset(rawLineStart, offsetMs)

                val words = YRC_WORD_REGEX.findAll(body).mapNotNull { wordMatch ->
                    val rawWordStart = wordMatch.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                    val wordDuration = wordMatch.groupValues[2].toLongOrNull() ?: 0L
                    val wordText = wordMatch.groupValues[3]
                    LyricWord(
                        startMs = applyLyricOffset(rawWordStart, offsetMs),
                        durationMs = wordDuration.coerceAtLeast(0L),
                        text = wordText,
                    )
                }.toList()

                buildTimedLyricLine(
                    startMs = lineStart,
                    durationMs = lineDuration,
                    rawWords = words,
                    fallbackText = if (words.isEmpty()) body else "",
                )
            }.sortedBy { it.startMs }.toList()
        }
    }
}
