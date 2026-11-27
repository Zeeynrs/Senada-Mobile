package senada.lyrics

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put
import senada.app.AppLanguage
import senada.app.AppStrings
import senada.resources.Res
import senada.resources.lyrics_align_center
import senada.resources.lyrics_align_left
import senada.resources.lyrics_highlight_bright
import senada.resources.lyrics_highlight_glow
import senada.resources.lyrics_highlight_karaoke
import senada.resources.lyrics_size_large
import senada.resources.lyrics_size_medium
import senada.resources.lyrics_size_small

internal enum class LyricsSize(val scale: Float) {
    Small(.85f), Medium(1f), Large(1.2f);

    fun label(language: AppLanguage = AppStrings.currentLanguage): String = when (this) {
        Small -> AppStrings.get(Res.string.lyrics_size_small, language)
        Medium -> AppStrings.get(Res.string.lyrics_size_medium, language)
        Large -> AppStrings.get(Res.string.lyrics_size_large, language)
    }

    val label: String get() = label()
}

internal enum class LyricsFont(val label: String) {
    Sans("Sans"), Serif("Serif"),
    ;

    fun label(@Suppress("UNUSED_PARAMETER") language: AppLanguage): String = label
}

internal enum class LyricsAlignment {
    Left, Center;

    fun label(language: AppLanguage = AppStrings.currentLanguage): String = when (this) {
        Left -> AppStrings.get(Res.string.lyrics_align_left, language)
        Center -> AppStrings.get(Res.string.lyrics_align_center, language)
    }

    val label: String get() = label()
}

internal enum class LyricsHighlight {
    Bright, Glow, Karaoke;

    fun label(language: AppLanguage = AppStrings.currentLanguage): String = when (this) {
        Bright -> AppStrings.get(Res.string.lyrics_highlight_bright, language)
        Glow -> AppStrings.get(Res.string.lyrics_highlight_glow, language)
        Karaoke -> AppStrings.get(Res.string.lyrics_highlight_karaoke, language)
    }

    val label: String get() = label()
}

internal data class LyricsAppearance(
    val size: LyricsSize = LyricsSize.Medium,
    val font: LyricsFont = LyricsFont.Sans,
    val alignment: LyricsAlignment = LyricsAlignment.Left,
    val highlight: LyricsHighlight = LyricsHighlight.Karaoke,
    val romanization: Boolean = false,
)

internal fun lyricsAppearanceFromJson(value: JsonElement?): LyricsAppearance {
    val json = value as? JsonObject ?: return LyricsAppearance()
    return LyricsAppearance(
        size = json.enumValue("size", LyricsSize.Medium),
        font = json.enumValue("font", LyricsFont.Sans),
        alignment = json.enumValue("alignment", LyricsAlignment.Left),
        highlight = json.enumValue("highlight", LyricsHighlight.Karaoke),
        romanization = (json["romanization"] as? JsonPrimitive)?.booleanOrNull ?: false,
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
    put("romanization", romanization)
}
