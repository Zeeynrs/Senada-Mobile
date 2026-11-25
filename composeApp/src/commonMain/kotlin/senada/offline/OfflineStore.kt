package senada.offline

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path
import senada.catalog.ResolvedAudio
import senada.catalog.youtube.YOUTUBE_MUSIC_SOURCE
import senada.catalog.youtube.videoIdOf
import senada.epochMillis
import senada.lyrics.LyricLine
import senada.lyrics.LyricsDocument
import senada.model.Track

internal data class OfflineSong(
    val track: Track,
    val audioUri: String,
    val coverUri: String?,
    val durationMs: Long?,
    val loudnessDb: Float?,
    val sizeBytes: Long,
    val downloadedAtMs: Long,
)

private val videoKey = Regex("[A-Za-z0-9_-]{1,64}")

internal fun canDownloadOffline(track: Track): Boolean = offlineKey(track) != null

private fun offlineKey(track: Track): String? =
    videoIdOf(track)?.takeIf { track.source == YOUTUBE_MUSIC_SOURCE && videoKey.matches(it) }

internal class OfflineStore(
    private val root: Path,
    private val files: FileSystem,
    private val fileUri: (Path) -> String,
) {
    fun list(): List<OfflineSong> = files.listOrNull(root).orEmpty()
        .filter { videoKey.matches(it.name) }
        .mapNotNull { read(it)?.song }
        .sortedByDescending { it.downloadedAtMs }

    fun find(track: Track): OfflineSong? = offlineKey(track)?.let { read(root / it)?.song }

    fun lyrics(track: Track): LyricsDocument? = offlineKey(track)?.let { read(root / it)?.lyrics }

    fun remove(track: Track) {
        val dir = root / checkNotNull(offlineKey(track))
        files.delete(dir / MANIFEST)
        files.deleteRecursively(dir, mustExist = false)
    }

    suspend fun save(
        track: Track,
        audio: ResolvedAudio,
        writeAudio: suspend (Path) -> Unit,
        writeCover: suspend (Path) -> Unit = {},
        readLyrics: suspend () -> LyricsDocument? = { null },
    ) {
        val dir = root / checkNotNull(offlineKey(track))
        if (read(dir) != null) return
        files.deleteRecursively(dir, mustExist = false)
        files.createDirectories(dir)
        var committed = false
        try {
            val media = dir / AUDIO
            writeAudio(media)
            check(size(media) > 0L)
            val cover = dir / COVER
            val lyrics = coroutineScope {
                val document = async { readLyrics() }
                writeCover(cover)
                document.await()
            }
            currentCoroutineContext().ensureActive()
            val manifest = encode(track, audio, lyrics)
            val temporary = dir / "$MANIFEST.part"
            files.write(temporary) { writeUtf8(manifest.toString()) }
            files.atomicMove(temporary, dir / MANIFEST)
            committed = true
        } finally {
            if (!committed) runCatching { files.deleteRecursively(dir, mustExist = false) }
        }
    }

    private fun size(path: Path): Long = files.metadataOrNull(path)?.takeIf { it.isRegularFile }?.size ?: 0L

    private fun read(dir: Path): StoredSong? = try {
        val media = dir / AUDIO
        val sizeBytes = size(media)
        val json = if (sizeBytes > 0L && files.exists(dir / MANIFEST)) {
            Json.parseToJsonElement(files.read(dir / MANIFEST) { readUtf8() }) as? JsonObject
        } else null
        json?.let { manifest ->
            val track = Track(
                id = checkNotNull(manifest.string("id")),
                title = checkNotNull(manifest.string("title")),
                artists = manifest.strings("artists"),
                durationMs = manifest.primitive("trackDurationMs")?.longOrNull,
                artworkUrl = manifest.string("artworkUrl"),
                source = YOUTUBE_MUSIC_SOURCE,
            )
            require(offlineKey(track) == dir.name)
            val cover = dir / COVER
            StoredSong(
                OfflineSong(
                    track, fileUri(media), cover.takeIf { size(it) > 0L }?.let(fileUri),
                    manifest.primitive("audioDurationMs")?.longOrNull,
                    manifest.primitive("loudnessDb")?.floatOrNull?.takeIf(Float::isFinite),
                    sizeBytes + size(cover), manifest.primitive("downloadedAtMs")?.longOrNull ?: 0L,
                ),
                (manifest["lyrics"] as? JsonObject)?.decodeLyrics(),
            )
        }
    } catch (_: Exception) {
        null
    }

    private data class StoredSong(val song: OfflineSong, val lyrics: LyricsDocument?)

    private companion object {
        const val MANIFEST = "song.json"
        const val AUDIO = "audio.m4a"
        const val COVER = "cover.jpg"
    }
}

private fun encode(track: Track, audio: ResolvedAudio, lyrics: LyricsDocument?) = buildJsonObject {
    put("id", track.id)
    put("title", track.title)
    put("artists", buildJsonArray { track.artists.forEach { add(JsonPrimitive(it)) } })
    put("trackDurationMs", track.durationMs)
    put("artworkUrl", track.artworkUrl)
    put("audioDurationMs", audio.durationMs)
    put("loudnessDb", audio.loudnessDb?.takeIf(Float::isFinite))
    put("downloadedAtMs", epochMillis())
    lyrics?.let { put("lyrics", it.encodeLyrics()) }
}

private fun LyricsDocument.encodeLyrics() = buildJsonObject {
    when (this@encodeLyrics) {
        is LyricsDocument.Plain -> {
            put("type", "plain")
            put("lines", buildJsonArray { lines.forEach { add(JsonPrimitive(it)) } })
        }
        is LyricsDocument.Synced -> {
            put("type", "synced")
            put("lines", buildJsonArray {
                lines.forEach { line ->
                    add(buildJsonObject {
                        put("time", line.startMs)
                        put("text", line.text)
                    })
                }
            })
        }
        LyricsDocument.Instrumental -> put("type", "instrumental")
        LyricsDocument.Unavailable -> put("type", "unavailable")
    }
}

private fun JsonObject.decodeLyrics(): LyricsDocument? = when (string("type")) {
    "plain" -> LyricsDocument.Plain(strings("lines"))
    "synced" -> LyricsDocument.Synced((get("lines") as? JsonArray).orEmpty().mapNotNull { value ->
        val line = value as? JsonObject ?: return@mapNotNull null
        val time = line.primitive("time")?.longOrNull ?: return@mapNotNull null
        LyricLine(time, line.string("text").orEmpty())
    })
    "instrumental" -> LyricsDocument.Instrumental
    "unavailable" -> LyricsDocument.Unavailable
    else -> null
}

private fun JsonObject.primitive(key: String) = get(key) as? JsonPrimitive
private fun JsonObject.string(key: String) = primitive(key)?.takeIf { it.isString }?.contentOrNull
private fun JsonObject.strings(key: String) = (get(key) as? JsonArray).orEmpty().mapNotNull {
    (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull
}
