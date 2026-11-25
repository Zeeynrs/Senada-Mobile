package senada.app.audiolab

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import senada.app.AppLanguage
import senada.audio.AudioEffects
import senada.audio.BassBoostSettings
import senada.audio.EqPreset
import senada.audio.EqualizerSettings
import senada.audio.ReverbRoom
import senada.audio.ReverbSettings
import senada.playback.SleepTimerState

class AudioLabFormatTest {
    @Test
    fun formatsSpeedDbAndPercentInEnglishAndIndonesianStyles() {
        assertEquals("0.75×", formatSpeed(0.75f))
        assertEquals("1.00×", formatSpeed(1f))
        assertEquals("1.25×", formatSpeed(1.25f))
        assertEquals("1.50×", formatSpeed(1.5f))
        assertEquals("0,75×", formatSpeed(0.75f, AppLanguage.Indonesian))

        assertEquals("0", formatDb(0f))
        assertEquals("0", formatDb(-0.0f))
        assertEquals("+7", formatDb(7f))
        assertEquals("+1.5", formatDb(1.5f))
        assertEquals("+1,5", formatDb(1.5f, AppLanguage.Indonesian))
        assertEquals("−2", formatDb(-2f))
        assertEquals("−2.5", formatDb(-2.5f))
        assertEquals("−12", formatDb(-12f))

        assertEquals("0%", formatPercent(0f))
        assertEquals("30%", formatPercent(0.30f))
        assertEquals("55%", formatPercent(0.55f))
        assertEquals("100%", formatPercent(1f))

        assertEquals("31 Hz, +7 dB", bandContentDescription(0, 7f))
        assertEquals("1k Hz, −1.5 dB", bandContentDescription(5, -1.5f))
        assertEquals("1k Hz, −1,5 dB", bandContentDescription(5, -1.5f, AppLanguage.Indonesian))
        assertEquals("16k Hz, 0 dB", bandContentDescription(9, 0f))
    }

    @Test
    fun snapsGainToHalfDbWithinBounds() {
        assertEquals(0.5f, snapHalfDb(0.3f))
        assertEquals(0f, snapHalfDb(0.2f))
        assertEquals(-2.5f, snapHalfDb(-2.6f))
        assertEquals(12f, snapHalfDb(15f))
        assertEquals(-12f, snapHalfDb(-15f))
        assertEquals(6f, snapHalfDb(8f, -12f, 6f))
    }

    @Test
    fun computesCardStatusesFromSettings() {
        val defaults = AudioEffects()
        assertEquals(CardStatus("Off", active = false), equalizerStatus(defaults))
        assertEquals(CardStatus("Off", active = false), soundFxStatus(defaults))
        assertEquals(CardStatus("1.00× · Pitch locked", active = false), speedStatus(defaults))
        assertEquals(CardStatus("High on Wi-Fi & cellular", active = false), audioQualityStatus(false))
        assertEquals(CardStatus("High on Wi-Fi · saver on cellular", active = true), audioQualityStatus(true))
        assertEquals(CardStatus("Off", active = false), sleepTimerStatus(SleepTimerState.Off))

        val bass = EqPreset.BuiltIns.first { it.id == "bass" }
        val active = AudioEffects(
            equalizer = EqualizerSettings(enabled = true, presetId = bass.id, gains = bass.gains),
            reverb = ReverbSettings(enabled = true, room = ReverbRoom.Aula),
            bassBoost = BassBoostSettings(enabled = true),
            speed = 1.25f,
            pitchLock = false,
        )
        assertEquals(CardStatus("On · Bass+", active = true), equalizerStatus(active))
        assertEquals(CardStatus("Reverb · Bass boost", active = true), soundFxStatus(active))
        assertEquals(CardStatus("1.25× · Pitch follows", active = true), speedStatus(active))
        assertEquals(CardStatus("Stops in 15 minutes", active = true), sleepTimerStatus(SleepTimerState.CountingDown(15)))
        assertEquals(CardStatus("Stop at end of track", active = true), sleepTimerStatus(SleepTimerState.EndOfTrack))
        assertEquals(CardStatus("Berhenti dalam 15 menit", active = true), sleepTimerStatus(SleepTimerState.CountingDown(15), AppLanguage.Indonesian))

        val reverbOnly = defaults.copy(reverb = ReverbSettings(enabled = true))
        assertEquals(CardStatus("Reverb", active = true), soundFxStatus(reverbOnly))
        val bassOnly = defaults.copy(bassBoost = BassBoostSettings(enabled = true))
        assertEquals(CardStatus("Bass boost", active = true), soundFxStatus(bassOnly))
        assertEquals(CardStatus("Match volume", active = true), soundFxStatus(defaults.copy(loudnessMatch = true)))
    }

    @Test
    fun manualBandEditSelectsCustomPresetAndCanBeSavedResetAndDeleted() {
        val bass = EqPreset.BuiltIns.first { it.id == "bass" }
        val withBass = selectPreset(AudioEffects(equalizer = EqualizerSettings(enabled = true)), bass)
        assertEquals("Bass+", withBass.activePresetName())

        val edited = updateBandGain(withBass, bandIndex = 4, gainDb = 3.3f)
        assertEquals(EqPreset.CustomId, edited.equalizer.presetId)
        assertNull(edited.activePreset())
        assertEquals(EqPreset.CustomName, edited.activePresetName())
        assertEquals(3.5f, edited.equalizer.gains[4])

        // Nama kosong diabaikan.
        assertEquals(edited, saveCustomPreset(edited, "   "))

        val saved = saveCustomPreset(edited, "  Studio Malam  ")
        assertEquals(1, saved.customPresets.size)
        val custom = saved.customPresets.single()
        assertEquals("custom-1", custom.id)
        assertEquals("Studio Malam", custom.name)
        assertEquals(edited.equalizer.gains, custom.gains)
        assertEquals("Studio Malam", saved.activePresetName())

        // Preset kedua mendapat ID unik.
        val savedSecond = saveCustomPreset(saved, "Podcast")
        assertEquals(listOf("custom-1", "custom-2"), savedSecond.customPresets.map { it.id })

        // Menghapus preset aktif mempertahankan gain sebagai Kustom.
        val afterDelete = deleteCustomPreset(saved, custom.id)
        assertTrue(afterDelete.customPresets.isEmpty())
        assertEquals(EqPreset.CustomId, afterDelete.equalizer.presetId)
        assertEquals(custom.gains, afterDelete.equalizer.gains)

        // Atur ulang kembali ke Datar dan preamp 0 dB tanpa mematikan sakelar EQ.
        val withPreamp = afterDelete.copy(equalizer = afterDelete.equalizer.copy(preamp = -4f))
        val reset = resetEqualizer(withPreamp)
        assertTrue(reset.equalizer.enabled)
        assertEquals(EqPreset.Flat.id, reset.equalizer.presetId)
        assertEquals(EqPreset.Flat.gains, reset.equalizer.gains)
        assertEquals(0f, reset.equalizer.preamp)
        assertFalse(soundFxStatus(reset).active)
    }

    @Test
    fun italicShearMatrixSlantsTopRightLikeItalicText() {
        val matrix = italicShearMatrix()
        val topPoint = matrix.map(Offset(0f, -50f))
        val bottomPoint = matrix.map(Offset(0f, 50f))
        assertTrue(topPoint.x > 0f, "top should slant right: $topPoint")
        assertTrue(bottomPoint.x < 0f, "bottom should slant left: $bottomPoint")
    }
}
