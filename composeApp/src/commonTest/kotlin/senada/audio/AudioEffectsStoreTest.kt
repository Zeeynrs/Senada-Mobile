package senada.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class AudioEffectsStoreTest {
    @Test
    fun defaults_and_builtin_gains_match_the_audio_lab() {
        val defaults = AudioEffects()
        assertEquals(EqualizerSettings(false, "flat", List(10) { 0f }, 0f), defaults.equalizer)
        assertEquals(ReverbSettings(false, ReverbRoom.Kamar, 0.30f, 0.55f), defaults.reverb)
        assertEquals(BassBoostSettings(false, 0.40f), defaults.bassBoost)
        assertEquals(1f, defaults.speed)
        assertEquals(true, defaults.pitchLock)
        assertEquals(false, defaults.loudnessMatch)
        assertEquals(emptyList(), defaults.customPresets)
        val expected = mapOf(
            "Flat" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            "Bass+" to listOf(7, 6, 4, 2, 0, 0, 0, 0, 0, 0),
            "Vocal" to listOf(-2, -1, 0, 2, 4, 4, 3, 1, 0, -1),
            "Acoustic" to listOf(3, 3, 2, 1, 1, 1, 2, 3, 2, 1),
            "Electronic" to listOf(5, 4, 1, 0, -2, 1, 1, 3, 4, 5),
            "Night" to listOf(2, 2, 1, 0, -1, -2, -3, -4, -5, -6),
        )
        assertEquals(expected.keys.toList(), EqPreset.BuiltIns.map { it.name })
        assertEquals(expected, EqPreset.BuiltIns.associate { it.name to it.gains.map(Float::toInt) })
        assertEquals(
            listOf("Datar", "Bass+", "Vokal", "Akustik", "Elektronik", "Malam"),
            EqPreset.BuiltIns.map { it.localizedName(senada.app.AppLanguage.Indonesian) },
        )
    }

    @Test
    fun round_trip_preserves_selection_parameters_and_user_presets() {
        var saved: String? = null
        fun open() = AudioEffectsStore({ saved }, { saved = it })
        val custom = EqPreset("custom-1", "Suaraku ♪", List(10) { it - 5.5f })
        val effects = AudioEffects(
            equalizer = EqualizerSettings(true, custom.id, custom.gains, -3f),
            reverb = ReverbSettings(true, ReverbRoom.Aula, 0.28f, 0.75f),
            bassBoost = BassBoostSettings(true, 0.8f),
            speed = 1.25f,
            pitchLock = false,
            loudnessMatch = true,
            customPresets = listOf(custom),
        )
        assertEquals(AudioEffects(), open().load())
        open().save(effects)
        assertEquals(JsonPrimitive(1), Json.parseToJsonElement(requireNotNull(saved)).jsonObject["version"])
        assertEquals(effects, open().load())
    }

    @Test
    fun old_settings_without_loudness_matching_keep_it_off() {
        val store = AudioEffectsStore({ """{"version":1,"speed":1.25,"bassBoost":{"enabled":true}}""" }, {})

        assertEquals(AudioEffects(speed = 1.25f, bassBoost = BassBoostSettings(enabled = true)), store.load())
    }

    @Test
    fun clamps_out_of_range_numbers_on_load_and_save() {
        var saved = """
            {"version":1,"equalizer":{"enabled":true,"preamp":-100,"gains":[-20,20,0,0,0,0,0,0,0,0]},
              "reverb":{"mix":1.5,"size":-0.8},"bassBoost":{"strength":2},"speed":0.1,
              "customPresets":[{"id":"custom-1","name":"Naik","gains":[42,0,0,0,0,0,0,0,0,0]}]}
        """.trimIndent()
        val store = AudioEffectsStore({ saved }, { saved = it })
        val effects = store.load()
        assertEquals(listOf(-12f, 12f) + List(8) { 0f }, effects.equalizer.gains)
        assertEquals(-12f, effects.equalizer.preamp)
        assertEquals(1f, effects.reverb.mix)
        assertEquals(0f, effects.reverb.size)
        assertEquals(1f, effects.bassBoost.strength)
        assertEquals(0.5f, effects.speed)
        assertEquals(12f, effects.customPresets.single().gains.first())

        store.save(effects.copy(
            equalizer = effects.equalizer.copy(preamp = 50f, gains = listOf(Float.NaN, 100f)),
            speed = 20f,
        ))
        val clamped = store.load()
        assertEquals(6f, clamped.equalizer.preamp)
        assertEquals(listOf(0f, 12f) + List(8) { 0f }, clamped.equalizer.gains)
        assertEquals(2f, clamped.speed)
    }

    @Test
    fun invalid_json_and_unknown_versions_restore_defaults() {
        for (invalid in listOf(
            "{",
            "[]",
            """{"version":"1"}""",
            """{"version":2,"speed":1.5}""",
            """{"version":1,"equalizer":{"enabled":"true"}}""",
            """{"version":1,"equalizer":{"gains":[1,2]}}""",
            """{"version":1,"reverb":{"room":"Unknown"}}""",
        )) {
            assertEquals(AudioEffects(), AudioEffectsStore({ invalid }, {}).load(), invalid)
        }
        assertEquals(AudioEffects(), AudioEffectsStore({ error("gagal membaca") }, {}).load())
    }
}
