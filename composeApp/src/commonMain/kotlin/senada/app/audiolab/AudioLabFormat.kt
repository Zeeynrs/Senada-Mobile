package senada.app.audiolab

import androidx.compose.ui.graphics.Matrix
import kotlin.math.abs
import kotlin.math.roundToInt
import senada.app.nowplaying.statusText
import senada.audio.AudioEffects
import senada.audio.EqPreset
import senada.playback.SleepTimerState

internal val EqFrequencyLabels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

internal val SpeedChoices = listOf(0.75f, 1.0f, 1.25f, 1.5f)

internal const val AudioLabUnavailableMessage = "Sebagian efek suara di iPhone sedang disiapkan"

internal data class CardStatus(val text: String, val active: Boolean)

internal fun equalizerStatus(effects: AudioEffects): CardStatus = if (effects.equalizer.enabled) {
    CardStatus(text = "Aktif · ${effects.activePresetName()}", active = true)
} else {
    CardStatus(text = "Mati", active = false)
}

internal fun soundFxStatus(effects: AudioEffects): CardStatus {
    val activeEffects = buildList {
        if (effects.reverb.enabled) add("Reverb")
        if (effects.bassBoost.enabled) add("Bass boost")
        if (effects.loudnessMatch) add("Samakan volume")
    }
    return if (activeEffects.isNotEmpty()) {
        CardStatus(text = activeEffects.joinToString(" · "), active = true)
    } else {
        CardStatus(text = "Mati", active = false)
    }
}

internal fun speedStatus(effects: AudioEffects): CardStatus {
    val pitchText = if (effects.pitchLock) "Nada terkunci" else "Nada ikut"
    val active = abs(effects.speed - 1f) > 0.005f || !effects.pitchLock
    return CardStatus(text = "${formatSpeed(effects.speed)} · $pitchText", active = active)
}

internal fun audioQualityStatus(saveMobileData: Boolean): CardStatus = if (saveMobileData) {
    CardStatus(text = "Wi-Fi tinggi · seluler hemat", active = true)
} else {
    CardStatus(text = "Wi-Fi & seluler tinggi", active = false)
}

internal fun sleepTimerStatus(state: SleepTimerState): CardStatus {
    val activeText = state.statusText("menit")
    return if (activeText != null) {
        CardStatus(text = activeText, active = true)
    } else {
        CardStatus(text = "Mati", active = false)
    }
}

internal fun formatSpeed(speed: Float): String {
    val hundredths = (speed.coerceIn(0.5f, 2f) * 100f).roundToInt()
    val whole = hundredths / 100
    val frac = (hundredths % 100).toString().padStart(2, '0')
    return "$whole,$frac×"
}

/** Memformat gain dB dalam langkah 0,5 dB dengan tanda minus tipografi (−) dan koma desimal. */
internal fun formatDb(db: Float): String {
    val halfSteps = (db * 2f).roundToInt()
    if (halfSteps == 0) return "0"
    val sign = if (halfSteps > 0) "+" else "−"
    val absHalf = abs(halfSteps)
    val whole = absHalf / 2
    return if (absHalf % 2 == 0) "$sign$whole" else "$sign$whole,5"
}

internal fun formatPercent(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100f).roundToInt()}%"

internal fun snapHalfDb(value: Float, min: Float = -12f, max: Float = 12f): Float =
    ((value.coerceIn(min, max) * 2f).roundToInt() / 2f).coerceIn(min, max)

internal fun snapPercent(value: Float): Float =
    ((value.coerceIn(0f, 1f) * 100f).roundToInt() / 100f).coerceIn(0f, 1f)

internal fun bandContentDescription(index: Int, gainDb: Float): String {
    val hz = EqFrequencyLabels.getOrElse(index) { EqPreset.FrequenciesHz[index].toString() }
    return "$hz Hz, ${formatDb(gainDb)} dB"
}

internal fun selectPreset(effects: AudioEffects, preset: EqPreset): AudioEffects = effects.copy(
    equalizer = effects.equalizer.copy(presetId = preset.id, gains = preset.gains),
)

internal fun updateBandGain(effects: AudioEffects, bandIndex: Int, gainDb: Float): AudioEffects {
    if (bandIndex !in EqPreset.FrequenciesHz.indices) return effects
    val snapped = snapHalfDb(gainDb, -12f, 12f)
    val updated = effects.equalizer.gains.toMutableList().apply {
        while (size < EqPreset.FrequenciesHz.size) add(0f)
        this[bandIndex] = snapped
    }
    return effects.copy(
        equalizer = effects.equalizer.copy(presetId = EqPreset.CustomId, gains = updated),
    )
}

internal fun resetEqualizer(effects: AudioEffects): AudioEffects = effects.copy(
    equalizer = effects.equalizer.copy(
        presetId = EqPreset.Flat.id,
        gains = EqPreset.Flat.gains,
        preamp = 0f,
    ),
)

internal fun nextCustomPresetId(existing: List<EqPreset>): String {
    val used = existing.map { it.id }.toSet()
    var index = 1
    while ("custom-$index" in used) index++
    return "custom-$index"
}

internal fun saveCustomPreset(effects: AudioEffects, rawName: String): AudioEffects {
    val trimmed = rawName.trim()
    if (trimmed.isEmpty()) return effects
    val preset = EqPreset(
        id = nextCustomPresetId(effects.customPresets),
        name = trimmed,
        gains = effects.equalizer.gains,
    )
    return effects.copy(
        equalizer = effects.equalizer.copy(presetId = preset.id),
        customPresets = effects.customPresets + preset,
    )
}

internal fun deleteCustomPreset(effects: AudioEffects, presetId: String): AudioEffects {
    val remaining = effects.customPresets.filterNot { it.id == presetId }
    if (remaining.size == effects.customPresets.size) return effects
    val nextEqualizer = if (effects.equalizer.presetId == presetId) {
        val fallbackId = if (effects.equalizer.gains == EqPreset.Flat.gains) EqPreset.Flat.id else EqPreset.CustomId
        effects.equalizer.copy(presetId = fallbackId)
    } else {
        effects.equalizer
    }
    return effects.copy(equalizer = nextEqualizer, customPresets = remaining)
}

/** Matriks kemiringan horizontal ~12° untuk teks dekoratif miring "FX" saat font italic tidak dibundel. */
internal fun italicShearMatrix(): Matrix = Matrix().apply {
    values[Matrix.SkewX] = -0.21f
}
