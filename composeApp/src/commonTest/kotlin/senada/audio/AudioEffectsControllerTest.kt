package senada.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioEffectsControllerTest {
    @Test
    fun loadsSettingsAndPersistsEdits() {
        var saved = """{"version":1,"speed":1.25,"pitchLock":false}"""
        val store = AudioEffectsStore({ saved }, { saved = it })
        val controller = AudioEffectsController(store)
        assertEquals(AudioEffects(speed = 1.25f, pitchLock = false), controller.effects.value)

        controller.update { it.copy(bassBoost = BassBoostSettings(enabled = true)) }
        val expected = AudioEffects(speed = 1.25f, pitchLock = false, bassBoost = BassBoostSettings(enabled = true))
        assertEquals(expected, controller.effects.value)
        assertEquals(expected, AudioEffectsController(store).effects.value)

        controller.update { it.copy(speed = 1f) }
        assertEquals(expected.copy(speed = 1f), store.load())
    }

    @Test
    fun fallsBackToDefaultsForCorruptSettings() {
        val controller = AudioEffectsController(AudioEffectsStore({ """{"version":1,"speed":"rusak"}""" }, {}))
        assertEquals(AudioEffects(), controller.effects.value)
    }

    @Test
    fun publishesTheSameBoundsThatArePersisted() {
        var saved: String? = null
        val store = AudioEffectsStore({ saved }, { saved = it })
        val controller = AudioEffectsController(store)
        controller.update { it.copy(speed = 3f, reverb = ReverbSettings(mix = -1f)) }
        val expected = AudioEffects(speed = 2f, reverb = ReverbSettings(mix = 0f))
        assertEquals(expected, controller.effects.value)
        assertEquals(expected, store.load())
    }
}
