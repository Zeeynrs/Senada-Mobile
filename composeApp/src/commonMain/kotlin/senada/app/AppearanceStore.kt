package senada.app

import coil3.PlatformContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import senada.app.theme.AccentChoice

internal enum class ThemeMode(val label: String) {
    System("Ikuti sistem"), Light("Terang"), Dark("Gelap"),
}

internal data class Appearance(
    val mode: ThemeMode = ThemeMode.System,
    val accent: AccentChoice = AccentChoice.Senada,
    val saveMobileData: Boolean = false,
)

/** Satu nilai berversi agar mode, aksen, dan penghemat data disimpan bersama. */
internal class AppearanceStore(private val read: () -> String?, private val write: (String) -> Unit) {
    fun load(): Appearance {
        return try {
            val value = Json.parseToJsonElement(read() ?: return Appearance()).jsonObject
            val mode = ThemeMode.valueOf(value.getValue("mode").jsonPrimitive.content)
            val accent = when (value["version"]) {
                JsonPrimitive(1) -> {
                    val follow = value.getValue("followCover").jsonPrimitive
                    require(!follow.isString)
                    if (follow.boolean) AccentChoice.FollowCover else AccentChoice.Senada
                }
                JsonPrimitive(2) -> {
                    val choice = value.getValue("accent").jsonPrimitive
                    require(choice.isString)
                    AccentChoice.entries.first { it.key == choice.content }
                }
                else -> return Appearance()
            }
            val saveData = value["saveMobileData"] as? JsonPrimitive
            Appearance(mode, accent, saveData?.takeUnless { it.isString }?.booleanOrNull ?: false)
        } catch (_: Exception) {
            Appearance()
        }
    }

    fun save(settings: Appearance) = write(buildJsonObject {
        put("version", 2)
        put("mode", settings.mode.name)
        put("accent", settings.accent.key)
        put("saveMobileData", settings.saveMobileData)
    }.toString())
}

internal expect fun appearanceStore(context: PlatformContext): AppearanceStore
