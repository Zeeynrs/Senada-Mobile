package senada.catalog

import senada.app.AppLanguage
import senada.app.AppStrings
import senada.app.UiText
import senada.resources.Res
import senada.resources.playlist_link_empty
import senada.resources.playlist_link_spotify_invalid_id
import senada.resources.playlist_link_spotify_only_playlists
import senada.resources.playlist_link_spotify_short
import senada.resources.playlist_link_spotify_unsupported
import senada.resources.playlist_link_unknown
import senada.resources.playlist_link_yt_account
import senada.resources.playlist_link_yt_invalid_id
import senada.resources.playlist_link_yt_missing_id
import senada.resources.playlist_link_yt_mix
import senada.resources.playlist_link_yt_not_playlist

/** Tautan playlist yang siap dibaca dari YouTube Music atau Spotify. */
internal sealed interface PlaylistLink {
    data class YouTubeMusic(val playlistId: String) : PlaylistLink
    data class Spotify(val playlistId: String) : PlaylistLink
}

/** Hasil validasi tautan playlist dari kolom input. */
internal sealed interface PlaylistLinkParseResult {
    data class Valid(val link: PlaylistLink) : PlaylistLinkParseResult
    data class Invalid(val uiText: UiText) : PlaylistLinkParseResult {
        constructor(message: String) : this(UiText.Raw(message))

        fun message(language: AppLanguage = AppStrings.currentLanguage): String = uiText.resolve(language)

        val message: String get() = message()
    }
}

/** Kegagalan saat membaca playlist atau mencocokkannya dengan pesan sesuai bahasa aktif. */
internal open class PlaylistImportException(
    internal val uiText: UiText,
    cause: Throwable? = null,
) : Exception(uiText.resolve(), cause) {
    constructor(userMessage: String, cause: Throwable? = null) : this(UiText.Raw(userMessage), cause)

    open fun userMessage(language: AppLanguage = AppStrings.currentLanguage): String = uiText.resolve(language)

    open val userMessage: String get() = userMessage()
}

/**
 * Mengurai tautan playlist YouTube Music / YouTube (`list=`) dan Spotify (`open.spotify.com/playlist/...`
 * beserta awalan `intl-...`, atau `spotify:playlist:...`).
 */
internal fun parsePlaylistLink(input: String): PlaylistLinkParseResult {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return PlaylistLinkParseResult.Invalid(UiText.Resource(Res.string.playlist_link_empty))
    }

    val spotifyUri = SPOTIFY_URI_REGEX.find(trimmed)
    val spotifyUrl = if (spotifyUri == null) SPOTIFY_URL_REGEX.find(trimmed) else null
    val spotifyMatch = spotifyUri ?: spotifyUrl
    if (spotifyMatch != null) {
        val kind = spotifyMatch.groupValues[1].lowercase()
        val id = spotifyMatch.groupValues[2]
        if (kind != "playlist") {
            return PlaylistLinkParseResult.Invalid(
                UiText.Resource(Res.string.playlist_link_spotify_only_playlists),
            )
        }
        if (id.length != SPOTIFY_ID_LENGTH) {
            return PlaylistLinkParseResult.Invalid(
                UiText.Resource(Res.string.playlist_link_spotify_invalid_id),
            )
        }
        return PlaylistLinkParseResult.Valid(PlaylistLink.Spotify(id))
    }

    if (SPOTIFY_SHORT_LINK_REGEX.containsMatchIn(trimmed)) {
        return PlaylistLinkParseResult.Invalid(
            UiText.Resource(Res.string.playlist_link_spotify_short),
        )
    }
    if (SPOTIFY_DOMAIN_OR_SCHEME_REGEX.containsMatchIn(trimmed)) {
        return PlaylistLinkParseResult.Invalid(
            UiText.Resource(Res.string.playlist_link_spotify_unsupported),
        )
    }

    val ytMatch = YOUTUBE_URL_REGEX.find(trimmed)
    if (ytMatch != null) {
        return parseYouTubePlaylistUrl(ytMatch.groupValues[1])
    }

    return PlaylistLinkParseResult.Invalid(
        UiText.Resource(Res.string.playlist_link_unknown),
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
            return PlaylistLinkParseResult.Invalid(UiText.Resource(Res.string.playlist_link_yt_missing_id))
        }
        return validateYouTubePlaylistId(rawList)
    }

    val browseMatch = YOUTUBE_BROWSE_PLAYLIST_REGEX.matchEntire(path)
    if (browseMatch != null) {
        return validateYouTubePlaylistId(browseMatch.groupValues[1])
    }

    return PlaylistLinkParseResult.Invalid(
        UiText.Resource(Res.string.playlist_link_yt_not_playlist),
    )
}

private fun validateYouTubePlaylistId(rawId: String): PlaylistLinkParseResult {
    val normalized = if (rawId.startsWith("VL") && rawId.length > 2) rawId.substring(2) else rawId
    if (!YOUTUBE_PLAYLIST_ID_REGEX.matches(normalized)) {
        return PlaylistLinkParseResult.Invalid(UiText.Resource(Res.string.playlist_link_yt_invalid_id))
    }
    if (normalized.uppercase() in YT_ACCOUNT_LISTS) {
        return PlaylistLinkParseResult.Invalid(
            UiText.Resource(Res.string.playlist_link_yt_account),
        )
    }
    if (isYouTubeMixPlaylist(normalized)) {
        return PlaylistLinkParseResult.Invalid(
            UiText.Resource(Res.string.playlist_link_yt_mix),
        )
    }
    return PlaylistLinkParseResult.Valid(PlaylistLink.YouTubeMusic(normalized))
}

/** Mix/radio pribadi YouTube (`RD…` selain playlist editorial `RDCLAK…`) berubah per pendengar. */
internal fun isYouTubeMixPlaylist(playlistId: String): Boolean {
    val cleanId = playlistId.trim().removePrefix("VL")
    return cleanId.startsWith("RD", ignoreCase = true) &&
        !cleanId.startsWith("RDCLAK", ignoreCase = true)
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
