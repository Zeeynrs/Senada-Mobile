package senada.catalog

import kotlinx.coroutines.CancellationException
import senada.local.LOCAL_FILE_SOURCE
import senada.model.Track

private const val DEMO_SOURCE = "demo"

private val LEADING_FEAT = Regex(
    """^[\(\[\s]*(?:feat\.?|ft\.?|featuring)\s+""",
    RegexOption.IGNORE_CASE,
)
private val TRAILING_FEAT = Regex(
    """[\(\[]?\s*\b(?:feat\.?|ft\.?|featuring)\b.*$""",
    RegexOption.IGNORE_CASE,
)
private val TRAILING_WITH_BRACKETS = Regex(
    """[\(\[]\s*with\s+[^\)\]]+[\)\]]\s*$""",
    RegexOption.IGNORE_CASE,
)
private val SPLIT_FEAT = Regex(
    """\s*[\(\[]?\s*\b(?:feat\.?|ft\.?|featuring)\s+""",
    RegexOption.IGNORE_CASE,
)

/** Melipat huruf beraksen dan tanda baca agar pencocokan nama artis tahan beda huruf besar/kecil dan diakritik. */
internal fun normalizeArtistName(raw: String): String {
    if (raw.isBlank()) return ""
    val out = StringBuilder(raw.length)
    var pendingSpace = false
    for (ch in raw) {
        // Buang tanda diakritik penggabung (NFD) seperti U+0301 pada "Beyonce\u0301".
        if (ch in '\u0300'..'\u036F') continue
        for (folded in foldLatinChar(ch.lowercaseChar())) {
            if (folded.isLetterOrDigit()) {
                if (pendingSpace && out.isNotEmpty()) out.append(' ')
                pendingSpace = false
                out.append(folded)
            } else {
                pendingSpace = true
            }
        }
    }
    return out.toString()
}

/** Menghapus sisa penanda "feat." / "ft." / "featuring" dari nama artis sebelum dicari atau dicocokkan. */
internal fun stripFeat(raw: String): String {
    val trimmed = raw.trim().trim('(', ')', '[', ']')
    if (trimmed.isEmpty()) return ""
    val withoutLeading = LEADING_FEAT.replace(trimmed, "").trim().trim('(', ')', '[', ']')
    val withoutTrailing = TRAILING_FEAT.replace(withoutLeading, "")
    val withoutWith = TRAILING_WITH_BRACKETS.replace(withoutTrailing, "")
    return withoutWith.trim().trim(',', '-', '–', '—', '(', ')', '[', ']').trim()
}

/**
 * Mengurai daftar pilihan nama artis dari [Track.artists] tanpa memecah koma atau `&` di dalam nama
 * tunggal (seperti "Tyler, The Creator" atau "Earth, Wind & Fire"), sambil membersihkan penanda `feat.`.
 */
internal fun artistChoicesFor(track: Track): List<String> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<String>()
    for (entry in track.artists) {
        val parts = SPLIT_FEAT.split(entry.trim())
        for (part in parts) {
            val clean = stripFeat(part)
            val norm = normalizeArtistName(clean)
            if (clean.isNotEmpty() && norm.isNotEmpty() && seen.add(norm)) {
                result += clean
            }
        }
    }
    return result
}

/** File audio impor lokal dan lagu contoh tidak memiliki halaman artis di YouTube Music. */
internal fun canOpenArtist(track: Track): Boolean =
    track.source != LOCAL_FILE_SOURCE && track.source != DEMO_SOURCE && artistChoicesFor(track).isNotEmpty()

/**
 * Memilih hasil pencarian artis yang namanya benar-benar cocok dengan [requestedName]
 * (tahan beda huruf besar/kecil, diakritik, dan sisa `feat.`), mendahulukan kecocokan persis.
 * Mengembalikan null bila tidak ada yang cocok agar tidak membuka halaman artis yang salah.
 */
internal fun matchArtist(requestedName: String, candidates: List<ArtistSummary>): ArtistSummary? {
    val cleaned = stripFeat(requestedName)
    val queryNorm = normalizeArtistName(cleaned)
    if (queryNorm.isEmpty()) return null

    val exactNormalized = candidates.filter { normalizeArtistName(it.name) == queryNorm }
    if (exactNormalized.isEmpty()) return null
    return exactNormalized.firstOrNull { it.name.trim().equals(cleaned, ignoreCase = true) }
        ?: exactNormalized.first()
}

/**
 * Indeks memori terbatas untuk menyimpan ID kanal YouTube Music dari hasil pencarian, halaman artis,
 * dan halaman album tanpa mengubah model [Track] maupun skema database.
 */
internal class ArtistIdIndex(private val maxEntries: Int = 500) {
    private val byTrackArtist = mutableMapOf<Pair<String, String>, String>()

    fun record(trackId: String, artistName: String, channelId: String) {
        if (trackId.isBlank() || channelId.isBlank()) return
        val norm = normalizeArtistName(stripFeat(artistName))
        if (norm.isEmpty()) return
        val key = trackId to norm
        byTrackArtist.remove(key)
        byTrackArtist[key] = channelId
        while (byTrackArtist.size > maxEntries) {
            val oldest = byTrackArtist.keys.firstOrNull() ?: break
            byTrackArtist.remove(oldest)
        }
    }

    fun channelIdFor(trackId: String, artistName: String): String? {
        val norm = normalizeArtistName(stripFeat(artistName))
        if (norm.isEmpty()) return null
        return byTrackArtist[trackId to norm]
    }
}

/** Hasil penyelesaian halaman artis dari sebuah lagu. */
internal sealed interface ArtistResolveResult {
    data class Found(val id: String, val name: String, val artworkUrl: String? = null) : ArtistResolveResult
    data object NotFound : ArtistResolveResult
    data class Error(val message: String) : ArtistResolveResult
}

/**
 * Menyelesaikan ID halaman artis untuk satu lagu: memakai ID kanal yang sudah diketahui dari konteks
 * bila ada, lalu mencari dengan filter Artis dan mencocokkan nama secara ketat bila belum diketahui.
 */
internal class ArtistResolver(
    private val catalog: CatalogBrowser,
    private val knownIds: ArtistIdIndex,
    private val maxNameCache: Int = 200,
) {
    private val byNormalizedName = mutableMapOf<String, ArtistSummary>()

    /** Mengembalikan artis tanpa panggilan jaringan bila ID-nya sudah diketahui di sesi ini. */
    fun knownArtist(track: Track, artistName: String): ArtistResolveResult.Found? {
        val cleaned = stripFeat(artistName)
        val norm = normalizeArtistName(cleaned)
        if (norm.isEmpty()) return null
        knownIds.channelIdFor(track.id, cleaned)?.let { channelId ->
            return ArtistResolveResult.Found(id = channelId, name = cleaned)
        }
        byNormalizedName[norm]?.let { cached ->
            return ArtistResolveResult.Found(id = cached.id, name = cached.name, artworkUrl = cached.artworkUrl)
        }
        return null
    }

    suspend fun resolve(track: Track, artistName: String): ArtistResolveResult {
        val cleaned = stripFeat(artistName)
        val norm = normalizeArtistName(cleaned)
        if (norm.isEmpty()) return ArtistResolveResult.NotFound
        knownArtist(track, cleaned)?.let { return it }

        return try {
            val page = catalog.searchArtists(cleaned)
            val matched = matchArtist(cleaned, page.items) ?: return ArtistResolveResult.NotFound
            cacheByName(norm, matched)
            ArtistResolveResult.Found(id = matched.id, name = matched.name, artworkUrl = matched.artworkUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ArtistResolveResult.Error((e as? CatalogException)?.userMessage ?: "Gagal mencari artis. Coba lagi.")
        }
    }

    private fun cacheByName(norm: String, artist: ArtistSummary) {
        byNormalizedName.remove(norm)
        byNormalizedName[norm] = artist
        while (byNormalizedName.size > maxNameCache) {
            val oldest = byNormalizedName.keys.firstOrNull() ?: break
            byNormalizedName.remove(oldest)
        }
    }
}

private fun foldLatinChar(ch: Char): String = when (ch) {
    'à', 'á', 'â', 'ã', 'ä', 'å', 'ā', 'ă', 'ą' -> "a"
    'ç', 'ć', 'ĉ', 'ċ', 'č' -> "c"
    'ď', 'đ', 'ð' -> "d"
    'è', 'é', 'ê', 'ë', 'ē', 'ĕ', 'ė', 'ę', 'ě' -> "e"
    'ĝ', 'ğ', 'ġ', 'ģ' -> "g"
    'ĥ', 'ħ' -> "h"
    'ì', 'í', 'î', 'ï', 'ĩ', 'ī', 'ĭ', 'į', 'ı' -> "i"
    'ĵ' -> "j"
    'ķ' -> "k"
    'ĺ', 'ļ', 'ľ', 'ŀ', 'ł' -> "l"
    'ñ', 'ń', 'ņ', 'ň' -> "n"
    'ò', 'ó', 'ô', 'õ', 'ö', 'ø', 'ō', 'ŏ', 'ő' -> "o"
    'ŕ', 'ŗ', 'ř' -> "r"
    'ś', 'ŝ', 'ş', 'š', 'ș' -> "s"
    'ţ', 'ť', 'ŧ', 'ț' -> "t"
    'ù', 'ú', 'û', 'ü', 'ũ', 'ū', 'ŭ', 'ů', 'ű', 'ų' -> "u"
    'ŵ' -> "w"
    'ý', 'ÿ', 'ŷ' -> "y"
    'ź', 'ż', 'ž' -> "z"
    'æ' -> "ae"
    'œ' -> "oe"
    'ß' -> "ss"
    '‐', '‑', '‒', '–', '—', '―', '−' -> "-"
    '’', '‘', '`' -> "'"
    else -> ch.toString()
}
