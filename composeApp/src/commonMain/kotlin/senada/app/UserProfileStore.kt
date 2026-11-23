@file:OptIn(kotlin.time.ExperimentalTime::class)

package senada.app

import coil3.PlatformContext
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

internal data class UserProfile(val name: String, val photoPath: String?, val createdAt: Long)

internal class UserProfileStore(private val read: () -> String?, private val write: (String) -> Unit) {
    fun load(): UserProfile? = try {
        read()?.let { raw ->
            val json = Json.parseToJsonElement(raw).jsonObject
            require(json["version"] == JsonPrimitive(1))
            val name = json.getValue("name").jsonPrimitive.also { require(it.isString) }.content
            val photoPath = json["photoPath"]?.takeUnless { it == JsonNull }
                ?.jsonPrimitive?.also { require(it.isString) }?.content
            val createdAt = json.getValue("createdAt").jsonPrimitive.also { require(!it.isString) }.long
            UserProfile(normalizeProfileName(name), photoPath, createdAt)
        }
    } catch (_: Exception) {
        null
    }

    fun save(profile: UserProfile) = write(buildJsonObject {
        put("version", 1)
        put("name", normalizeProfileName(profile.name))
        put("photoPath", profile.photoPath)
        put("createdAt", profile.createdAt)
    }.toString())
}

internal class UserProfileController(
    private val store: UserProfileStore,
    now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val profile = MutableStateFlow(store.load() ?: UserProfile("", null, now()).also(store::save))
    val state = profile.asStateFlow()

    fun update(name: String, photoPath: String?) {
        val updated = profile.value.copy(name = normalizeProfileName(name), photoPath = photoPath)
        store.save(updated)
        profile.value = updated
    }
}

internal fun normalizeProfileName(name: String): String {
    val trimmed = name.trim()
    var end = 0
    repeat(40) {
        if (end == trimmed.length) return trimmed
        end += if (trimmed[end].isHighSurrogate() && trimmed.getOrNull(end + 1)?.isLowSurrogate() == true) 2 else 1
    }
    return trimmed.substring(0, end).trimEnd()
}

internal expect fun userProfileStore(context: PlatformContext): UserProfileStore
