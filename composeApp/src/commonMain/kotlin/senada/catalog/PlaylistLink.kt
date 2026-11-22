package senada.catalog

/** Tautan playlist yang siap dibaca dari YouTube Music atau Spotify. */
internal sealed interface PlaylistLink {
    data class YouTubeMusic(val playlistId: String) : PlaylistLink
    data class Spotify(val playlistId: String) : PlaylistLink
}

/** Hasil validasi tautan playlist dari kolom input. */
internal sealed interface PlaylistLinkParseResult {
    data class Valid(val link: PlaylistLink) : PlaylistLinkParseResult
    data class Invalid(val message: String) : PlaylistLinkParseResult
}

/** Kegagalan saat membaca playlist atau mencocokkannya dengan pesan Bahasa Indonesia untuk pengguna. */
internal open class PlaylistImportException(val userMessage: String, cause: Throwable? = null) :
    Exception(userMessage, cause)

/**
 * Mengurai tautan playlist YouTube Music / YouTube (`list=`) dan Spotify (`open.spotify.com/playlist/...`
 * beserta awalan `intl-...`, atau `spotify:playlist:...`).
 */
internal fun parsePlaylistLink(input: String): PlaylistLinkParseResult {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return PlaylistLinkParseResult.Invalid("Tempel tautan playlist YouTube Music atau Spotify terlebih dahulu.")
    }

    val spotifyUri = SPOTIFY_URI_REGEX.find(trimmed)
    val spotifyUrl = if (spotifyUri == null) SPOTIFY_URL_REGEX.find(trimmed) else null
    val spotifyMatch = spotifyUri ?: spotifyUrl
    if (spotifyMatch != null) {
        val kind = spotifyMatch.groupValues[1].lowercase()
        val id = spotifyMatch.groupValues[2]
        if (kind != "playlist") {
            return PlaylistLinkParseResult.Invalid(
                "Saat ini hanya playlist Spotify yang didukung. Tempel tautan playlist Spotify.",
            )
        }
        if (id.length != SPOTIFY_ID_LENGTH) {
            return PlaylistLinkParseResult.Invalid(
                "ID playlist Spotify tidak valid. Pastikan seluruh tautan disalin.",
            )
        }
        return PlaylistLinkParseResult.Valid(PlaylistLink.Spotify(id))
    }

    if (SPOTIFY_SHORT_LINK_REGEX.containsMatchIn(trimmed)) {
        return PlaylistLinkParseResult.Invalid(
            "Tautan singkat Spotify belum didukung. Buka tautan tersebut lalu salin tautan open.spotify.com/playlist/….",
        )
    }
    if (SPOTIFY_DOMAIN_OR_SCHEME_REGEX.containsMatchIn(trimmed)) {
        return PlaylistLinkParseResult.Invalid(
            "Tautan Spotify ini bukan playlist yang didukung. Gunakan tautan open.spotify.com/playlist/… atau spotify:playlist:….",
        )
    }

    val ytMatch = YOUTUBE_URL_REGEX.find(trimmed)
    if (ytMatch != null) {
        return parseYouTubePlaylistUrl(ytMatch.groupValues[1])
    }

    return PlaylistLinkParseResult.Invalid(
        "Tautan tidak dikenali. Gunakan tautan playlist YouTube Music atau Spotify.",
    )
}

private fun parseYouTubePlaylistUrl(rawUrl: String): PlaylistLinkParseResult {
    val noFragment = rawUrl.substringBefore('#')
    val pathPart = noFragment.substringBefore('?')
    val queryPart = noFragment.substringAfter('?', "")
    val withoutScheme = pathPart.substringAfter("://", pathPart)
    val path = if ('/' in withoutScheme) "/" + withoutScheme.substringAfter('/') else "/"
    val queryParams = parseQueryParams(queryPart)

    if ("list" in queryParams) {
        val rawList = queryParams["list"].orEmpty()
        if (rawList.isBlank()) {
            return PlaylistLinkParseResult.Invalid("ID playlist YouTube tidak ditemukan pada tautan ini.")
        }
        return validateYouTubePlaylistId(rawList)
    }

    val browseMatch = YOUTUBE_BROWSE_PLAYLIST_REGEX.matchEntire(path)
    if (browseMatch != null) {
        return validateYouTubePlaylistId(browseMatch.groupValues[1])
    }

    return PlaylistLinkParseResult.Invalid(
        "Tautan YouTube ini bukan playlist. Tempel tautan playlist yang memiliki parameter list=.",
    )
}

private fun validateYouTubePlaylistId(rawId: String): PlaylistLinkParseResult {
    val normalized = if (rawId.startsWith("VL") && rawId.length > 2) rawId.substring(2) else rawId
    if (!YOUTUBE_PLAYLIST_ID_REGEX.matches(normalized)) {
        return PlaylistLinkParseResult.Invalid("ID playlist YouTube tidak valid.")
    }
    if (normalized.uppercase() in YT_ACCOUNT_LISTS) {
        return PlaylistLinkParseResult.Invalid(
            "Daftar akun YouTube (seperti Lagu yang disukai atau Tonton nanti) memerlukan login. " +
                "Salin lagu ke playlist publik atau tidak terdaftar, lalu impor tautannya.",
        )
    }
    if (normalized.startsWith("RD", ignoreCase = true) && !normalized.startsWith("RDCLAK", ignoreCase = true)) {
        return PlaylistLinkParseResult.Invalid(
            "Mix YouTube tidak dapat diimpor karena berubah untuk setiap pendengar. Simpan sebagai playlist terlebih dahulu.",
        )
    }
    return PlaylistLinkParseResult.Valid(PlaylistLink.YouTubeMusic(normalized))
}

private fun parseQueryParams(query: String): Map<String, String> {
    if (query.isEmpty()) return emptyMap()
    val out = LinkedHashMap<String, String>()
    for (pair in query.split('&')) {
        if (pair.isEmpty()) continue
        val eq = pair.indexOf('=')
        val key = percentDecode(if (eq >= 0) pair.substring(0, eq) else pair)
        val value = percentDecode(if (eq >= 0) pair.substring(eq + 1) else "")
        out[key] = value
    }
    return out
}

private fun percentDecode(input: String): String {
    if ('%' !in input) return input
    val bytes = ArrayList<Byte>(input.length)
    var i = 0
    while (i < input.length) {
        val ch = input[i]
        if (ch == '%' && i + 2 < input.length) {
            val hi = input[i + 1].digitToIntOrNull(16)
            val lo = input[i + 2].digitToIntOrNull(16)
            if (hi != null && lo != null) {
                bytes.add(((hi shl 4) or lo).toByte())
                i += 3
                continue
            }
        }
        bytes.addAll(ch.toString().encodeToByteArray().toList())
        i++
    }
    return bytes.toByteArray().decodeToString()
}

private const val SPOTIFY_ID_LENGTH = 22
private val YT_ACCOUNT_LISTS = setOf("LM", "LL", "WL", "SE")

private val SPOTIFY_URI_REGEX = Regex(
    """(?:^|\s)spotify:(?:user:[^:\s]+:)?(playlist|album|track|artist|episode|show):([A-Za-z0-9]+)(?=$|[\s?#])""",
    RegexOption.IGNORE_CASE,
)
private val SPOTIFY_URL_REGEX = Regex(
    """(?:^|\s)(?:https?://)?open\.spotify\.com/(?:intl-[a-zA-Z0-9-]+/)?(?:embed/)?(?:user/[^/\s]+/)?(playlist|album|track|artist|episode|show)/([A-Za-z0-9]+)(?=$|[/\s?#])""",
    RegexOption.IGNORE_CASE,
)
private val SPOTIFY_SHORT_LINK_REGEX = Regex(
    """(?:^|\s)(?:https?://)?(?:spotify\.link|spoti\.fi)/[^\s]+""",
    RegexOption.IGNORE_CASE,
)
private val SPOTIFY_DOMAIN_OR_SCHEME_REGEX = Regex(
    """(?:^|\s)(?:(?:https?://)?open\.spotify\.com(?:/|$|\s)|spotify:)""",
    RegexOption.IGNORE_CASE,
)
private val YOUTUBE_URL_REGEX = Regex(
    """(?:^|\s)((?:https?://)?(?:music\.youtube\.com|www\.youtube\.com|youtube\.com|m\.youtube\.com|youtu\.be)(?:/[^\s]*|\?[^\s]*))""",
    RegexOption.IGNORE_CASE,
)
private val YOUTUBE_BROWSE_PLAYLIST_REGEX = Regex("""^/browse/VL([A-Za-z0-9_-]{2,})/?$""")
private val YOUTUBE_PLAYLIST_ID_REGEX = Regex("""^[A-Za-z0-9_-]{2,}$""")
