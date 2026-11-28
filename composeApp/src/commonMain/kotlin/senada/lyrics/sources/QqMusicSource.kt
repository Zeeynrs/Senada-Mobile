// Portions adapted from Mochy Music (https://github.com/realidkroo/Mochy-Music, commit 3071023), GPL-3.0.
package senada.lyrics.sources

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import senada.lyrics.LrcParser
import senada.lyrics.LyricLine
import senada.lyrics.LyricWord
import senada.model.Track

/**
 * QQ Music lyrics source.
 *
 * Searches songs via `u.y.qq.com/cgi-bin/musicu.fcg` (`DoSearchForQQMusicDesktop`)
 * and fetches lyrics via `GetPlayLyricInfo`, preferring decrypted word-timed QRC
 * over base64 line-timed LRC.
 */
internal class QqMusicSource(private val http: HttpClient) : LyricsSource {
    override val id: String = "qqmusic"

    override suspend fun search(track: Track): List<LyricsCandidate> {
        if (track.title.isBlank()) return emptyList()
        val songs = searchSongs(track)
        if (songs.isEmpty()) return emptyList()

        val ranked = rankCnSearchHits(
            track,
            songs.map { song ->
                CnSearchHit(
                    titles = listOfNotNull(song.name, song.subtitle),
                    artists = song.singers,
                    durationMs = song.durationMs,
                    ref = song,
                )
            },
        )
        if (ranked.isEmpty()) return emptyList()

        val candidates = mutableListOf<LyricsCandidate>()
        for (hit in ranked.take(CN_MAX_LYRIC_FETCH_ATTEMPTS)) {
            val song = hit.ref
            val lines = fetchSongLyrics(song) ?: continue
            val candidate = LyricsCandidate(
                sourceId = id,
                lines = lines,
                wordSynced = hasWordSync(lines),
                title = song.name.ifBlank { null },
                artist = song.singers.joinToString(", ").ifBlank { null },
                durationMs = song.durationMs,
            )
            candidates += candidate
            if (candidate.wordSynced) break
        }
        return candidates.sortedByDescending { it.wordSynced }
    }

    private suspend fun searchSongs(track: Track): List<QqSong> {
        val query = buildString {
            append(track.title.trim())
            val artist = track.artists.firstOrNull()?.trim().orEmpty()
            if (artist.isNotEmpty()) {
                append(' ')
                append(artist)
            }
        }
        val root = musicu(
            module = "music.search.SearchCgiService",
            method = "DoSearchForQQMusicDesktop",
            param = buildJsonObject {
                put("query", query)
                put("num_per_page", SEARCH_LIMIT)
                put("page_num", 1)
                put("search_type", 0)
            },
        ) ?: return emptyList()

        val list = root.obj("req")?.obj("data")?.obj("body")?.obj("song")?.arr("list")
            ?: return emptyList()
        return list.mapNotNull { element ->
            val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val songId = obj.longVal("id") ?: return@mapNotNull null
            val songMid = obj.str("mid").orEmpty().trim()
            if (songMid.isEmpty()) return@mapNotNull null
            val name = (obj.str("name") ?: obj.str("title")).orEmpty().trim()
            val subtitle = obj.str("subtitle")?.trim()?.takeIf { it.isNotEmpty() }
            val intervalSec = obj.longVal("interval")?.takeIf { it > 0L }
            val singers = obj.arr("singer").orEmpty().mapNotNull { singerEl ->
                singerEl.asJsonObjectOrNull()?.str("name")?.trim()?.takeIf { it.isNotEmpty() }
            }
            QqSong(
                id = songId,
                mid = songMid,
                name = name,
                subtitle = subtitle,
                singers = singers,
                durationMs = intervalSec?.times(1_000L),
            )
        }
    }

    private suspend fun fetchSongLyrics(song: QqSong): List<LyricLine>? {
        val qrcRoot = musicu(
            module = "music.musichallSong.PlayLyricInfo",
            method = "GetPlayLyricInfo",
            param = buildJsonObject {
                put("songID", song.id)
                put("songMID", song.mid)
                put("qrc", 1)
                put("trans", 0)
                put("roma", 0)
                put("crypt", 1)
                put("lrc_t", 0)
                put("qrc_t", 0)
                put("roma_t", 0)
                put("trans_t", 0)
            },
        )
        val qrcRaw = qrcRoot?.obj("req")?.obj("data")?.str("lyric")
        if (!qrcRaw.isNullOrBlank()) {
            decodeQqLyricPayload(qrcRaw)?.let { return it }
        }

        // Fall back to plain base64 LRC when encrypted QRC is unavailable for this track.
        val lrcRoot = musicu(
            module = "music.musichallSong.PlayLyricInfo",
            method = "GetPlayLyricInfo",
            param = buildJsonObject {
                put("songMID", song.mid)
                put("songID", song.id)
            },
        ) ?: return null
        val lrcEncoded = lrcRoot.obj("req")?.obj("data")?.str("lyric")
        if (lrcEncoded.isNullOrBlank()) return null
        return decodeQqLyricPayload(lrcEncoded)
    }

    private suspend fun musicu(module: String, method: String, param: JsonObject): JsonObject? {
        val payload = buildJsonObject {
            put(
                "comm",
                buildJsonObject {
                    put("ct", "19")
                    put("cv", "1859")
                    put("uin", "0")
                },
            )
            put(
                "req",
                buildJsonObject {
                    put("module", module)
                    put("method", method)
                    put("param", param)
                },
            )
        }
        val body = withCnSourceRequest(id) {
            val response = http.post(MUSICU_URL) {
                applyCnSourceDefaults(http)
                setBody(TextContent(payload.toString(), ContentType.Application.Json))
            }
            if (response.status == HttpStatusCode.NotFound) return@withCnSourceRequest null
            if (!response.status.isSuccess()) {
                throw LyricsSourceException(id, "QQ Music request failed with HTTP ${response.status.value}")
            }
            response.bodyAsText()
        } ?: return null

        val root = parseCnJsonObject(id, body)
        val topCode = root.intVal("code")
        if (topCode != null && topCode != 0 && root.obj("req") == null) {
            throw LyricsSourceException(id, "QQ Music returned error code $topCode")
        }
        return root
    }

    private fun decodeQqLyricPayload(raw: String): List<LyricLine>? {
        val decrypted = QqQrcCrypto.decryptQrc(raw)
        if (decrypted != null) {
            val content = QqQrcCrypto.extractLyricContent(decrypted)
            val qrcLines = parseQrc(content).withoutCnCredits()
            if (qrcLines.hasSungLyrics()) return qrcLines

            val lrcLines = LrcParser.parse(content).withoutCnCredits().withCnDuetAgents()
            if (lrcLines.hasSungLyrics()) return lrcLines
        }

        val decodedBytes = decodeBase64OrNull(raw) ?: return null
        val decodedText = decodedBytes.decodeToString().removePrefix("\uFEFF")
        if (decodedText.isBlank()) return null

        val content = QqQrcCrypto.extractLyricContent(decodedText)
        val qrcLines = parseQrc(content).withoutCnCredits()
        if (qrcLines.hasSungLyrics()) return qrcLines

        val lrcLines = LrcParser.parse(content).withoutCnCredits().withCnDuetAgents()
        return lrcLines.takeIf { it.hasSungLyrics() }
    }

    private data class QqSong(
        val id: Long,
        val mid: String,
        val name: String,
        val subtitle: String?,
        val singers: List<String>,
        val durationMs: Long?,
    )

    internal companion object {
        private const val MUSICU_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        private const val SEARCH_LIMIT = 10

        private val QRC_LINE_REGEX = Regex("""^\[(\d+),(\d+)](.*)$""")
        private val QRC_WORD_REGEX = Regex("""(.*?)\((\d+),(\d+)(?:,-?\d+)?\)""")

        /**
         * Parses QQ Music's QRC `LyricContent` format:
         * `[lineStartMs,lineDurationMs]word1 (wordStartMs,wordDurationMs)word2(wordStartMs,wordDurationMs)`
         */
        internal fun parseQrc(qrcContent: String): List<LyricLine> {
            val offsetMs = parseOffsetTagMs(qrcContent)
            return qrcContent.lineSequence().mapNotNull { raw ->
                val match = QRC_LINE_REGEX.matchEntire(raw.trim()) ?: return@mapNotNull null
                val rawLineStart = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val lineDuration = match.groupValues[2].toLongOrNull() ?: 0L
                val body = match.groupValues[3]
                val lineStart = applyLyricOffset(rawLineStart, offsetMs)

                val matches = QRC_WORD_REGEX.findAll(body).toList()
                val words = matches.mapNotNull { wordMatch ->
                    val wordText = wordMatch.groupValues[1]
                    val rawWordStart = wordMatch.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                    val wordDuration = wordMatch.groupValues[3].toLongOrNull() ?: 0L
                    LyricWord(
                        startMs = applyLyricOffset(rawWordStart, offsetMs),
                        durationMs = wordDuration.coerceAtLeast(0L),
                        text = wordText,
                    )
                }.toMutableList()
                if (matches.isNotEmpty() && words.isNotEmpty()) {
                    val tailStart = matches.last().range.last + 1
                    if (tailStart < body.length) {
                        val tail = body.substring(tailStart)
                        if (tail.isNotBlank()) {
                            val last = words.last()
                            words[words.lastIndex] = last.copy(text = last.text + tail)
                        }
                    }
                }

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
