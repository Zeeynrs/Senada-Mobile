package senada.audio

import coil3.PlatformContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal class AudioEffectsStore(private val read: () -> String?, private val write: (String) -> Unit) {
    fun load(): AudioEffects {
        val defaults = AudioEffects()
        return try {
            val json = Json.parseToJsonElement(read() ?: return defaults).jsonObject
            if (json["version"] != JsonPrimitive(VERSION)) return defaults
            AudioEffects(
                equalizer = json["equalizer"]?.jsonObject?.readEqualizer(defaults.equalizer) ?: defaults.equalizer,
                reverb = json["reverb"]?.jsonObject?.readReverb(defaults.reverb) ?: defaults.reverb,
                bassBoost = json["bassBoost"]?.jsonObject?.readBassBoost(defaults.bassBoost) ?: defaults.bassBoost,
                speed = json.number("speed", defaults.speed).bounded(0.5f, 2f, defaults.speed),
                pitchLock = json.flag("pitchLock", defaults.pitchLock),
                loudnessMatch = json.flag("loudnessMatch", defaults.loudnessMatch),
                customPresets = json["customPresets"]?.jsonArray?.map { it.jsonObject.readPreset() } ?: emptyList(),
            )
        } catch (_: Exception) {
            defaults
        }
    }

    fun save(effects: AudioEffects) {
        val equalizer = effects.equalizer.bounded()
        val reverb = effects.reverb.bounded()
        val bassBoost = effects.bassBoost.bounded()
        val json = buildJsonObject {
            put("version", VERSION)
            put("equalizer", buildJsonObject {
                put("enabled", equalizer.enabled)
                put("presetId", equalizer.presetId)
                put("gains", buildJsonArray { equalizer.gains.forEach { add(it) } })
                put("preamp", equalizer.preamp)
            })
            put("reverb", buildJsonObject {
                put("enabled", reverb.enabled)
                put("room", reverb.room.name)
                put("mix", reverb.mix)
                put("size", reverb.size)
            })
            put("bassBoost", buildJsonObject {
                put("enabled", bassBoost.enabled)
                put("strength", bassBoost.strength)
            })
            put("speed", effects.speed.bounded(0.5f, 2f, 1f))
            put("pitchLock", effects.pitchLock)
            put("loudnessMatch", effects.loudnessMatch)
            put("customPresets", buildJsonArray {
                for (preset in effects.customPresets) {
                    add(buildJsonObject {
                        put("id", preset.id)
                        put("name", preset.name)
                        put("gains", buildJsonArray { preset.gains.boundedGains().forEach { add(it) } })
                    })
                }
            })
        }
        write(json.toString())
    }

    private fun JsonObject.readEqualizer(defaults: EqualizerSettings) = EqualizerSettings(
        enabled = flag("enabled", defaults.enabled),
        presetId = get("presetId")?.string() ?: defaults.presetId,
        gains = get("gains")?.bandGains() ?: defaults.gains,
        preamp = number("preamp", defaults.preamp),
    ).bounded()

    private fun JsonObject.readReverb(defaults: ReverbSettings) = ReverbSettings(
        enabled = flag("enabled", defaults.enabled),
        room = get("room")?.string()?.let { ReverbRoom.valueOf(it) } ?: defaults.room,
        mix = number("mix", defaults.mix),
        size = number("size", defaults.size),
    ).bounded()

    private fun JsonObject.readBassBoost(defaults: BassBoostSettings) = BassBoostSettings(
        enabled = flag("enabled", defaults.enabled),
        strength = number("strength", defaults.strength),
    ).bounded()

    private fun JsonObject.readPreset() = EqPreset(
        id = getValue("id").string(),
        name = getValue("name").string(),
        gains = getValue("gains").bandGains().boundedGains(),
    )

    private fun JsonObject.flag(key: String, default: Boolean): Boolean {
        val value = get(key)?.jsonPrimitive ?: return default
        require(!value.isString)
        return value.boolean
    }

    private fun JsonObject.number(key: String, default: Float): Float = get(key)?.number() ?: default

    private fun JsonElement.number(): Float {
        val value = jsonPrimitive
        require(!value.isString && value.content.firstOrNull()?.let { it == '-' || it in '0'..'9' } == true)
        return value.float.also { require(!it.isNaN()) }
    }

    private fun JsonElement.string(): String {
        val value = jsonPrimitive
        require(value.isString)
        return value.content
    }

    private fun JsonElement.bandGains(): List<Float> {
        val bands = jsonArray
        require(bands.size == EqPreset.FrequenciesHz.size)
        return bands.map { it.number() }
    }

    private companion object {
        const val VERSION = 1
    }
}

internal expect fun audioEffectsStore(context: PlatformContext): AudioEffectsStore
