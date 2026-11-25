package senada.audio

import senada.app.AppLanguage
import senada.app.AppStrings
import senada.resources.Res
import senada.resources.eq_preset_acoustic
import senada.resources.eq_preset_bass
import senada.resources.eq_preset_custom
import senada.resources.eq_preset_electronic
import senada.resources.eq_preset_flat
import senada.resources.eq_preset_night
import senada.resources.eq_preset_vocal
import senada.resources.reverb_cathedral
import senada.resources.reverb_hall
import senada.resources.reverb_room
import senada.resources.reverb_studio

internal data class AudioEffects(
    val equalizer: EqualizerSettings = EqualizerSettings(),
    val reverb: ReverbSettings = ReverbSettings(),
    val bassBoost: BassBoostSettings = BassBoostSettings(),
    val speed: Float = 1f,
    val pitchLock: Boolean = true,
    val loudnessMatch: Boolean = false,
    val customPresets: List<EqPreset> = emptyList(),
) {
    internal fun bounded() = copy(
        equalizer = equalizer.bounded(),
        reverb = reverb.bounded(),
        bassBoost = bassBoost.bounded(),
        speed = speed.bounded(0.5f, 2f, 1f),
        customPresets = customPresets.map { it.copy(gains = it.gains.boundedGains()) },
    )

    internal fun allPresets(): List<EqPreset> = EqPreset.BuiltIns + customPresets

    internal fun activePreset(): EqPreset? = allPresets().firstOrNull { it.id == equalizer.presetId }

    internal fun activePresetName(language: AppLanguage = AppStrings.currentLanguage): String =
        activePreset()?.localizedName(language) ?: AppStrings.get(Res.string.eq_preset_custom, language)
}

/** The gains are authoritative, including when the user edits a selected preset. */
internal data class EqualizerSettings(
    val enabled: Boolean = false,
    val presetId: String = EqPreset.Flat.id,
    val gains: List<Float> = EqPreset.Flat.gains,
    val preamp: Float = 0f,
) {
    internal fun bounded() = copy(gains = gains.boundedGains(), preamp = preamp.bounded(-12f, 6f, 0f))
}

internal data class ReverbSettings(
    val enabled: Boolean = false,
    val room: ReverbRoom = ReverbRoom.Kamar,
    val mix: Float = 0.30f,
    val size: Float = 0.55f,
) {
    internal fun bounded() = copy(mix = mix.bounded(0f, 1f, 0.30f), size = size.bounded(0f, 1f, 0.55f))
}

internal enum class ReverbRoom {
    Kamar, Studio, Aula, Katedral;

    fun label(language: AppLanguage = AppStrings.currentLanguage): String = when (this) {
        Kamar -> AppStrings.get(Res.string.reverb_room, language)
        Studio -> AppStrings.get(Res.string.reverb_studio, language)
        Aula -> AppStrings.get(Res.string.reverb_hall, language)
        Katedral -> AppStrings.get(Res.string.reverb_cathedral, language)
    }
}

internal data class BassBoostSettings(
    val enabled: Boolean = false,
    val strength: Float = 0.40f,
) {
    internal fun bounded() = copy(strength = strength.bounded(0f, 1f, 0.40f))
}

/** User presets use the same band order and a stable, caller-supplied ID. */
internal data class EqPreset(val id: String, val name: String, val gains: List<Float>) {
    fun localizedName(language: AppLanguage = AppStrings.currentLanguage): String = when (id) {
        "flat" -> AppStrings.get(Res.string.eq_preset_flat, language)
        "bass" -> AppStrings.get(Res.string.eq_preset_bass, language)
        "vocal" -> AppStrings.get(Res.string.eq_preset_vocal, language)
        "acoustic" -> AppStrings.get(Res.string.eq_preset_acoustic, language)
        "electronic" -> AppStrings.get(Res.string.eq_preset_electronic, language)
        "night" -> AppStrings.get(Res.string.eq_preset_night, language)
        else -> name
    }

    companion object {
        const val CustomId = "custom"
        const val CustomName = "Custom"
        val FrequenciesHz = listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)
        val Flat = EqPreset("flat", "Flat", List(FrequenciesHz.size) { 0f })
        val BuiltIns = listOf(
            Flat,
            EqPreset("bass", "Bass+", listOf(7f, 6f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
            EqPreset("vocal", "Vocal", listOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, -1f)),
            EqPreset("acoustic", "Acoustic", listOf(3f, 3f, 2f, 1f, 1f, 1f, 2f, 3f, 2f, 1f)),
            EqPreset("electronic", "Electronic", listOf(5f, 4f, 1f, 0f, -2f, 1f, 1f, 3f, 4f, 5f)),
            EqPreset("night", "Night", listOf(2f, 2f, 1f, 0f, -1f, -2f, -3f, -4f, -5f, -6f)),
        )
    }
}

internal fun List<Float>.boundedGains(): List<Float> = List(EqPreset.FrequenciesHz.size) { index ->
    (getOrNull(index) ?: 0f).bounded(-12f, 12f, 0f)
}

internal fun Float.bounded(min: Float, max: Float, fallback: Float): Float =
    if (isNaN()) fallback else coerceIn(min, max)
