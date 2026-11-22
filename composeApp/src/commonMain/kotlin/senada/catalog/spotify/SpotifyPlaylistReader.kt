package senada.catalog.spotify

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import senada.catalog.PlaylistImportException

/** Metadata lagu dari halaman publik Spotify sebelum dicocokkan ke YouTube Music. */
internal data class SpotifyTrack(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String = "",
    val durationMs: Long? = null,
)

/** Isi playlist Spotify yang berhasil dibaca tanpa akun. */
internal data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val tracks: List<SpotifyTrack>,
    val complete: Boolean = true,
)

internal fun interface SpotifyPlaylistSource {
    suspend fun fetchPlaylist(
        playlistId: String,
        limit: Int = SpotifyPlaylistReader.DEFAULT_LIMIT,
    ): SpotifyPlaylist
}

/**
 * Membaca playlist Spotify publik tanpa login melalui halaman embed (`open.spotify.com/embed/...`),
 * lalu melanjutkan halaman berikutnya (bila > 100 lagu dan tersedia token anonim) lewat Partner Query API
 * atau Web API publik seperti Senada desktop (`senada/spotify_import.py`).
 */
internal class SpotifyPlaylistReader(
    private val http: HttpClient,
) : SpotifyPlaylistSource {
    private val hashLock = Mutex()
    private var cachedPlaylistHash: String = DEFAULT_PLAYLIST_HASH

    override suspend fun fetchPlaylist(playlistId: String, limit: Int): SpotifyPlaylist {
        val embed = fetchEmbed(playlistId)
        val firstTracks = embed.tracks.distinctBy { it.id }

        if (embed.rawTrackCount < PAGE_SIZE || firstTracks.size >= limit || embed.accessToken.isBlank()) {
            val capped = firstTracks.take(limit)
            if (capped.isEmpty()) {
                throw PlaylistImportException(EMPTY_OR_PRIVATE)
            }
            return SpotifyPlaylist(
                id = playlistId,
                name = embed.name,
                tracks = capped,
                complete = embed.rawTrackCount < PAGE_SIZE || firstTracks.size >= limit,
            )
        }

        val (tracks, complete) = loadRemainingTracks(
            playlistId = playlistId,
            token = embed.accessToken,
            initialTracks = firstTracks,
            rawInitialCount = embed.rawTrackCount,
            limit = limit,
        )
        val deduped = tracks.distinctBy { it.id }.take(limit)
        if (deduped.isEmpty()) {
            throw PlaylistImportException(EMPTY_OR_PRIVATE)
        }
        return SpotifyPlaylist(
            id = playlistId,
            name = embed.name,
            tracks = deduped,
            complete = complete,
        )
    }

    private suspend fun fetchEmbed(playlistId: String): EmbedPage {
        val html = try {
            val response = http.get("$EMBED_BASE_URL/$playlistId") {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
            }
            when (response.status) {
                HttpStatusCode.NotFound -> throw PlaylistImportException(NOT_FOUND_OR_PRIVATE)
                HttpStatusCode.TooManyRequests -> throw PlaylistImportException(RATE_LIMITED)
                else -> if (!response.status.isSuccess()) {
                    throw PlaylistImportException("Spotify menolak permintaan (HTTP ${response.status.value}).")
                }
            }
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: PlaylistImportException) {
            throw e
        } catch (e: Exception) {
            throw PlaylistImportException("Tidak dapat terhubung ke Spotify. Periksa koneksi internet.", e)
        }
        return parseEmbedHtml(html)
    }

    private suspend fun loadRemainingTracks(
        playlistId: String,
        token: String,
        initialTracks: List<SpotifyTrack>,
        rawInitialCount: Int,
        limit: Int,
    ): Pair<List<SpotifyTrack>, Boolean> {
        try {
            val partnerTracks = fetchPartnerPlaylist(playlistId, token, limit)
            if (partnerTracks.isNotEmpty()) {
                return partnerTracks to true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Lanjutkan ke cadangan Web API tanpa membuang halaman pertama dari embed.
        }

        try {
            val extra = fetchWebApiTracks(playlistId, token, rawInitialCount, limit - initialTracks.size)
            return (initialTracks + extra) to true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return initialTracks to false
        }
    }

    private suspend fun fetchPartnerPlaylist(
        playlistId: String,
        token: String,
        limit: Int,
    ): List<SpotifyTrack> {
        var queryHash = hashLock.withLock { cachedPlaylistHash }
        var refreshed = false
        val tracks = mutableListOf<SpotifyTrack>()
        var offset = 0
        var totalCount: Int? = null

        while (tracks.size < limit && (totalCount == null || offset < totalCount)) {
            val body = buildJsonObject {
                put("operationName", "fetchPlaylist")
                putJsonObject("variables") {
                    put("uri", "spotify:playlist:$playlistId")
                    put("offset", offset)
                    put("limit", PAGE_SIZE)
                    put("enableWatchFeedEntrypoint", false)
                }
                putJsonObject("extensions") {
                    putJsonObject("persistedQuery") {
                        put("version", 1)
                        put("sha256Hash", queryHash)
                    }
                }
            }

            val response = http.post(PARTNER_URL) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/json")
                header("App-Platform", "WebPlayer")
                setBody(TextContent(body.toString(), ContentType.Application.Json))
            }

            if ((response.status == HttpStatusCode.BadRequest || response.status.value == 412) && !refreshed) {
                refreshed = true
                val newHash = refreshPlaylistHash()
                if (newHash != null) {
                    queryHash = newHash
                    continue
                }
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("Spotify Partner HTTP ${response.status.value}")
            }

            val json = parseJsonObject(response.bodyAsText())
            val content = json.at("data", "playlistV2", "content") ?: break
            totalCount = content.long("totalCount")?.toInt() ?: totalCount
            val items = content.arr("items")
            if (items.isEmpty()) break

            for (item in items) {
                parsePartnerTrack(item.at("itemV2", "data"))?.let { tracks += it }
            }
            offset += items.size
        }
        return tracks
    }

    private suspend fun refreshPlaylistHash(): String? {
        val html = try {
            val response = http.get(OPEN_SPOTIFY_HOME) {
                header(HttpHeaders.UserAgent, USER_AGENT)
            }
            if (!response.status.isSuccess()) return null
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }

        val scripts = WEB_PLAYER_SCRIPT_REGEX.findAll(html)
            .map { it.value }
            .distinct()
            .sortedBy { if ("/web-player/web-player." in it) 0 else 1 }
            .take(MAX_SCRAPED_SCRIPTS)
            .toList()

        for (scriptUrl in scripts) {
            val scriptBody = try {
                val resp = http.get(scriptUrl) { header(HttpHeaders.UserAgent, USER_AGENT) }
                if (!resp.status.isSuccess()) continue
                resp.bodyAsText()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
            val foundHash = FETCH_PLAYLIST_HASH_REGEX.find(scriptBody)?.groupValues?.getOrNull(1)
            if (foundHash != null) {
                hashLock.withLock { cachedPlaylistHash = foundHash }
                return foundHash
            }
        }
        return null
    }

    private suspend fun fetchWebApiTracks(
        playlistId: String,
        token: String,
        startOffset: Int,
        remainingLimit: Int,
    ): List<SpotifyTrack> {
        if (remainingLimit <= 0) return emptyList()
        val out = mutableListOf<SpotifyTrack>()
        var useInitialUrl = true
        var nextUrl: String? = null

        while (out.size < remainingLimit && (useInitialUrl || nextUrl != null)) {
            val response = if (useInitialUrl) {
                useInitialUrl = false
                http.get("$WEB_API_BASE/$playlistId/tracks") {
                    parameter("offset", startOffset)
                    parameter("limit", PAGE_SIZE)
                    parameter("fields", WEB_API_FIELDS)
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.Accept, "application/json")
                }
            } else {
                val pageUrl = nextUrl ?: break
                http.get(pageUrl) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.Accept, "application/json")
                }
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("Spotify Web API HTTP ${response.status.value}")
            }

            val json = parseJsonObject(response.bodyAsText())
            for (item in json.arr("items")) {
                parseWebApiTrack(item.at("track"))?.let { out += it }
            }
            nextUrl = json.str("next")?.takeIf { it.startsWith("https://") }
        }
        return out
    }

    companion object {
        const val PAGE_SIZE = 100
        const val DEFAULT_LIMIT = 500
        private const val MAX_SCRAPED_SCRIPTS = 5

        private const val EMBED_BASE_URL = "https://open.spotify.com/embed/playlist"
        private const val OPEN_SPOTIFY_HOME = "https://open.spotify.com/"
        private const val PARTNER_URL = "https://api-partner.spotify.com/pathfinder/v2/query"
        private const val WEB_API_BASE = "https://api.spotify.com/v1/playlists"
        private const val WEB_API_FIELDS = "total,next,items(track(type,uri,name,duration_ms,artists(name),album(name)))"
        private const val DEFAULT_PLAYLIST_HASH = "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf"
        private const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

        private val NEXT_DATA_REGEX = Regex(
            """<script\b[^>]*\bid=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        private val WEB_PLAYER_SCRIPT_REGEX = Regex(
            """https://[a-z.]*spotifycdn\.com/cdn/build/web-player/[^"'\s]+\.js""",
        )
        private val FETCH_PLAYLIST_HASH_REGEX = Regex(
            """"fetchPlaylist","query","([0-9a-f]{64})"""",
        )

        internal const val NOT_FOUND_OR_PRIVATE =
            "Spotify tidak dapat menemukan playlist ini. Pastikan playlist bersifat publik."
        internal const val EMPTY_OR_PRIVATE =
            "Playlist Spotify ini kosong atau bersifat pribadi."
        internal const val FORMAT_CHANGED =
            "Format halaman Spotify berubah sehingga belum dapat dibaca."
        internal const val UNREADABLE_DATA =
            "Data playlist Spotify tidak dapat dibaca."
        internal const val RATE_LIMITED =
            "Spotify sedang membatasi permintaan. Coba lagi nanti."

        internal fun parseEmbedHtml(html: String): EmbedPage {
            val jsonText = NEXT_DATA_REGEX.find(html)?.groupValues?.getOrNull(1)
                ?: throw PlaylistImportException(FORMAT_CHANGED)
            val root = try {
                parseJsonObject(jsonText)
            } catch (e: Exception) {
                throw PlaylistImportException(UNREADABLE_DATA, e)
            }

            val state = root.at("props", "pageProps", "state")
            val entity = state.at("data", "entity")
            val name = entity.str("name")?.trim()?.takeIf { it.isNotEmpty() }
                ?: entity.str("title")?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw PlaylistImportException(NOT_FOUND_OR_PRIVATE)

            val token = state.str("settings", "session", "accessToken").orEmpty()
            val rawList = entity.arr("trackList")
            val tracks = rawList.mapNotNull(::parseEmbedTrack)
            return EmbedPage(
                name = name,
                accessToken = token,
                tracks = tracks,
                rawTrackCount = rawList.size,
            )
        }
    }
}

internal data class EmbedPage(
    val name: String,
    val accessToken: String,
    val tracks: List<SpotifyTrack>,
    val rawTrackCount: Int,
)

internal fun parseEmbedTrack(item: JsonElement): SpotifyTrack? {
    val uri = item.str("uri").orEmpty()
    if (!uri.startsWith(SPOTIFY_TRACK_PREFIX)) return null
    val id = uri.removePrefix(SPOTIFY_TRACK_PREFIX).takeIf { it.isNotBlank() } ?: return null
    val title = (item.str("title") ?: item.str("name")).orEmpty().trim()
    if (title.isEmpty()) return null
    val artists = parseArtistList(item.at("subtitle")).ifEmpty {
        parseArtistList(item.at("artists"))
    }
    val durationMs = item.long("duration")?.takeIf { it > 0 }
    val album = item.str("album").orEmpty().trim()
    return SpotifyTrack(id = id, title = title, artists = artists, album = album, durationMs = durationMs)
}

internal fun parsePartnerTrack(data: JsonElement?): SpotifyTrack? {
    if (data == null) return null
    val typename = data.str("__typename")
    val uri = data.str("uri").orEmpty()
    if (typename != "Track" || !uri.startsWith(SPOTIFY_TRACK_PREFIX)) return null
    val id = uri.removePrefix(SPOTIFY_TRACK_PREFIX).takeIf { it.isNotBlank() } ?: return null
    val title = data.str("name").orEmpty().trim()
    if (title.isEmpty()) return null
    val artists = data.at("artists").arr("items")
        .mapNotNull { it.at("profile").str("name")?.trim()?.takeIf { n -> n.isNotEmpty() } }
    val album = data.at("albumOfTrack").str("name").orEmpty().trim()
    val durationMs = data.at("trackDuration").long("totalMilliseconds")?.takeIf { it > 0 }
    return SpotifyTrack(id = id, title = title, artists = artists, album = album, durationMs = durationMs)
}

internal fun parseWebApiTrack(track: JsonElement?): SpotifyTrack? {
    if (track == null) return null
    val type = track.str("type")
    val uri = track.str("uri").orEmpty()
    if (type != "track" || !uri.startsWith(SPOTIFY_TRACK_PREFIX)) return null
    val id = uri.removePrefix(SPOTIFY_TRACK_PREFIX).takeIf { it.isNotBlank() } ?: return null
    val title = track.str("name").orEmpty().trim()
    if (title.isEmpty()) return null
    val artists = track.arr("artists")
        .mapNotNull { it.str("name")?.trim()?.takeIf { n -> n.isNotEmpty() } }
    val album = track.at("album").str("name").orEmpty().trim()
    val durationMs = track.long("duration_ms")?.takeIf { it > 0 }
    return SpotifyTrack(id = id, title = title, artists = artists, album = album, durationMs = durationMs)
}

private fun parseArtistList(element: JsonElement?): List<String> = when (element) {
    is JsonArray -> element.mapNotNull { entry ->
        when (entry) {
            is JsonObject -> entry.str("name")?.trim()?.takeIf { it.isNotEmpty() }
            is JsonPrimitive -> if (entry.isString) entry.content.trim().takeIf { it.isNotEmpty() } else null
            else -> null
        }
    }
    is JsonPrimitive -> if (element.isString) {
        element.content
            .replace('\u00A0', ' ')
            .split(',')
            .map(String::trim)
            .filter { it.isNotEmpty() }
    } else {
        emptyList()
    }
    else -> emptyList()
}

private fun parseJsonObject(text: String): JsonObject =
    Json.parseToJsonElement(text) as? JsonObject ?: throw IllegalArgumentException("Expected JSON object")

private fun JsonElement?.at(vararg path: Any): JsonElement? {
    var current = this
    for (key in path) {
        current = when {
            key is String && current is JsonObject -> current[key]
            key is Int && current is JsonArray -> current.getOrNull(key)
            else -> null
        }
    }
    return current
}

private fun JsonElement?.str(vararg path: Any): String? =
    (at(*path) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonElement?.long(vararg path: Any): Long? =
    (at(*path) as? JsonPrimitive)?.longOrNull

private fun JsonElement?.arr(vararg path: Any): List<JsonElement> =
    at(*path) as? JsonArray ?: emptyList()

private const val SPOTIFY_TRACK_PREFIX = "spotify:track:"
