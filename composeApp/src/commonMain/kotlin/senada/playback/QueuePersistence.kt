package senada.playback

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
}

internal data class QueueSnapshot(val queue: Queue, val selectedEntryId: Long?, val positionMs: Long)

internal fun PlaybackController.persistQueue(store: QueueStore) {
    val text = try {
        store.load()
    } catch (_: Exception) {
        null
    }
    QueueJson.decode(text)?.let(::restore)
    var lastSaved = state.value.queueSnapshot()
    addListener { state ->
        val snapshot = state.queueSnapshot()
        if (snapshot != lastSaved) {
            try {
                store.save(QueueJson.encode(snapshot))
                lastSaved = snapshot
            } catch (_: Exception) {
                // Antrean tetap bisa dipakai. Coba lagi saat ada perubahan berikutnya.
            }
        }
    }
}

private fun PlaybackState.queueSnapshot(): QueueSnapshot {
    val entryId = currentEntry?.id
    return QueueSnapshot(queue, entryId, if (entryId == null) 0 else audio.positionMs)
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
        put("entries", buildJsonArray {
            snapshot.queue.entries.forEach { entry ->
                add(buildJsonObject {
                    put("id", entry.id)
                    put("track", entry.track.toJson())
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
                QueueItem(item.getValue("id").readLong(), item.getValue("track").jsonObject.readTrack())
            }
            val queue = Queue.restore(entries, root.getValue("nextId").readLong())
            val selected = root.getValue("selectedEntryId").takeUnless { it == JsonNull }?.readLong()
            val position = root.getValue("positionMs").readLong()
            require(position in 0..MAX_TIME_MS)
            require(if (selected == null) position == 0L else entries.any { it.id == selected })
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
