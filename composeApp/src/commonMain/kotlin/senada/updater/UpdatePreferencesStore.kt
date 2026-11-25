package senada.updater

import coil3.PlatformContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Versioned local persistence for updater settings; corrupt or legacy data safely falls back to defaults. */
internal class UpdatePreferencesStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
) {
    fun load(defaultChannel: UpdateChannel = UpdateChannel.Stable): UpdatePreferences {
        val raw = read() ?: return UpdatePreferences(channel = defaultChannel)
        return try {
            val json = Json.parseToJsonElement(raw).jsonObject
            val version = (json["version"] as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toIntOrNull()
            if (version != 1) return UpdatePreferences(channel = defaultChannel)
            val channelKey = (json["channel"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val channel = UpdateChannel.entries.firstOrNull { it.key == channelKey } ?: defaultChannel
            val autoCheck = (json["autoCheckEnabled"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: true
            val lastChecked = (json["lastCheckedAtMs"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
            val dismissed = json["dismissedTag"]?.takeUnless { it == JsonNull }
                ?.let { it as? JsonPrimitive }?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
            UpdatePreferences(
                channel = channel,
                autoCheckEnabled = autoCheck,
                lastCheckedAtMs = lastChecked,
                dismissedTag = dismissed,
            )
        } catch (_: Exception) {
            UpdatePreferences(channel = defaultChannel)
        }
    }

    fun save(prefs: UpdatePreferences) {
        write(
            buildJsonObject {
                put("version", 1)
                put("channel", prefs.channel.key)
                put("autoCheckEnabled", prefs.autoCheckEnabled)
                put("lastCheckedAtMs", prefs.lastCheckedAtMs)
                put("dismissedTag", prefs.dismissedTag)
            }.toString(),
        )
    }
}

internal expect fun updatePreferencesStore(context: PlatformContext): UpdatePreferencesStore
