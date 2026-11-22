package senada.catalog.spotify

import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt
import senada.catalog.CatalogException
import senada.catalog.youtube.CandidateSearcher
import senada.catalog.youtube.MatchCandidate
import senada.model.Track

internal const val MATCHER_ACCEPT = 70
internal const val MATCHER_MINIMUM = 45

/** Skor kecocokan 0–100 antara lagu sumber (Spotify) dan kandidat YouTube Music (`senada/matching.py`). */
internal data class MatchScore(
    val score: Int,
    val title: Double,
    val artist: Double,
    val duration: Double,
    val tags: Set<String>,
    val disqualified: Boolean,
)

internal data class RankedCandidate(
    val candidate: MatchCandidate,
    val match: MatchScore,
)

/**
 * Mencocokkan satu lagu Spotify ke lagu YouTube Music terbaik.
 * Pertama mencari tab Lagu; bila belum meyakinkan (< [MATCHER_ACCEPT]), mencari tab Video juga.
 * Mengembalikan null bila tidak ada kandidat yang memenuhi ambang [MATCHER_MINIMUM].
 */
internal suspend fun matchSpotifyTrack(
    source: SpotifyTrack,
    searcher: CandidateSearcher,
): Track? {
    val query = searchQuery(source)
    if (query.isEmpty() || source.title.isBlank()) return null

    var songsError: CatalogException? = null
    var videosError: CatalogException? = null
    val combined = mutableListOf<MatchCandidate>()

    try {
        combined += searcher.search(query, includeVideos = false)
    } catch (e: CancellationException) {
        throw e
    } catch (e: CatalogException) {
        songsError = e
    }

    var ranked = rankCandidates(source, combined)
    val bestAfterSongs = ranked.firstOrNull()?.match?.score ?: 0
    if (bestAfterSongs < MATCHER_ACCEPT) {
        try {
            combined += searcher.search(query, includeVideos = true)
            ranked = rankCandidates(source, combined)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CatalogException) {
            videosError = e
        }
    }

    if (combined.isEmpty()) {
        (songsError ?: videosError)?.let { throw it }
        return null
    }

    val best = ranked.firstOrNull() ?: return null
    return if (best.match.score >= MATCHER_MINIMUM) best.candidate.track else null
}

internal fun searchQuery(source: SpotifyTrack): String {
    val primaryArtist = source.artists.firstOrNull { it.isNotBlank() }?.let(::foldText).orEmpty()
    var title = foldText(source.title)
    title = FEAT_REGEX.replace(title, " ")
    title = REMASTER_SUFFIX_REGEX.replace(title, "")
    title = REMASTER_BRACKET_REGEX.replace(title, " ")
    return MULTI_SPACE_REGEX.replace("$primaryArtist $title", " ").trim()
}

internal fun rankCandidates(
    source: SpotifyTrack,
    candidates: List<MatchCandidate>,
): List<RankedCandidate> {
    val seen = HashSet<String>()
    val scored = ArrayList<RankedCandidate>(candidates.size)
    for (candidate in candidates) {
        val id = candidate.track.id
        if (id.isEmpty() || !seen.add(id)) continue
        scored += RankedCandidate(candidate, scoreCandidate(source, candidate))
    }
    val srcDuration = source.durationMs?.takeIf { it > 0 }
    scored.sortWith { a, b ->
        val scoreCmp = b.match.score.compareTo(a.match.score)
        if (scoreCmp != 0) return@sortWith scoreCmp
        if (srcDuration != null) {
            val deltaA = a.candidate.track.durationMs?.takeIf { it > 0 }?.let { abs(it - srcDuration) } ?: Long.MAX_VALUE
            val deltaB = b.candidate.track.durationMs?.takeIf { it > 0 }?.let { abs(it - srcDuration) } ?: Long.MAX_VALUE
            val durCmp = deltaA.compareTo(deltaB)
            if (durCmp != 0) return@sortWith durCmp
        }
        0
    }
    return scored
}

internal fun scoreCandidate(source: SpotifyTrack, candidate: MatchCandidate): MatchScore {
    val srcTitle = source.title
    val srcArtists = splitArtistEntries(source.artists) + featuredArtists(srcTitle)
    val candTitle = candidate.track.title
    val candArtists = splitArtistEntries(candidate.track.artists) + featuredArtists(candTitle)

    val t = titleSimilarity(srcTitle, candTitle, candArtists)
    val a = artistSimilarity(srcArtists, candArtists, candTitle)
    val (d, disqualified) = durationPoints(source.durationMs, candidate.track.durationMs)
    val kind = TYPE_POINTS[candidate.videoType.orEmpty()] ?: 0

    val srcTags = versionTags(srcTitle)
    val candTags = versionTags("$candTitle ${candidate.album.orEmpty()}")
    var penalty = 0
    for (tag in candTags - srcTags) {
        penalty += if (tag in HEAVY_MISMATCH_TAGS) 40 else 30
    }
    for (tag in srcTags - candTags) {
        penalty += 18
    }
    if (a < 0.4) {
        penalty += 15
    }
    if (candidate.videoType == "MUSIC_VIDEO_TYPE_UGC" && LYRIC_REGEX.containsMatchIn(candTitle)) {
        penalty += 6
    }

    var total = 45.0 * t + 30.0 * a + d + kind - penalty
    if (disqualified) {
        total = minOf(total, (MATCHER_MINIMUM - 1).toDouble())
    }
    return MatchScore(
        score = total.roundToInt().coerceIn(0, 100),
        title = ((t * 1000.0).roundToInt()) / 1000.0,
        artist = ((a * 1000.0).roundToInt()) / 1000.0,
        duration = ((d * 10.0).roundToInt()) / 10.0,
        tags = candTags,
        disqualified = disqualified,
    )
}

internal fun foldText(text: String): String {
    if (text.isEmpty()) return ""
    val out = StringBuilder(text.length)
    for (ch in text) {
        if (ch in '\u0300'..'\u036F' || ch in '\u1AB0'..'\u1AFF' || ch in '\u1DC0'..'\u1DFF' ||
            ch in '\u20D0'..'\u20FF' || ch in '\uFE20'..'\uFE2F'
        ) {
            continue
        }
        val mapped = FOLD_CHAR_MAP[ch]
        if (mapped != null) {
            out.append(mapped)
        } else {
            out.append(ch.lowercaseChar())
        }
    }
    return out.toString()
}

private fun squash(text: String): String {
    if (text.isEmpty()) return ""
    val out = StringBuilder(text.length)
    var lastWasSpace = true
    for (ch in text) {
        if (ch.isLetterOrDigit() || ch == '_') {
            out.append(ch)
            lastWasSpace = false
        } else if (!lastWasSpace) {
            out.append(' ')
            lastWasSpace = true
        }
    }
    val end = if (out.isNotEmpty() && out.last() == ' ') out.length - 1 else out.length
    return out.substring(0, end)
}

internal fun normalizeText(text: String): String = squash(foldText(text))

internal fun versionTags(text: String): Set<String> {
    val folded = foldText(text)
    val out = LinkedHashSet<String>()
    for ((tag, regex) in VERSION_TAG_REGEXES) {
        if (regex.containsMatchIn(folded)) {
            out += tag
        }
    }
    return out
}

internal fun splitArtists(text: String): List<String> =
    ARTIST_SPLIT_REGEX.split(text)
        .map { squash(foldText(it)) }
        .filter { it.isNotEmpty() }

private fun splitArtistEntries(artists: List<String>): List<String> =
    artists.flatMap(::splitArtists)

internal fun featuredArtists(text: String): List<String> {
    val folded = foldText(text)
    val out = mutableListOf<String>()
    for (match in FEAT_REGEX.findAll(folded)) {
        val credit = match.groupValues[1].ifEmpty { match.groupValues[2] }
        if (credit.isNotEmpty()) {
            out += splitArtists(credit)
        }
    }
    return out
}

internal fun cleanTitle(text: String): String {
    var t = foldText(text)
    t = FEAT_REGEX.replace(t, " ")
    t = NOISE_REGEX.replace(t, " ")
    t = BRACKET_PUNCT_REGEX.replace(t, " ")
    return squash(t)
}

internal fun titleSimilarity(
    source: String,
    candidate: String,
    candidateArtists: List<String>,
): Double {
    val src = cleanTitle(source)
    val cand = cleanTitle(candidate)
    if (src.isEmpty() || cand.isEmpty()) return 0.0

    var stripped = cand
    for (name in candidateArtists) {
        if (name.isNotEmpty() && name in stripped && name != src) {
            stripped = squash(stripped.replace(name, " "))
        }
    }
    stripped = squash(stripped.replace(" - ", " ").trim('-', ' '))
    if (src == cand || src == stripped) return 1.0

    var best = maxOf(sequenceRatio(src, cand), sequenceRatio(src, stripped))
    val st = src.split(' ').filter { it.isNotEmpty() }.toSet()
    val ct = (stripped.ifEmpty { cand }).split(' ').filter { it.isNotEmpty() }.toSet()
    if (st.isNotEmpty()) {
        val covered = (st intersect ct).size.toDouble() / st.size
        val extra = (ct - st).size.toDouble() / maxOf(1, ct.size)
        best = maxOf(best, covered * (1.0 - 0.5 * extra))
    }
    val clamped = best.coerceAtMost(1.0)
    return clamped * clamped
}

internal fun artistSimilarity(
    sourceArtists: List<String>,
    candidateArtists: List<String>,
    candidateTitle: String,
): Double {
    if (sourceArtists.isEmpty()) return 0.5
    val titleWords = " ${squash(foldText(candidateTitle))} "
    var best = 0.0
    for ((i, src) in sourceArtists.withIndex()) {
        val weight = if (i == 0) 1.0 else 0.8
        for (cand in candidateArtists) {
            if (cand.isEmpty()) continue
            if (src == cand || (src.length > 3 && (src in cand || cand in src))) {
                best = maxOf(best, weight)
            } else {
                val r = sequenceRatio(src, cand)
                if (r >= 0.85) {
                    best = maxOf(best, weight * r)
                }
            }
        }
        if (src.length > 2 && " $src " in titleWords) {
            best = maxOf(best, weight * 0.9)
        }
    }
    return best
}

internal fun durationPoints(sourceMs: Long?, candidateMs: Long?): Pair<Double, Boolean> {
    if (sourceMs == null || sourceMs <= 0 || candidateMs == null || candidateMs <= 0) {
        return 8.0 to false
    }
    val delta = abs(sourceMs - candidateMs) / 1000.0
    if (delta <= 2.0) return 20.0 to false
    if (delta <= 15.0) return (20.0 * (1.0 - (delta - 2.0) / 13.0)) to false
    return 0.0 to (delta > 30.0)
}

/** Kesamaan dua teks (0..1) menggunakan blok cocok terpanjang ala Ratcliff/Obershelp (`difflib.SequenceMatcher.ratio`). */
internal fun sequenceRatio(a: String, b: String): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    if (a == b) return 1.0
    val matches = countMatchingChars(a, 0, a.length, b, 0, b.length)
    return (2.0 * matches) / (a.length + b.length)
}

private fun countMatchingChars(
    a: String,
    aStart: Int,
    aEnd: Int,
    b: String,
    bStart: Int,
    bEnd: Int,
): Int {
    val aLen = aEnd - aStart
    val bLen = bEnd - bStart
    if (aLen <= 0 || bLen <= 0) return 0

    var prev = IntArray(bLen + 1)
    var curr = IntArray(bLen + 1)
    var bestLen = 0
    var bestAEnd = 0
    var bestBEnd = 0

    for (i in 1..aLen) {
        val ca = a[aStart + i - 1]
        for (j in 1..bLen) {
            if (ca == b[bStart + j - 1]) {
                val len = prev[j - 1] + 1
                curr[j] = len
                if (len > bestLen) {
                    bestLen = len
                    bestAEnd = aStart + i
                    bestBEnd = bStart + j
                }
            } else {
                curr[j] = 0
            }
        }
        val tmp = prev
        prev = curr
        curr = tmp
        curr.fill(0)
    }

    if (bestLen == 0) return 0
    val bestAStart = bestAEnd - bestLen
    val bestBStart = bestBEnd - bestLen
    return bestLen +
        countMatchingChars(a, aStart, bestAStart, b, bStart, bestBStart) +
        countMatchingChars(a, bestAEnd, aEnd, b, bestBEnd, bEnd)
}

private val TYPE_POINTS = mapOf(
    "MUSIC_VIDEO_TYPE_ATV" to 5,
    "MUSIC_VIDEO_TYPE_OMV" to 2,
    "MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC" to 2,
)

private val HEAVY_MISMATCH_TAGS = setOf("karaoke", "cover", "instrumental")

private val VERSION_TAG_REGEXES = mapOf(
    "live" to Regex("""\blive\b|\blive at\b|\bkonser\b|\bconcert\b|\bunplugged\b|\blangsung\b""", RegexOption.IGNORE_CASE),
    "remix" to Regex("""\bremix(?:ed)?\b|\brmx\b|\bbootleg\b|\bmash ?up\b|\bflip\b|\bvip mix\b""", RegexOption.IGNORE_CASE),
    "acoustic" to Regex("""\bacoustic\b|\bakustik\b|\bunplugged\b""", RegexOption.IGNORE_CASE),
    "cover" to Regex("""\bcover(?:ed)?\b|\bcovered by\b|\btribute\b""", RegexOption.IGNORE_CASE),
    "karaoke" to Regex("""\bkaraoke\b|\bminus one\b|\bbacking track\b|\bsing ?along\b""", RegexOption.IGNORE_CASE),
    "instrumental" to Regex("""\binstrumental\b|\binstrumen\b|\bpiano version\b|\bpiano cover\b|\bguitar cover\b""", RegexOption.IGNORE_CASE),
    "slowed" to Regex("""\bslowed\b|\breverb\b""", RegexOption.IGNORE_CASE),
    "sped" to Regex("""\bsped ?up\b|\bspeed ?up\b|\bnightcore\b""", RegexOption.IGNORE_CASE),
    "8d" to Regex("""\b8d\b|\b16d\b""", RegexOption.IGNORE_CASE),
    "extended" to Regex("""\bextended\b|\bloop\b|\b1 hour\b|\b1 jam\b""", RegexOption.IGNORE_CASE),
    "demo" to Regex("""\bdemo\b""", RegexOption.IGNORE_CASE),
)

private val NOISE_REGEX = Regex(
    """\b(?:official|resmi)\s*(?:music\s*)?(?:video|audio|mv|lyric(?:s)?\s*video|visuali[sz]er|clip)\b""" +
        """|\b(?:music|lyric(?:s)?|video)\s*(?:video|klip|clip)\b""" +
        """|\bvideo\s*(?:musik|lirik|klip)\b""" +
        """|\b(?:with\s*)?lyrics?\b|\blirik(?:\s*lagu)?\b""" +
        """|\b(?:hd|hq|4k|1080p|720p|mv|m/v|audio|visuali[sz]er|explicit|clean|full\s*album)\b""" +
        """|\b(?:\d{4}\s*)?remaster(?:ed)?(?:\s*\d{4})?(?:\s*version)?\b""" +
        """|\bradio\s*edit\b|\balbum\s*version\b|\bsingle\s*version\b|\boriginal\s*mix\b""",
    RegexOption.IGNORE_CASE,
)

private val FEAT_REGEX = Regex(
    """[\(\[]\s*with\s+([^\)\]]+)[\)\]]|[\(\[]?\s*\b(?:feat\.?|ft\.?|featuring)\s+([^\)\]\-–|]+)[\)\]]?""",
    RegexOption.IGNORE_CASE,
)

private val LYRIC_REGEX = Regex("""\blyrics?\b|\blirik\b""", RegexOption.IGNORE_CASE)

private val ARTIST_SPLIT_REGEX = Regex(
    """\s*(?:,|&|\bx\b|\band\b|\bdan\b|\bfeat\.?|\bft\.?|\bfeaturing\b|\bwith\b|/|;)\s*""",
    RegexOption.IGNORE_CASE,
)

private val REMASTER_SUFFIX_REGEX = Regex(
    """\s*-\s*(?:\d{4}\s*)?remaster(?:ed)?.*$""",
    RegexOption.IGNORE_CASE,
)

private val REMASTER_BRACKET_REGEX = Regex(
    """[\(\[][^\)\]]*remaster[^\)\]]*[\)\]]""",
    RegexOption.IGNORE_CASE,
)

private val BRACKET_PUNCT_REGEX = Regex("""[\(\)\[\]\{\}|"]""")
private val MULTI_SPACE_REGEX = Regex("""\s+""")

private val FOLD_CHAR_MAP: Map<Char, String> = mapOf(
    '‐' to "-", '‑' to "-", '‒' to "-", '–' to "-", '—' to "-", '―' to "-", '−' to "-",
    '’' to "'", '‘' to "'", '`' to "'", '“' to "\"", '”' to "\"",
    'À' to "a", 'Á' to "a", 'Â' to "a", 'Ã' to "a", 'Ä' to "a", 'Å' to "a",
    'à' to "a", 'á' to "a", 'â' to "a", 'ã' to "a", 'ä' to "a", 'å' to "a",
    'Ā' to "a", 'ā' to "a", 'Ă' to "a", 'ă' to "a", 'Ą' to "a", 'ą' to "a",
    'Æ' to "ae", 'æ' to "ae",
    'Ç' to "c", 'ç' to "c", 'Ć' to "c", 'ć' to "c", 'Ĉ' to "c", 'ĉ' to "c", 'Ċ' to "c", 'ċ' to "c", 'Č' to "c", 'č' to "c",
    'Ð' to "d", 'ð' to "d", 'Ď' to "d", 'ď' to "d", 'Đ' to "d", 'đ' to "d",
    'È' to "e", 'É' to "e", 'Ê' to "e", 'Ë' to "e",
    'è' to "e", 'é' to "e", 'ê' to "e", 'ë' to "e",
    'Ē' to "e", 'ē' to "e", 'Ĕ' to "e", 'ĕ' to "e", 'Ė' to "e", 'ė' to "e", 'Ę' to "e", 'ę' to "e", 'Ě' to "e", 'ě' to "e",
    'Ĝ' to "g", 'ĝ' to "g", 'Ğ' to "g", 'ğ' to "g", 'Ġ' to "g", 'ġ' to "g", 'Ģ' to "g", 'ģ' to "g",
    'Ĥ' to "h", 'ĥ' to "h", 'Ħ' to "h", 'ħ' to "h",
    'Ì' to "i", 'Í' to "i", 'Î' to "i", 'Ï' to "i",
    'ì' to "i", 'í' to "i", 'î' to "i", 'ï' to "i",
    'Ĩ' to "i", 'ĩ' to "i", 'Ī' to "i", 'ī' to "i", 'Ĭ' to "i", 'ĭ' to "i", 'Į' to "i", 'į' to "i", 'İ' to "i", 'ı' to "i",
    'Ĵ' to "j", 'ĵ' to "j", 'Ķ' to "k", 'ķ' to "k",
    'Ĺ' to "l", 'ĺ' to "l", 'Ļ' to "l", 'ļ' to "l", 'Ľ' to "l", 'ľ' to "l", 'Ŀ' to "l", 'ŀ' to "l", 'Ł' to "l", 'ł' to "l",
    'Ñ' to "n", 'ñ' to "n", 'Ń' to "n", 'ń' to "n", 'Ņ' to "n", 'ņ' to "n", 'Ň' to "n", 'ň' to "n",
    'Ò' to "o", 'Ó' to "o", 'Ô' to "o", 'Õ' to "o", 'Ö' to "o", 'Ø' to "o",
    'ò' to "o", 'ó' to "o", 'ô' to "o", 'õ' to "o", 'ö' to "o", 'ø' to "o",
    'Ō' to "o", 'ō' to "o", 'Ŏ' to "o", 'ŏ' to "o", 'Ő' to "o", 'ő' to "o",
    'Œ' to "oe", 'œ' to "oe",
    'Ŕ' to "r", 'ŕ' to "r", 'Ŗ' to "r", 'ŗ' to "r", 'Ř' to "r", 'ř' to "r",
    'Ś' to "s", 'ś' to "s", 'Ŝ' to "s", 'ŝ' to "s", 'Ş' to "s", 'ş' to "s", 'Š' to "s", 'š' to "s", 'ß' to "ss",
    'Ţ' to "t", 'ţ' to "t", 'Ť' to "t", 'ť' to "t", 'Ŧ' to "t", 'ŧ' to "t",
    'Þ' to "th", 'þ' to "th",
    'Ù' to "u", 'Ú' to "u", 'Û' to "u", 'Ü' to "u",
    'ù' to "u", 'ú' to "u", 'û' to "u", 'ü' to "u",
    'Ũ' to "u", 'ũ' to "u", 'Ū' to "u", 'ū' to "u", 'Ŭ' to "u", 'ŭ' to "u", 'Ů' to "u", 'ů' to "u", 'Ű' to "u", 'ű' to "u", 'Ų' to "u", 'ų' to "u",
    'Ŵ' to "w", 'ŵ' to "w",
    'Ý' to "y", 'ý' to "y", 'Ÿ' to "y", 'ÿ' to "y", 'Ŷ' to "y", 'ŷ' to "y",
    'Ź' to "z", 'ź' to "z", 'Ż' to "z", 'ż' to "z", 'Ž' to "z", 'ž' to "z",
)
