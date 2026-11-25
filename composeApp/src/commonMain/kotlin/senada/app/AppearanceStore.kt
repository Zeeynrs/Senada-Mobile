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
import senada.lyrics.LyricsAppearance
import senada.lyrics.lyricsAppearanceFromJson
import senada.lyrics.toJson
import senada.resources.Res
import senada.resources.motion_fast
import senada.resources.motion_normal
import senada.resources.motion_off
import senada.resources.motion_slow
import senada.resources.player_theme_standard
import senada.resources.player_theme_vinyl
import senada.resources.theme_dark
import senada.resources.theme_light
import senada.resources.theme_system

internal const val MIN_COVER_RADIUS_DP = 0
internal const val MAX_COVER_RADIUS_DP = 28
internal const val DEFAULT_COVER_RADIUS_DP = 16
internal const val COVER_RADIUS_STEP_DP = 2

internal fun clampCoverRadius(radiusDp: Int): Int =
    radiusDp.coerceIn(MIN_COVER_RADIUS_DP, MAX_COVER_RADIUS_DP)

internal enum class ThemeMode {
    System, Light, Dark;

    fun label(language: AppLanguage = AppStrings.currentLanguage): String = when (this) {
        System -> AppStrings.get(Res.string.theme_system, language)
        Light -> AppStrings.get(Res.string.theme_light, language)
        Dark -> AppStrings.get(Res.string.theme_dark, language)
    }

    val label: String get() = label()
}

internal enum class FullViewTheme(val key: String) {
    Standard("standard"), Vinyl("vinyl");

    fun label(language: AppLanguage = AppStrings.currentLanguage): String = when (this) {
        Standard -> AppStrings.get(Res.string.player_theme_standard, language)
        Vinyl -> AppStrings.get(Res.string.player_theme_vinyl, language)
    }

    val label: String get() = label()
}

internal enum class AnimationSpeed(
    val key: String,
    val scale: Float,
) {
    Slow("slow", 1.5f),
    Normal("normal", 1.0f),
    Fast("fast", 0.6f),
    Off("off", 0f);

    fun label(language: AppLanguage = AppStrings.currentLanguage): String = when (this) {
        Slow -> AppStrings.get(Res.string.motion_slow, language)
        Normal -> AppStrings.get(Res.string.motion_normal, language)
        Fast -> AppStrings.get(Res.string.motion_fast, language)
        Off -> AppStrings.get(Res.string.motion_off, language)
    }

    val label: String get() = label()
}

internal data class Appearance(
    val mode: ThemeMode = ThemeMode.System,
    val accent: AccentChoice = AccentChoice.FollowCover,
    val saveMobileData: Boolean = false,
    val coverRadius: Int = DEFAULT_COVER_RADIUS_DP,
    val playerBackdropFromCover: Boolean = true,
    val glass: Boolean = true,
    val animation: AnimationSpeed = AnimationSpeed.Normal,
    val lyricsAppearance: LyricsAppearance = LyricsAppearance(),
    val fullViewTheme: FullViewTheme = FullViewTheme.Standard,
    val language: AppLanguage = AppLanguage.English,
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
                val fullViewKey = (value["fullViewTheme"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                val languageTag = (value["language"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                Appearance(
                    mode = mode,
                    accent = accent,
                    saveMobileData = saveData,
                    coverRadius = coverRadius,
                    playerBackdropFromCover = playerBackdrop,
                    glass = glass,
                    animation = animation,
                    lyricsAppearance = lyricsAppearanceFromJson(value["lyricsAppearance"]),
                    fullViewTheme = FullViewTheme.entries.firstOrNull { it.key == fullViewKey } ?: FullViewTheme.Standard,
                    language = AppLanguage.fromTag(languageTag),
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
        put("lyricsAppearance", settings.lyricsAppearance.toJson())
        put("fullViewTheme", settings.fullViewTheme.key)
        put("language", settings.language.tag)
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
