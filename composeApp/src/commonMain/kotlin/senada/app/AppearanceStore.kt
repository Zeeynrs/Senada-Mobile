package senada.app

import coil3.PlatformContext
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import senada.app.theme.AccentChoice

internal const val MIN_COVER_RADIUS_DP = 0
internal const val MAX_COVER_RADIUS_DP = 28
internal const val DEFAULT_COVER_RADIUS_DP = 16
internal const val COVER_RADIUS_STEP_DP = 2

internal fun clampCoverRadius(radiusDp: Int): Int =
    radiusDp.coerceIn(MIN_COVER_RADIUS_DP, MAX_COVER_RADIUS_DP)

internal enum class ThemeMode(val label: String) {
    System("Ikuti sistem"), Light("Terang"), Dark("Gelap"),
}

internal enum class AnimationSpeed(
    val key: String,
    val label: String,
    val scale: Float,
) {
    Slow("slow", "Pelan", 1.5f),
    Normal("normal", "Normal", 1.0f),
    Fast("fast", "Cepat", 0.6f),
    Off("off", "Mati", 0f),
}

internal data class Appearance(
    val mode: ThemeMode = ThemeMode.System,
    val accent: AccentChoice = AccentChoice.FollowCover,
    val saveMobileData: Boolean = false,
    val coverRadius: Int = DEFAULT_COVER_RADIUS_DP,
    val playerBackdropFromCover: Boolean = true,
    val glass: Boolean = true,
    val animation: AnimationSpeed = AnimationSpeed.Normal,
)

/** Satu nilai berversi agar tema, aksen, penghemat data, dan tampilan visual disimpan bersama. */
internal class AppearanceStore(private val read: () -> String?, private val write: (String) -> Unit) {
    fun load(): Appearance {
        return try {
            val value = Json.parseToJsonElement(read() ?: return Appearance()).jsonObject
            val mode = ThemeMode.valueOf(value.getValue("mode").jsonPrimitive.content)
            val version = value["version"]
            val accent = when (version) {
                JsonPrimitive(1) -> {
                    val follow = value.getValue("followCover").jsonPrimitive
                    require(!follow.isString)
                    if (follow.boolean) AccentChoice.FollowCover else AccentChoice.Senada
                }
                JsonPrimitive(2), JsonPrimitive(3) -> {
                    val choice = value.getValue("accent").jsonPrimitive
                    require(choice.isString)
                    AccentChoice.entries.first { it.key == choice.content }
                }
                else -> return Appearance()
            }
            val saveData = value.optionalBoolean("saveMobileData", default = false)
            if (version == JsonPrimitive(3)) {
                val coverRadius = clampCoverRadius(value.optionalInt("coverRadius", DEFAULT_COVER_RADIUS_DP))
                val playerBackdrop = value.optionalBoolean("playerBackdropFromCover", default = true)
                val glass = value.optionalBoolean("glass", default = true)
                val animationKey = (value["animation"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                val animation = AnimationSpeed.entries.firstOrNull { it.key == animationKey } ?: AnimationSpeed.Normal
                Appearance(
                    mode = mode,
                    accent = accent,
                    saveMobileData = saveData,
                    coverRadius = coverRadius,
                    playerBackdropFromCover = playerBackdrop,
                    glass = glass,
                    animation = animation,
                )
            } else {
                Appearance(mode = mode, accent = accent, saveMobileData = saveData)
            }
        } catch (_: Exception) {
            Appearance()
        }
    }

    fun save(settings: Appearance) = write(buildJsonObject {
        put("version", 3)
        put("mode", settings.mode.name)
        put("accent", settings.accent.key)
        put("saveMobileData", settings.saveMobileData)
        put("coverRadius", clampCoverRadius(settings.coverRadius))
        put("playerBackdropFromCover", settings.playerBackdropFromCover)
        put("glass", settings.glass)
        put("animation", settings.animation.key)
    }.toString())
}

private fun JsonObject.optionalBoolean(key: String, default: Boolean): Boolean =
    (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: default

private fun JsonObject.optionalInt(key: String, default: Int): Int {
    val primitive = this[key] as? JsonPrimitive ?: return default
    if (primitive.isString) return default
    return primitive.intOrNull
        ?: primitive.longOrNull?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt()
        ?: primitive.doubleOrNull?.takeIf { !it.isNaN() && !it.isInfinite() }?.roundToInt()
        ?: default
}

internal expect fun appearanceStore(context: PlatformContext): AppearanceStore
