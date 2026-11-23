package senada.audio

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

internal enum class ReverbRoom { Kamar, Studio, Aula, Katedral }

internal data class BassBoostSettings(
    val enabled: Boolean = false,
    val strength: Float = 0.40f,
) {
    internal fun bounded() = copy(strength = strength.bounded(0f, 1f, 0.40f))
}

/** User presets use the same band order and a stable, caller-supplied ID. */
internal data class EqPreset(val id: String, val name: String, val gains: List<Float>) {
    companion object {
        val FrequenciesHz = listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)
        val Flat = EqPreset("flat", "Datar", List(FrequenciesHz.size) { 0f })
        val BuiltIns = listOf(
            Flat,
            EqPreset("bass", "Bass+", listOf(7f, 6f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
            EqPreset("vocal", "Vokal", listOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, -1f)),
            EqPreset("acoustic", "Akustik", listOf(3f, 3f, 2f, 1f, 1f, 1f, 2f, 3f, 2f, 1f)),
            EqPreset("electronic", "Elektronik", listOf(5f, 4f, 1f, 0f, -2f, 1f, 1f, 3f, 4f, 5f)),
            EqPreset("night", "Malam", listOf(2f, 2f, 1f, 0f, -1f, -2f, -3f, -4f, -5f, -6f)),
        )
    }
}

internal fun List<Float>.boundedGains(): List<Float> = List(EqPreset.FrequenciesHz.size) { index ->
    (getOrNull(index) ?: 0f).bounded(-12f, 12f, 0f)
}

internal fun Float.bounded(min: Float, max: Float, fallback: Float): Float =
    if (isNaN()) fallback else coerceIn(min, max)
