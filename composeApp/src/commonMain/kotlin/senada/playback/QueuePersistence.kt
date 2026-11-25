package senada.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import senada.model.Track

internal interface QueueStore {
    fun load(): String?
    fun save(text: String)

    /** Menjalankan penulisan berurutan. Flush menunggu pekerjaan yang sudah dijadwalkan selesai. */
    fun scheduleSave(flush: Boolean, write: () -> Unit) = write()
}

internal data class QueueSnapshot(val queue: Queue, val selectedEntryId: Long?, val positionMs: Long)

internal fun QueueStore.loadSnapshot(): QueueSnapshot? = try {
    QueueJson.decode(load())
} catch (_: Exception) {
    null
}

internal class QueuePersistence(
    private val store: QueueStore,
    private val scope: CoroutineScope,
    private val snapshot: () -> QueueSnapshot,
) {
    private var lastSaved = snapshot()
    private var timer: Job? = null

    fun onPlaybackChanged(state: PlaybackState) {
        save(flush = false)
        if (state.audio.status != SampleAudioStatus.Playing || state.currentEntry == null) {
            timer?.cancel()
            timer = null
        } else if (timer == null) {
            timer = scope.launch {
                while (isActive) {
                    delay(15_000L)
                    save(flush = false)
                }
            }
        }
    }

    fun saveNow() = save(flush = true)

    private fun save(flush: Boolean) {
        val current = snapshot()
        store.scheduleSave(flush) {
            if (current == lastSaved) return@scheduleSave
            try {
                store.save(QueueJson.encode(current))
                lastSaved = current
            } catch (_: Exception) {
                // Antrean tetap bisa dipakai. Coba lagi saat penyimpanan berikutnya.
            }
        }
    }
}

internal object QueueJson {
    private const val VERSION = 1
    // Sesi media Android mengonversi durasi dari milidetik ke mikrodetik.
    private const val MAX_TIME_MS = Long.MAX_VALUE / 1_000

    fun encode(snapshot: QueueSnapshot): String = buildJsonObject {
        put("version", VERSION)
        put("nextId", snapshot.queue.nextId)
        put("selectedEntryId", snapshot.selectedEntryId)
        put("positionMs", snapshot.positionMs)
        put("repeat", snapshot.queue.repeatMode.name.lowercase())
        put("shuffle", snapshot.queue.shuffleEnabled)
        if (snapshot.queue.shuffleEnabled) {
            put("shuffleOrder", buildJsonArray {
                snapshot.queue.playOrder.forEach { add(JsonPrimitive(it.id)) }
            })
        }
        put("entries", buildJsonArray {
            snapshot.queue.entries.forEach { entry ->
                add(buildJsonObject {
                    put("id", entry.id)
                    put("track", entry.track.toJson())
                    entry.playlistKey?.let { put("playlistKey", it) }
                })
            }
        })
    }.toString()

    fun decode(text: String?): QueueSnapshot? {
        if (text == null) return null
        return try {
            val root = Json.parseToJsonElement(text).jsonObject
            if (root["version"] != JsonPrimitive(VERSION)) return null
            val entries = root.getValue("entries").jsonArray.map { element ->
                val item = element.jsonObject
                QueueItem(
                    id = item.getValue("id").readLong(),
                    track = item.getValue("track").jsonObject.readTrack(),
                    playlistKey = item["playlistKey"]?.readString(),
                )
            }
            val selected = root.getValue("selectedEntryId").takeUnless { it == JsonNull }?.readLong()
            val position = root.getValue("positionMs").readLong()
            require(position in 0..MAX_TIME_MS)
            require(if (selected == null) position == 0L else entries.any { it.id == selected })
            val repeat = root["repeat"]?.readString()?.let { value ->
                RepeatMode.entries.first { it.name.lowercase() == value }
            } ?: RepeatMode.Off
            val shuffle = root["shuffle"]?.jsonPrimitive?.run {
                require(!isString)
                boolean
            } ?: false
            val order = root["shuffleOrder"]?.jsonArray?.map { it.readLong() }
            require(shuffle || order == null)
            val queue = Queue.restore(entries, root.getValue("nextId").readLong(), repeat, order)
                .withShuffle(shuffle, selected)
            QueueSnapshot(queue, selected, position)
        } catch (_: Exception) {
            null
        }
    }

    private fun Track.toJson() = buildJsonObject {
        put("id", id)
        put("title", title)
        put("artists", buildJsonArray { artists.forEach { add(JsonPrimitive(it)) } })
        put("durationMs", durationMs)
        put("artworkUrl", artworkUrl)
        put("source", source)
    }

    private fun JsonObject.readTrack() = Track(
        id = getValue("id").readString(),
        title = getValue("title").readString(),
        artists = getValue("artists").jsonArray.map { it.readString() },
        durationMs = getValue("durationMs").takeUnless { it == JsonNull }?.readLong()?.also {
            require(it in 0..MAX_TIME_MS)
        },
        artworkUrl = getValue("artworkUrl").takeUnless { it == JsonNull }?.readString(),
        source = getValue("source").readString(),
    )

    private fun JsonElement.readLong(): Long = jsonPrimitive.run {
        require(!isString)
        long
    }

    private fun JsonElement.readString(): String = jsonPrimitive.run {
        require(isString)
        content
    }
}
