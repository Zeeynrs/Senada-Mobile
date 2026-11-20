package senada.catalog.youtube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Menelusuri balasan Innertube; kunci String untuk objek, Int untuk array. Null bila jalurnya tidak ada. */
internal fun JsonElement?.at(vararg path: Any): JsonElement? {
    var current = this
    for (key in path) {
        current = when {
            key is String && current is JsonObject -> current[key]
            key is Int && current is JsonArray -> current.getOrNull(key)
            else -> null
        }
    }
    return current
}

internal fun JsonElement?.string(vararg path: Any): String? =
    (at(*path) as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonElement?.array(vararg path: Any): List<JsonElement> =
    at(*path) as? JsonArray ?: emptyList()
