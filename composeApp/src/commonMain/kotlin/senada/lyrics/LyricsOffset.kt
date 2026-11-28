// Portions adapted from Mochy Music (https://github.com/realidkroo/Mochy-Music, commit 3071023), GPL-3.0.
package senada.lyrics

import coil3.PlatformContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import senada.app.AppLanguage
import senada.app.AppStrings
import senada.resources.Res
import senada.resources.lyrics_offset_seconds

internal const val MAX_LYRICS_OFFSET_MS = 10_000
internal const val MIN_LYRICS_OFFSET_MS = -MAX_LYRICS_OFFSET_MS

internal val LYRICS_OFFSET_STEPS_MS = listOf(-500, -100, 100, 500)

private const val MINUS_SIGN = '\u2212'

internal fun clampLyricsOffsetMs(offsetMs: Int): Int =
    offsetMs.coerceIn(MIN_LYRICS_OFFSET_MS, MAX_LYRICS_OFFSET_MS)

/**
 * Formats a lyrics timing offset in seconds with one decimal place and an explicit sign when non-zero
 * (e.g. "+0.3 s", "−1.2 s", "0.0 s" in English; "+0,3 dtk", "−1,2 dtk", "0,0 dtk" in Indonesian).
 */
internal fun formatLyricsOffset(
    offsetMs: Int,
    language: AppLanguage = AppStrings.currentLanguage,
): String {
    val clamped = clampLyricsOffsetMs(offsetMs)
    val absTenths = (abs(clamped) + 50) / 100
    val sign = when {
        absTenths == 0 -> ""
        clamped > 0 -> "+"
        else -> MINUS_SIGN.toString()
    }
    val number = "$sign${formatTenths(absTenths, language)}"
    return AppStrings.get(Res.string.lyrics_offset_seconds, language, number)
}

/** Formats an unsigned magnitude in seconds with one decimal place for accessibility announcements. */
internal fun formatLyricsOffsetMagnitude(
    offsetMs: Int,
    language: AppLanguage = AppStrings.currentLanguage,
): String {
    val clamped = clampLyricsOffsetMs(offsetMs)
    val absTenths = (abs(clamped) + 50) / 100
    return AppStrings.get(Res.string.lyrics_offset_seconds, language, formatTenths(absTenths, language))
}

private fun formatTenths(absTenths: Int, language: AppLanguage): String {
    val whole = absTenths / 10
    val frac = absTenths % 10
    val separator = if (language == AppLanguage.Indonesian) ',' else '.'
    return "$whole$separator$frac"
}

/**
 * Persists per-track lyrics timing offsets (in milliseconds, keyed by `Track.id`) in a single
 * versioned JSON payload. Offsets are clamped to ±[MAX_LYRICS_OFFSET_MS], and 0 is never stored.
 */
internal class LyricsOffsetStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
) {
    fun loadAll(): Map<String, Int> {
        return try {
            val raw = read() ?: return emptyMap()
            val root = Json.parseToJsonElement(raw).jsonObject
            if (root["version"] != JsonPrimitive(VERSION)) return emptyMap()
            val entries = root["offsets"] as? JsonObject ?: return emptyMap()
            buildMap {
                for ((trackId, element) in entries) {
                    if (trackId.isEmpty()) continue
                    val parsed = element.offsetIntOrNull() ?: continue
                    val clamped = clampLyricsOffsetMs(parsed)
                    if (clamped != 0) put(trackId, clamped)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveAll(offsets: Map<String, Int>) {
        val payload = buildJsonObject {
            put("version", VERSION)
            put("offsets", buildJsonObject {
                for ((trackId, rawOffset) in offsets) {
                    if (trackId.isEmpty()) continue
                    val clamped = clampLyricsOffsetMs(rawOffset)
                    if (clamped != 0) put(trackId, clamped)
                }
            })
        }
        write(payload.toString())
    }

    fun get(trackId: String): Int =
        if (trackId.isEmpty()) 0 else loadAll()[trackId] ?: 0

    fun set(trackId: String, offsetMs: Int): Int {
        if (trackId.isEmpty()) return 0
        val clamped = clampLyricsOffsetMs(offsetMs)
        val current = loadAll().toMutableMap()
        if (clamped == 0) {
            if (current.remove(trackId) != null) saveAll(current)
        } else {
            if (current.put(trackId, clamped) != clamped) saveAll(current)
        }
        return clamped
    }

    fun reset(trackId: String) {
        set(trackId, 0)
    }

    private companion object {
        const val VERSION = 1
    }
}

private fun JsonElement.offsetIntOrNull(): Int? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive.isString) return null
    return primitive.intOrNull
        ?: primitive.longOrNull?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt()
        ?: primitive.doubleOrNull?.takeIf { !it.isNaN() && !it.isInfinite() }?.roundToInt()
}

internal class LyricsOffsetController(private val store: LyricsOffsetStore) {
    private val offsets = MutableStateFlow<Map<String, Int>>(emptyMap())
    val state: StateFlow<Map<String, Int>> = offsets.asStateFlow()

    private val editedBeforeRestore = mutableSetOf<String>()
    private var restored = false

    suspend fun restore() {
        if (restored) return
        val loaded = withContext(Dispatchers.Default) { store.loadAll() }
        restored = true
        if (editedBeforeRestore.isEmpty()) {
            offsets.value = loaded
        } else {
            val merged = loaded.toMutableMap()
            val pending = offsets.value
            for (trackId in editedBeforeRestore) {
                val value = pending[trackId] ?: 0
                if (value == 0) merged.remove(trackId) else merged[trackId] = value
            }
            editedBeforeRestore.clear()
            offsets.value = merged
            store.saveAll(merged)
        }
    }

    fun offsetFor(trackId: String?): Int =
        if (trackId.isNullOrEmpty()) 0 else offsets.value[trackId] ?: 0

    fun setOffset(trackId: String, offsetMs: Int): Int {
        if (trackId.isEmpty()) return 0
        val clamped = clampLyricsOffsetMs(offsetMs)
        val current = offsets.value
        val previous = current[trackId] ?: 0
        if (clamped == previous) return clamped
        val next = if (clamped == 0) current - trackId else current + (trackId to clamped)
        offsets.value = next
        if (restored) {
            store.saveAll(next)
        } else {
            editedBeforeRestore += trackId
        }
        return clamped
    }

    fun adjustOffset(trackId: String, deltaMs: Int): Int =
        setOffset(trackId, offsetFor(trackId) + deltaMs)

    fun resetOffset(trackId: String) {
        setOffset(trackId, 0)
    }
}

internal expect fun lyricsOffsetStore(context: PlatformContext): LyricsOffsetStore
