package senada.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

class AudioEffectsProcessorTest {
    @Test
    fun bypass_preserves_float_bits_and_pcm_values() {
        val processor = AudioEffectsProcessor(SAMPLE_RATE, channels = 2)
        val samples = floatArrayOf(-0f, Float.fromBits(0x7fc01234), -2f, 2f, -0.43f, 0.17f)
        val expected = IntArray(samples.size) { samples[it].toRawBits() }
        processor.process(samples, offset = 0, frames = 3)
        assertContentEquals(expected, IntArray(samples.size) { samples[it].toRawBits() })

        processor.update(allEffects())
        processor.process(FloatArray(FADE_FRAMES * 2), offset = 0, frames = FADE_FRAMES)
        processor.update(AudioEffects(equalizer = EqualizerSettings(enabled = true)))
        val fadeSamples = FADE_FRAMES * 2
        val fadeAndTail = FloatArray(fadeSamples + samples.size) { 0.1f }
        samples.copyInto(fadeAndTail, destinationOffset = fadeSamples)
        processor.process(fadeAndTail, offset = 0, frames = FADE_FRAMES + 3)
        assertContentEquals(expected, IntArray(samples.size) { fadeAndTail[fadeSamples + it].toRawBits() })
        processor.process(samples, offset = 0, frames = 3)
        assertContentEquals(expected, IntArray(samples.size) { samples[it].toRawBits() })

        val pcm = shortArrayOf(Short.MIN_VALUE, -1, 1, Short.MAX_VALUE)
        val untouched = pcm.copyOf()
        processor.processShort(pcm, offset = 0, frames = 2)
        assertContentEquals(untouched, pcm)
    }

    @Test
    fun each_equalizer_band_boosts_its_center_frequency() {
        val frequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        for ((band, frequency) in frequencies.withIndex()) {
            val effects = AudioEffects(equalizer = EqualizerSettings(
                enabled = true,
                gains = List(10) { if (it == band) 6f else 0f },
            ))
            val gain = measureGain(effects, frequency)
            assertTrue(abs(gain - 6) < 0.5, "Band $frequency Hz: $gain dB")
            if (frequency == 1000) {
                assertTrue(abs(measureGain(effects, 125)) < 0.2)
                assertTrue(abs(measureGain(effects, 8000)) < 0.2)
            }
        }
    }

    @Test
    fun bass_preset_and_shelf_boost_only_the_low_end() {
        val preset = EqPreset.BuiltIns.first { it.name == "Bass+" }
        val bassPreset = AudioEffects(equalizer = EqualizerSettings(
            enabled = true, presetId = preset.id, gains = preset.gains,
        ))
        assertTrue(measureGain(bassPreset, 62) > 6)
        assertTrue(abs(measureGain(bassPreset, 4000)) < 0.1)

        val shelf = AudioEffects(bassBoost = BassBoostSettings(enabled = true, strength = 1f))
        assertTrue(abs(measureGain(shelf, 20) - 12) < 0.1)
        assertTrue(abs(measureGain(shelf, 100) - 6) < 0.1)
        assertTrue(abs(measureGain(shelf, 4000)) < 0.1)
    }

    @Test
    fun limiter_is_soft_and_bounds_both_polarities() {
        val processor = AudioEffectsProcessor(SAMPLE_RATE, 1)
        processor.update(AudioEffects(equalizer = EqualizerSettings(enabled = true, preamp = 6f)))
        val samples = floatArrayOf(-2f, -1f, -0.6f, 0.6f, 1f, 2f)
        processor.process(samples, offset = 0, frames = samples.size)
        assertTrue(samples.all { abs(it) <= 1f })
        assertTrue(samples[3] in 0.9f..<samples[5])
        assertTrue((1 until samples.size).all { samples[it - 1] < samples[it] })
    }

    @Test
    fun extreme_parameters_keep_all_channels_finite_and_bounded() {
        val random = Random(42)
        for (room in ReverbRoom.entries) {
            val processor = AudioEffectsProcessor(SAMPLE_RATE, 2)
            processor.update(allEffects().copy(reverb = ReverbSettings(true, room, 0.8f, 1f)))
            val samples = FloatArray(SAMPLE_RATE * 2) { random.nextFloat() * 4f - 2f }
            processor.process(samples, offset = 0, frames = SAMPLE_RATE)
            assertTrue(samples.all { it.isFinite() && abs(it) <= 1f }, room.name)
        }
    }

    @Test
    fun midstream_updates_smooth_the_boundary_and_limit_overshoot() {
        val processor = AudioEffectsProcessor(SAMPLE_RATE, 1)
        val samples = FloatArray(2400) { 0.1f }
        processor.process(samples, offset = 0, frames = 600)
        processor.update(AudioEffects(equalizer = EqualizerSettings(enabled = true, preamp = 6f)))
        processor.process(samples, offset = 600, frames = 600)
        processor.update(AudioEffects(equalizer = EqualizerSettings(enabled = true, preamp = -12f)))
        processor.process(samples, offset = 1200, frames = 1200)
        assertTrue(abs(samples[1199] - 0.1995f) < 0.001f)
        assertTrue(abs(samples.last() - 0.0251f) < 0.001f)
        assertTrue(samples.all { it in 0.025f..0.200f })
        for (index in 1 until samples.size) {
            assertTrue(abs(samples[index] - samples[index - 1]) < MAX_SAMPLE_JUMP, "Jump at sample $index")
        }
    }

    @Test
    fun switching_off_bass_eq_and_reverb_fades_across_blocks() {
        val processor = AudioEffectsProcessor(SAMPLE_RATE, 1)
        val preset = EqPreset.BuiltIns.first { it.name == "Bass+" }
        processor.update(AudioEffects(
            equalizer = EqualizerSettings(enabled = true, presetId = preset.id, gains = preset.gains),
            reverb = ReverbSettings(enabled = true, mix = 0.7f),
        ))
        val dry = 0.1f
        val halfFade = FADE_FRAMES / 2
        val warmup = FloatArray(halfFade)
        repeat(120) {
            warmup.fill(dry)
            processor.process(warmup, offset = 0, frames = warmup.size)
        }
        val lastWetSample = warmup.last()
        assertTrue(abs(lastWetSample - dry) > MAX_SAMPLE_JUMP, "Warm reverb must differ from dry input")

        processor.update(AudioEffects())
        val fade = FloatArray(FADE_FRAMES) { dry }
        processor.process(fade, offset = 0, frames = halfFade)
        processor.process(fade, offset = halfFade, frames = halfFade)
        assertTrue(abs(fade.first() - lastWetSample) < MAX_SAMPLE_JUMP, "Jump at bypass boundary")
        for (index in 1 until fade.size) {
            assertTrue(abs(fade[index] - fade[index - 1]) < MAX_SAMPLE_JUMP, "Jump at fade sample $index")
        }
        assertEquals(dry.toRawBits(), fade.last().toRawBits())

        val dryBlock = floatArrayOf(-0f, Float.fromBits(0x7fc01234), -2f, 2f, -0.98f, 0.98f)
        val bits = IntArray(dryBlock.size) { dryBlock[it].toRawBits() }
        processor.process(dryBlock, offset = 0, frames = dryBlock.size)
        assertContentEquals(bits, IntArray(dryBlock.size) { dryBlock[it].toRawBits() })
    }

    @Test
    fun reverb_has_a_room_dependent_tail_and_reset_clears_all_state() {
        val roomTails = ReverbRoom.entries.map { room ->
            val processor = AudioEffectsProcessor(SAMPLE_RATE, channels = 2)
            processor.update(allEffects().copy(reverb = ReverbSettings(true, room, 0.7f, 0.55f)))
            val impulse = FloatArray(4800 * 2).also { it[0] = 0.1f }
            processor.process(impulse, offset = 0, frames = 4800)
            assertTrue((2000 until impulse.size step 2).any { abs(impulse[it]) > 0.0001f })
            assertTrue((1 until impulse.size step 2).all { impulse[it] == 0f })
            processor.reset()
            val silence = FloatArray(4800 * 2)
            processor.process(silence, offset = 0, frames = 4800)
            assertTrue(silence.all { it == 0f })
            impulse
        }
        for (index in 1 until roomTails.size) {
            assertTrue(!roomTails[index - 1].contentEquals(roomTails[index]))
        }
    }

    @Test
    fun pcm_conversion_handles_offsets_and_blocks_larger_than_scratch_capacity() {
        val samples = shortArrayOf(905, -1200, 300, 800, -210, -900, 89, 500, 76, -376)
        val floats = FloatArray(samples.size) { samples[it] / 32768f }
        val effects = AudioEffects(equalizer = EqualizerSettings(
            enabled = true,
            gains = List(10) { if (it == 5) 6f else 0f },
            preamp = 6f,
        ))
        val pcm = AudioEffectsProcessor(SAMPLE_RATE, 2, maxPcm16Frames = 2)
        val floatingPoint = AudioEffectsProcessor(SAMPLE_RATE, 2)
        pcm.update(effects)
        floatingPoint.update(effects)

        pcm.processShort(samples, offset = 1, frames = 4)
        floatingPoint.process(floats, offset = 1, frames = 4)
        assertContentEquals(ShortArray(floats.size) { (floats[it] * 32768f).roundToInt().toShort() }, samples)
    }

    @Test
    fun pcm_bypass_fades_across_scratch_chunks() {
        val processor = AudioEffectsProcessor(SAMPLE_RATE, 1, maxPcm16Frames = 127)
        val dry = 32_000.toShort()
        processor.update(AudioEffects(equalizer = EqualizerSettings(enabled = true, preamp = -12f)))
        val warmup = ShortArray(FADE_FRAMES) { dry }
        processor.processShort(warmup, offset = 0, frames = warmup.size)

        processor.update(AudioEffects())
        val samples = ShortArray(FADE_FRAMES + 16) { dry }
        processor.processShort(samples, offset = 0, frames = samples.size)
        assertTrue(abs(samples.first() - warmup.last()) < MAX_SAMPLE_JUMP * 32768)
        for (index in 1 until FADE_FRAMES) {
            assertTrue(abs(samples[index] - samples[index - 1]) < MAX_SAMPLE_JUMP * 32768)
        }
        assertTrue((FADE_FRAMES - 1 until samples.size).all { samples[it] == dry })
    }

    @Test
    fun reports_processing_time_for_one_second_of_48_khz_stereo() {
        val processor = AudioEffectsProcessor(SAMPLE_RATE, channels = 2)
        processor.update(allEffects())
        val input = FloatArray(SAMPLE_RATE * 2) { index ->
            (0.1 * sin(2 * PI * (if (index % 2 == 0) 311 else 443) * (index / 2) / SAMPLE_RATE)).toFloat()
        }
        val buffer = input.copyOf()
        repeat(5) {
            input.copyInto(buffer)
            processor.process(buffer, offset = 0, frames = SAMPLE_RATE)
        }
        val timings = List(5) {
            input.copyInto(buffer)
            measureTime { processor.process(buffer, offset = 0, frames = SAMPLE_RATE) }
        }
        println("DSP 48 kHz stereo, 1 s, EQ + preamp + bass boost + reverb + limiter: median ${timings.sorted()[2]}")
        assertTrue(buffer.all { it.isFinite() && abs(it) <= 1f })
    }

    private fun measureGain(effects: AudioEffects, frequency: Int): Double {
        val input = FloatArray(SAMPLE_RATE) { (0.04 * sin(2 * PI * frequency * it / SAMPLE_RATE)).toFloat() }
        val output = input.copyOf()
        AudioEffectsProcessor(SAMPLE_RATE, 1).apply {
            update(effects)
            process(output, offset = 0, frames = output.size)
        }
        var inputPower = 0.0
        var outputPower = 0.0
        for (index in SAMPLE_RATE / 2 until SAMPLE_RATE) {
            inputPower += input[index].toDouble() * input[index]
            outputPower += output[index].toDouble() * output[index]
        }
        return 10 * log10(outputPower / inputPower)
    }

    private fun allEffects() = AudioEffects(
        equalizer = EqualizerSettings(enabled = true, gains = List(10) { if (it % 2 == 0) 12f else -12f }, preamp = 6f),
        reverb = ReverbSettings(enabled = true, room = ReverbRoom.Katedral),
        bassBoost = BassBoostSettings(enabled = true, strength = 1f),
    )

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val FADE_FRAMES = SAMPLE_RATE / 100
        const val MAX_SAMPLE_JUMP = 0.002f
    }
}
