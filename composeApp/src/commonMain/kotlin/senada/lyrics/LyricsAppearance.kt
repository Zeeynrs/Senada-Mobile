package senada.lyrics

import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal enum class LyricsSize(val label: String, val scale: Float) {
    Small("Kecil", .85f), Medium("Sedang", 1f), Large("Besar", 1.2f),
}

internal enum class LyricsFont(val label: String) {
    Sans("Sans"), Serif("Serif"),
}

internal enum class LyricsAlignment(val label: String) {
    Left("Kiri"), Center("Tengah"),
}

internal enum class LyricsHighlight(val label: String) {
    Bright("Terang"), Glow("Pendar"), Karaoke("Karaoke"),
}

internal data class LyricsAppearance(
    val size: LyricsSize = LyricsSize.Medium,
    val font: LyricsFont = LyricsFont.Sans,
    val alignment: LyricsAlignment = LyricsAlignment.Left,
    val highlight: LyricsHighlight = LyricsHighlight.Bright,
)

internal fun lyricsAppearanceFromJson(value: JsonElement?): LyricsAppearance {
    val json = value as? JsonObject ?: return LyricsAppearance()
    return LyricsAppearance(
        size = json.enumValue("size", LyricsSize.Medium),
        font = json.enumValue("font", LyricsFont.Sans),
        alignment = json.enumValue("alignment", LyricsAlignment.Left),
        highlight = json.enumValue("highlight", LyricsHighlight.Bright),
    )
}

private inline fun <reified E : Enum<E>> JsonObject.enumValue(key: String, default: E): E {
    val value = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return default
    return enumValues<E>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: default
}

internal fun LyricsAppearance.toJson(): JsonObject = buildJsonObject {
    put("size", size.name.lowercase())
    put("font", font.name.lowercase())
    put("alignment", alignment.name.lowercase())
    put("highlight", highlight.name.lowercase())
}

internal fun lyricProgress(positionMs: Long, startMs: Long, nextStartMs: Long?): Float {
    if (positionMs <= startMs) return 0f
    val durationMs = nextStartMs?.takeIf { it > startMs }?.minus(startMs) ?: 4_000L
    return ((positionMs - startMs).toDouble() / durationMs).coerceAtMost(1.0).toFloat()
}

internal fun sungTextEnd(text: String, progress: Float): Int {
    val end = (text.length * progress.coerceIn(0f, 1f)).roundToInt()
    return if (end in 1 until text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) {
        end - 1
    } else {
        end
    }
}
