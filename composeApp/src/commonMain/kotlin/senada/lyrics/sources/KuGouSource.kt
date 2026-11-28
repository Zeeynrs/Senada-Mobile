// Portions adapted from Mochy Music (https://github.com/realidkroo/Mochy-Music, commit 3071023), GPL-3.0.
package senada.lyrics.sources

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlin.math.abs
import senada.lyrics.LrcParser
import senada.lyrics.LyricLine
import senada.lyrics.LyricWord
import senada.model.Track

/**
 * KuGou lyrics source.
 *
 * Searches songs via `mobileservice.kugou.com/api/v3/search/song`, finds lyric candidates via
 * `lyrics.kugou.com/search` (by audio hash, with keyword fallback), and downloads via
 * `lyrics.kugou.com/download` preferring word-timed KRC (base64 -> XOR -> zlib -> KRC) over LRC.
 */
internal class KuGouSource(private val http: HttpClient) : LyricsSource {
    override val id: String = "kugou"

    override suspend fun search(track: Track): List<LyricsCandidate> {
        if (track.title.isBlank()) return emptyList()
        val keyword = buildKeyword(track)
        val songs = searchSongs(keyword)
        val rankedSongs = rankCnSearchHits(
            track,
            songs.map { song ->
                CnSearchHit(
                    titles = listOfNotNull(song.title, song.otherName),
                    artists = song.artists,
                    durationMs = song.durationMs,
                    ref = song,
                )
            },
        )

        val lyricMatches = mutableListOf<Pair<KuGouLyricCandidate, KuGouSong?>>()
        val seenIds = hashSetOf<String>()
        for (hit in rankedSongs.take(MAX_SONG_HASH_LOOKUPS)) {
            val song = hit.ref
            val found = searchLyricsByHash(song.hash)
            for (cand in sortLyricCandidates(found, track.durationMs)) {
                if (seenIds.add("${cand.id}:${cand.accessKey}")) {
                    lyricMatches += cand to song
                }
            }
            if (lyricMatches.isNotEmpty()) break
        }

        if (lyricMatches.isEmpty()) {
            val byKeyword = searchLyricsByKeyword(keyword, track.durationMs)
            val rankedByKeyword = rankKeywordCandidates(track, byKeyword)
            for (cand in rankedByKeyword) {
                if (seenIds.add("${cand.id}:${cand.accessKey}")) {
                    lyricMatches += cand to null
                }
            }
        }

        if (lyricMatches.isEmpty()) return emptyList()

        val results = mutableListOf<LyricsCandidate>()
        for ((cand, song) in lyricMatches.take(CN_MAX_LYRIC_FETCH_ATTEMPTS)) {
            val lines = downloadAndParse(cand) ?: continue
            val candidateTitle = cand.song.ifBlank { song?.title.orEmpty() }.ifBlank { null }
            val candidateArtist = cand.singer.ifBlank { song?.artists?.joinToString(", ").orEmpty() }.ifBlank { null }
            val candidateDurationMs = cand.durationMs ?: song?.durationMs
            val result = LyricsCandidate(
                sourceId = id,
                lines = lines,
                wordSynced = hasWordSync(lines),
                title = candidateTitle,
                artist = candidateArtist,
                durationMs = candidateDurationMs,
            )
            results += result
            if (result.wordSynced) break
        }
        return results.sortedByDescending { it.wordSynced }
    }

    private suspend fun searchSongs(keyword: String): List<KuGouSong> {
        val body = withCnSourceRequest(id) {
            val response = http.get(SONG_SEARCH_URL) {
                applyCnSourceDefaults(http)
                parameter("version", 9108)
                parameter("plat", 0)
                parameter("pagesize", PAGE_SIZE)
                parameter("showtype", 0)
                url.encodedParameters.append("keyword", keyword.encodeURLParameter(spaceToPlus = false))
            }
            if (response.status == HttpStatusCode.NotFound) return@withCnSourceRequest null
            if (!response.status.isSuccess()) {
                throw LyricsSourceException(id, "KuGou song search failed with HTTP ${response.status.value}")
            }
            response.bodyAsText()
        } ?: return emptyList()

        val root = parseCnJsonObject(id, body)
        val infoArray = root.obj("data")?.arr("info") ?: return emptyList()
        return infoArray.mapNotNull { element ->
            val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val hash = obj.str("hash")?.trim().orEmpty()
            if (hash.isEmpty()) return@mapNotNull null
            val filename = obj.str("filename").orEmpty()
            val title = obj.str("songname")?.trim()?.takeIf { it.isNotEmpty() }
                ?: obj.str("songname_original")?.trim()?.takeIf { it.isNotEmpty() }
                ?: filename.substringAfter(" - ", "").trim()
            val otherName = obj.str("othername")?.trim()?.takeIf { it.isNotEmpty() }
            val singerRaw = obj.str("singername")?.trim()?.takeIf { it.isNotEmpty() }
                ?: filename.substringBefore(" - ", "").trim()
            val artists = splitArtists(singerRaw)
            val durationMs = obj.longVal("duration")?.takeIf { it > 0L }?.times(1_000L)
            KuGouSong(
                hash = hash,
                title = title,
                otherName = otherName,
                artists = artists,
                durationMs = durationMs,
            )
        }
    }

    private suspend fun searchLyricsByHash(hash: String): List<KuGouLyricCandidate> {
        val body = withCnSourceRequest(id) {
            val response = http.get(LYRICS_SEARCH_URL) {
                applyCnSourceDefaults(http)
                parameter("ver", 1)
                parameter("man", "yes")
                parameter("client", "pc")
                parameter("hash", hash)
            }
            if (response.status == HttpStatusCode.NotFound) return@withCnSourceRequest null
            if (!response.status.isSuccess()) {
                throw LyricsSourceException(id, "KuGou lyric search failed with HTTP ${response.status.value}")
            }
            response.bodyAsText()
        } ?: return emptyList()
        return parseLyricCandidates(body)
    }

    private suspend fun searchLyricsByKeyword(keyword: String, durationMs: Long?): List<KuGouLyricCandidate> {
        val body = withCnSourceRequest(id) {
            val response = http.get(LYRICS_SEARCH_URL) {
                applyCnSourceDefaults(http)
                parameter("ver", 1)
                parameter("man", "yes")
                parameter("client", "pc")
                durationMs?.takeIf { it > 0L }?.let { parameter("duration", it) }
                url.encodedParameters.append("keyword", keyword.encodeURLParameter(spaceToPlus = false))
            }
            if (response.status == HttpStatusCode.NotFound) return@withCnSourceRequest null
            if (!response.status.isSuccess()) {
                throw LyricsSourceException(id, "KuGou lyric search failed with HTTP ${response.status.value}")
            }
            response.bodyAsText()
        } ?: return emptyList()
        return parseLyricCandidates(body)
    }

    private fun parseLyricCandidates(body: String): List<KuGouLyricCandidate> {
        val root = parseCnJsonObject(id, body)
        val arr = root.arr("candidates") ?: return emptyList()
        return arr.mapNotNull { element ->
            val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val candId = obj.strOrNumber("id")?.trim().orEmpty()
            val accessKey = obj.str("accesskey")?.trim().orEmpty()
            if (candId.isEmpty() || accessKey.isEmpty()) return@mapNotNull null
            KuGouLyricCandidate(
                id = candId,
                accessKey = accessKey,
                song = obj.str("song").orEmpty().trim(),
                singer = obj.str("singer").orEmpty().trim(),
                durationMs = obj.longVal("duration")?.takeIf { it > 0L },
                score = obj.intVal("score") ?: 0,
                krcType = obj.intVal("krctype") ?: 0,
            )
        }
    }

    private fun rankKeywordCandidates(
        track: Track,
        candidates: List<KuGouLyricCandidate>,
    ): List<KuGouLyricCandidate> {
        val withTitles = candidates.filter { it.song.isNotBlank() }
        if (withTitles.isEmpty()) return sortLyricCandidates(candidates, track.durationMs)
        val ranked = rankCnSearchHits(
            track,
            withTitles.map { cand ->
                CnSearchHit(
                    titles = listOf(cand.song),
                    artists = splitArtists(cand.singer),
                    durationMs = cand.durationMs,
                    ref = cand,
                )
            },
        )
        return ranked.map { it.ref }
    }

    private fun sortLyricCandidates(
        candidates: List<KuGouLyricCandidate>,
        trackDurationMs: Long?,
    ): List<KuGouLyricCandidate> = candidates.sortedWith(
        compareByDescending<KuGouLyricCandidate> { it.krcType == 1 }
            .thenByDescending { it.score }
            .thenBy { cand ->
                val d = cand.durationMs
                if (trackDurationMs != null && trackDurationMs > 0L && d != null) {
                    abs(d - trackDurationMs)
                } else {
                    0L
                }
            },
    )

    private suspend fun downloadAndParse(candidate: KuGouLyricCandidate): List<LyricLine>? {
        val krcContent = downloadContent(candidate, format = "krc")
        if (!krcContent.isNullOrBlank()) {
            val decoded = decryptKrc(krcContent)
            if (decoded != null) {
                val krcLines = parseKrc(decoded).withoutCnCredits()
                if (krcLines.hasSungLyrics()) return krcLines
                val lrcLines = LrcParser.parse(decoded).withoutCnCredits().withCnDuetAgents()
                if (lrcLines.hasSungLyrics()) return lrcLines
            } else {
                // Some KuGou responses return plain LRC even when KRC is requested.
                val plainBytes = decodeBase64OrNull(krcContent)
                if (plainBytes != null && !hasKrcMagic(plainBytes)) {
                    val plainText = plainBytes.decodeToString().removePrefix("\uFEFF")
                    val lrcLines = LrcParser.parse(plainText).withoutCnCredits().withCnDuetAgents()
                    if (lrcLines.hasSungLyrics()) return lrcLines
                }
            }
        }

        val lrcContent = downloadContent(candidate, format = "lrc")
        if (lrcContent.isNullOrBlank()) return null
        val decodedBytes = decodeBase64OrNull(lrcContent) ?: return null
        val lrcText = decodedBytes.decodeToString().removePrefix("\uFEFF")
        val lrcLines = LrcParser.parse(lrcText).withoutCnCredits().withCnDuetAgents()
        return lrcLines.takeIf { it.hasSungLyrics() }
    }

    private suspend fun downloadContent(candidate: KuGouLyricCandidate, format: String): String? {
        val body = withCnSourceRequest(id) {
            val response = http.get(LYRICS_DOWNLOAD_URL) {
                applyCnSourceDefaults(http)
                parameter("ver", 1)
                parameter("client", "pc")
                parameter("charset", "utf8")
                parameter("fmt", format)
                parameter("id", candidate.id)
                parameter("accesskey", candidate.accessKey)
            }
            if (response.status == HttpStatusCode.NotFound) return@withCnSourceRequest null
            if (!response.status.isSuccess()) {
                throw LyricsSourceException(id, "KuGou $format download failed with HTTP ${response.status.value}")
            }
            response.bodyAsText()
        } ?: return null

        val root = parseCnJsonObject(id, body)
        return root.str("content")?.takeIf { it.isNotBlank() }
    }

    private fun buildKeyword(track: Track): String {
        val title = normalizeKuGouTitle(track.title).ifEmpty { track.title.trim() }
        val artist = track.artists.firstOrNull()?.let(::normalizeKuGouArtist).orEmpty()
        return if (artist.isNotEmpty()) "$title - $artist" else title
    }

    private fun normalizeKuGouTitle(title: String): String =
        TITLE_BRACKET_REGEX.replace(title, "").trim()

    private fun normalizeKuGouArtist(artist: String): String =
        ARTIST_BRACKET_REGEX.replace(
            artist.replace(", ", "、").replace(" & ", "、").replace(".", "").replace("和", "、"),
            "",
        ).trim()

    private fun splitArtists(raw: String): List<String> =
        raw.split("、", ",", "&", "/").map { it.trim() }.filter { it.isNotEmpty() }

    private data class KuGouSong(
        val hash: String,
        val title: String,
        val otherName: String?,
        val artists: List<String>,
        val durationMs: Long?,
    )

    private data class KuGouLyricCandidate(
        val id: String,
        val accessKey: String,
        val song: String,
        val singer: String,
        val durationMs: Long?,
        val score: Int,
        val krcType: Int,
    )

    internal companion object {
        private const val SONG_SEARCH_URL = "https://mobileservice.kugou.com/api/v3/search/song"
        private const val LYRICS_SEARCH_URL = "https://lyrics.kugou.com/search"
        private const val LYRICS_DOWNLOAD_URL = "https://lyrics.kugou.com/download"
        private const val PAGE_SIZE = 8
        private const val MAX_SONG_HASH_LOOKUPS = 2

        private val KRC_MAGIC = byteArrayOf(0x6B, 0x72, 0x63, 0x31) // "krc1"

        private val KRC_XOR_KEY = byteArrayOf(
            0x40, 0x47, 0x61, 0x77, 0x5E, 0x32, 0x74, 0x47,
            0x51, 0x36, 0x31, 0x2D, 0xCE.toByte(), 0xD2.toByte(), 0x6E, 0x69,
        )

        private val TITLE_BRACKET_REGEX = Regex("""\(.*\)|（.*）|「.*」|『.*』|<.*>|《.*》|〈.*〉|＜.*＞""")
        private val ARTIST_BRACKET_REGEX = Regex("""\(.*\)|（.*）""")

        private val KRC_LINE_REGEX = Regex("""^\[(\d+),(\d+)](.*)$""")
        private val KRC_WORD_REGEX = Regex("""<(\d+),(\d+)(?:,-?\d+)?>(.*?)(?=<\d+,\d+(?:,-?\d+)?>|$)""")

        internal fun hasKrcMagic(bytes: ByteArray): Boolean =
            bytes.size >= 4 &&
                bytes[0] == KRC_MAGIC[0] &&
                bytes[1] == KRC_MAGIC[1] &&
                bytes[2] == KRC_MAGIC[2] &&
                bytes[3] == KRC_MAGIC[3]

        /**
         * Decodes KuGou's KRC payload: base64 -> strip 4-byte `krc1` header -> 16-byte XOR -> zlib inflate.
         * Returns null if the payload is not valid KRC.
         */
        internal fun decryptKrc(base64Content: String): String? {
            val raw = decodeBase64OrNull(base64Content) ?: return null
            if (!hasKrcMagic(raw) || raw.size <= 4) return null
            val payloadLen = raw.size - 4
            val xored = ByteArray(payloadLen)
            for (i in 0 until payloadLen) {
                xored[i] = (raw[i + 4].toInt() xor KRC_XOR_KEY[i % KRC_XOR_KEY.size].toInt()).toByte()
            }
            val inflated = runCatching { ZlibInflater.inflate(xored) }.getOrNull() ?: return null
            return inflated.decodeToString().removePrefix("\uFEFF")
        }

        /**
         * Parses decrypted KuGou KRC text:
         * `[lineStartMs,lineDurationMs]<wordOffsetMs,wordDurationMs,0>word...`
         *
         * Note that KRC word offsets are relative to the line's start time.
         */
        internal fun parseKrc(krc: String): List<LyricLine> {
            val offsetMs = parseOffsetTagMs(krc)
            return krc.lineSequence().mapNotNull { raw ->
                val match = KRC_LINE_REGEX.matchEntire(raw.trim()) ?: return@mapNotNull null
                val rawLineStart = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val lineDuration = match.groupValues[2].toLongOrNull() ?: 0L
                val body = match.groupValues[3]
                val lineStart = applyLyricOffset(rawLineStart, offsetMs)

                val words = KRC_WORD_REGEX.findAll(body).mapNotNull { wordMatch ->
                    val wordOffset = wordMatch.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                    val wordDuration = wordMatch.groupValues[2].toLongOrNull() ?: 0L
                    val wordText = wordMatch.groupValues[3]
                    LyricWord(
                        startMs = applyLyricOffset(rawLineStart + wordOffset, offsetMs),
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
