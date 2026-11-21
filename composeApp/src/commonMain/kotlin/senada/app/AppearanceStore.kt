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

internal enum class ThemeMode(val label: String) {
    System("Ikuti sistem"), Light("Terang"), Dark("Gelap"),
}

internal data class Appearance(
    val mode: ThemeMode = ThemeMode.System,
    val followCover: Boolean = true,
    val saveMobileData: Boolean = false,
)

/** Satu nilai berversi agar mode dan pilihan sampul selalu disimpan bersama. */
internal class AppearanceStore(private val read: () -> String?, private val write: (String) -> Unit) {
    fun load(): Appearance {
        return try {
            val value = Json.parseToJsonElement(read() ?: return Appearance()).jsonObject
            require(value["version"] == JsonPrimitive(1))
            val mode = ThemeMode.valueOf(value.getValue("mode").jsonPrimitive.content)
            val follow = value.getValue("followCover").jsonPrimitive
            require(!follow.isString)
            val saveData = value["saveMobileData"] as? JsonPrimitive
            Appearance(mode, follow.boolean, saveData?.takeUnless { it.isString }?.booleanOrNull ?: false)
        } catch (_: Exception) {
            Appearance()
        }
    }

    fun save(settings: Appearance) = write(buildJsonObject {
        put("version", 1)
        put("mode", settings.mode.name)
        put("followCover", settings.followCover)
        put("saveMobileData", settings.saveMobileData)
    }.toString())
}

internal expect fun appearanceStore(context: PlatformContext): AppearanceStore
