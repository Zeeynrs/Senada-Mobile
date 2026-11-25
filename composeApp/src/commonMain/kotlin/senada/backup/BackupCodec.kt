package senada.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import senada.app.AppLanguage
import senada.app.AppStrings
import senada.app.UiText
import senada.catalog.youtube.YOUTUBE_MUSIC_SOURCE
import senada.model.Track
import senada.resources.Res
import senada.resources.error_backup_corrupted
import senada.resources.error_backup_newer_version
import senada.resources.error_backup_not_a_backup
import senada.resources.error_backup_too_large

/** Backup tidak bisa dibaca atau ditolak; [userMessage] siap ditampilkan. */
internal class BackupException(
    internal val uiText: UiText,
    cause: Throwable? = null,
) : Exception(uiText.resolve(), cause) {
    constructor(userMessage: String, cause: Throwable? = null) : this(UiText.Raw(userMessage), cause)

    fun userMessage(language: AppLanguage = AppStrings.currentLanguage): String = uiText.resolve(language)

    val userMessage: String
        get() = userMessage()
}

/** Lagu di backup beserta waktunya (ditambahkan ke favorit atau terakhir diputar), dalam milidetik. */
internal data class BackupTrack(val track: Track, val atMs: Long?)

internal data class BackupPlaylist(
    val id: String,
    val name: String,
    val description: String,
    val createdAtMs: Long?,
    val tracks: List<Track>,
)

/** Album/playlist katalog yang disimpan; [metadata] adalah objek JSON desktop utuh. */
internal data class BackupSavedItem(val kind: String, val id: String, val metadata: String)

/**
 * Isi library dari backup yang bisa dipakai di ponsel. [skipped] menghitung entri yang dilewati:
 * file lokal desktop, lagu dari sumber selain YouTube Music, dan entri yang rusak.
 */
internal data class BackupDocument(
    val liked: List<BackupTrack> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val saved: List<BackupSavedItem> = emptyList(),
    val history: List<BackupTrack> = emptyList(),
    val skipped: Int = 0,
)

/**
 * Format backup Senada desktop (`senada/backup.py`): `app=Senada`, `kind=backup`, `format=1`, dengan
 * `library` berisi `liked`, `playlists`, `saved` dan `history`. Waktu di desktop dalam detik, artis
 * berupa satu teks dipisah koma, dan identitas lagu ada di `uri` (`yt:track:<videoId>`).
 * Pengaturan desktop tidak pernah dibaca.
 */
internal object BackupCodec {
    const val FORMAT = 1
    const val MAX_BYTES = 10 * 1024 * 1024
    private val LIBRARY_KEYS = listOf("liked", "playlists", "saved", "history")
    private const val TRACK_PREFIX = "yt:track:"
    private const val ARTIST_SEPARATOR = ", "

    fun decode(text: String): BackupDocument {
        if (text.length > MAX_BYTES || text.encodeToByteArray().size > MAX_BYTES) {
            throw BackupException(UiText.Resource(Res.string.error_backup_too_large))
        }
        val parsed = try {
            Json.parseToJsonElement(text)
        } catch (e: IllegalArgumentException) {
            throw BackupException(UiText.Resource(Res.string.error_backup_not_a_backup), e)
        }
        val root = parsed as? JsonObject ?: throw BackupException(UiText.Resource(Res.string.error_backup_not_a_backup))

        val library = when {
            root.text("app") == "Senada" && root.text("kind") == "backup" -> {
                val format = (root["format"] as? JsonPrimitive)?.longOrNull ?: 0
                if (format > FORMAT) throw BackupException(UiText.Resource(Res.string.error_backup_newer_version))
                root["library"] as? JsonObject ?: throw BackupException(UiText.Resource(Res.string.error_backup_not_a_backup))
            }
            // library.json desktop yang disalin langsung juga diterima, seperti di desktop.
            LIBRARY_KEYS.any { it in root } -> root
            else -> throw BackupException(UiText.Resource(Res.string.error_backup_not_a_backup))
        }

        val sections = LIBRARY_KEYS.associateWith { key ->
            when (val items = library[key]) {
                null, JsonNull -> emptyList()
                is JsonArray -> items.filterIsInstance<JsonObject>()
                else -> throw BackupException(UiText.Resource(Res.string.error_backup_corrupted))
            }
        }

        var skipped = 0
        fun trackOrSkip(item: JsonObject): Track? = item.toTrack().also { if (it == null) skipped++ }

        val liked = sections.getValue("liked").mapNotNull { item ->
            trackOrSkip(item)?.let { BackupTrack(it, item.secondsAsMs("addedAt")) }
        }
        val playlists = sections.getValue("playlists").mapNotNull { item ->
            val id = item.text("id")?.takeIf { it.isNotBlank() } ?: run { skipped++; return@mapNotNull null }
            val tracks = (item["tracks"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull(::trackOrSkip)
            BackupPlaylist(
                id = id,
                name = item.text("name").orEmpty(),
                description = item.text("description").orEmpty(),
                createdAtMs = item.secondsAsMs("created"),
                tracks = tracks,
            )
        }
        val saved = sections.getValue("saved").mapNotNull { item ->
            val kind = item.text("kind")?.takeIf { it.isNotBlank() }
            val id = item.text("id")?.takeIf { it.isNotBlank() }
            if (kind == null || id == null) {
                skipped++
                null
            } else {
                BackupSavedItem(kind, id, item.toString())
            }
        }
        val history = sections.getValue("history").mapNotNull { item ->
            trackOrSkip(item)?.let { BackupTrack(it, item.secondsAsMs("playedAt")) }
        }
        return BackupDocument(liked, playlists, saved, history, skipped)
    }

    /** Backup dalam format desktop; [createdIso] waktu pembuatan dalam ISO 8601. */
    fun encode(document: BackupDocument, createdIso: String): String = buildJsonObject {
        put("app", "Senada")
        put("kind", "backup")
        put("format", FORMAT)
        put("senadaVersion", "mobile")
        put("created", createdIso)
        put("library", buildJsonObject {
            put("liked", buildJsonArray { document.liked.forEach { add(it.track.toDesktop("addedAt", it.atMs)) } })
            put("playlists", buildJsonArray {
                document.playlists.forEach { playlist ->
                    add(buildJsonObject {
                        put("id", playlist.id)
                        put("name", playlist.name)
                        put("description", playlist.description)
                        put("source", "")
                        put("created", (playlist.createdAtMs ?: 0) / 1_000)
                        put("tracks", buildJsonArray { playlist.tracks.forEach { add(it.toDesktop()) } })
                    })
                }
            })
            put("saved", buildJsonArray { document.saved.forEach { add(it.toDesktop()) } })
            put("history", buildJsonArray { document.history.forEach { add(it.track.toDesktop("playedAt", it.atMs)) } })
        })
    }.toString()

    private fun JsonObject.toTrack(): Track? {
        val uri = text("uri") ?: return null
        val videoId = uri.removePrefix(TRACK_PREFIX)
        if (!uri.startsWith(TRACK_PREFIX) || videoId.isBlank()) return null
        val title = text("name")?.takeIf { it.isNotBlank() } ?: return null
        val artists = when (val value = this["artists"]) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            is JsonPrimitive -> if (value.isString) value.content.split(ARTIST_SEPARATOR) else emptyList()
            else -> emptyList()
        }.map { it.trim() }.filter { it.isNotEmpty() }
        val artwork = listOf("image", "imageLarge", "imageSmall").firstNotNullOfOrNull { key -> text(key)?.takeIf { it.isNotBlank() } }
        return Track(
            id = uri,
            title = title,
            artists = artists,
            durationMs = (this["durationMs"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 },
            artworkUrl = artwork,
            source = YOUTUBE_MUSIC_SOURCE,
        )
    }

    /** Kolom lagu desktop (`store.TRACK_KEYS`); data yang tidak dimiliki ponsel diisi kosong. */
    private fun Track.toDesktop(timeKey: String? = null, atMs: Long? = null) = buildJsonObject {
        put("uri", id)
        put("id", id.removePrefix(TRACK_PREFIX))
        put("name", title)
        put("artists", artists.joinToString(ARTIST_SEPARATOR))
        put("artistId", "")
        put("album", "")
        put("albumId", "")
        put("imageSmall", artworkUrl.orEmpty())
        put("image", artworkUrl.orEmpty())
        put("imageLarge", artworkUrl.orEmpty())
        put("durationMs", durationMs ?: 0L)
        put("explicit", false)
        if (timeKey != null) put(timeKey, (atMs ?: 0) / 1_000)
    }

    private fun BackupSavedItem.toDesktop(): JsonObject {
        val original = runCatching { Json.parseToJsonElement(metadata) as? JsonObject }.getOrNull()
        return original ?: buildJsonObject {
            put("kind", kind)
            put("id", id)
        }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** Waktu desktop (detik) sebagai milidetik; nilai tidak valid dianggap tidak ada. */
    private fun JsonObject.secondsAsMs(key: String): Long? {
        val value = this[key] as? JsonPrimitive ?: return null
        if (value.isString || value.booleanOrNull != null) return null
        val seconds = value.longOrNull ?: value.content.toDoubleOrNull()?.toLong() ?: return null
        return seconds.takeIf { it in 1..MAX_SECONDS }?.times(1_000)
    }

    private const val MAX_SECONDS = Long.MAX_VALUE / 1_000
}
