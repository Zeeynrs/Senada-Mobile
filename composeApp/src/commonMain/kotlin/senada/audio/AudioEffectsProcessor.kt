package senada.audio

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * In-place DSP for interleaved PCM. Offsets count samples and lengths count frames.
 * One audio thread owns [process], [processShort], and [reset]. [update] may be called concurrently.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class AudioEffectsProcessor(
    private val sampleRate: Int,
    private val channels: Int,
    private val maxPcm16Frames: Int = 4096,
) {
    init {
        require(sampleRate > 0 && channels > 0)
        require(maxPcm16Frames in 1..Int.MAX_VALUE / channels)
    }

    private val pcmScratch = FloatArray(maxPcm16Frames * channels)
    private val pending = AtomicReference(EffectChain(DspSettings(), sampleRate, channels))
    private val fadeFrames = (sampleRate / 100).coerceAtLeast(1)
    private var active: EffectChain? = null
    private var next: EffectChain? = null
    private var fadeRemaining = 0

    /** Build the coefficients and fresh state on the caller's thread, then publish them together. */
    fun update(effects: AudioEffects) {
        val eq = effects.equalizer.takeIf { it.enabled }?.bounded()
        val settings = DspSettings(
            preamp = eq?.preamp ?: 0f,
            gains = eq?.gains ?: EqPreset.Flat.gains,
            bassStrength = effects.bassBoost.bounded().let { if (it.enabled) it.strength else 0f },
            reverb = effects.reverb.bounded().takeIf { it.enabled && it.mix > 0f },
        )
        var observed = pending.load()
        if (observed.settings == settings) return
        val chain = EffectChain(settings, sampleRate, channels)
        while (!pending.compareAndSet(observed, chain)) {
            observed = pending.load()
            if (observed.settings == settings) return
        }
    }

    fun process(buffer: FloatArray, offset: Int, frames: Int) {
        checkRange(buffer.size, offset, frames)
        if (frames == 0 || !prepareBlock()) return
        render(buffer, offset, frames)
    }

    /** Convert through one preallocated scratch buffer, chunking larger blocks without resizing. */
    fun processShort(buffer: ShortArray, offset: Int, frames: Int) {
        checkRange(buffer.size, offset, frames)
        if (frames == 0 || !prepareBlock()) return
        var position = offset
        var framesRemaining = if (next?.bypass == true) minOf(frames, fadeRemaining) else frames
        while (framesRemaining > 0) {
            val chunk = minOf(framesRemaining, maxPcm16Frames)
            val samples = chunk * channels
            for (index in 0 until samples) {
                pcmScratch[index] = buffer[position + index] / PCM_SCALE
            }
            render(pcmScratch, 0, chunk)
            for (index in 0 until samples) {
                buffer[position + index] = (pcmScratch[index] * PCM_SCALE).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            position += samples
            framesRemaining -= chunk
        }
    }

    fun reset() {
        pending.load().reset()
        active = null
        next = null
        fadeRemaining = 0
    }

    private fun checkRange(size: Int, offset: Int, frames: Int) {
        require(offset in 0..size && frames in 0..(size - offset) / channels)
    }

    private fun prepareBlock(): Boolean {
        val requested = pending.load()
        val current = active
        if (current == null || (next == null && current.bypass && requested.bypass)) {
            active = requested
            return !requested.bypass
        }
        if (next == null && current !== requested) {
            // Finish a fade before accepting another update so fast slider moves stay continuous.
            next = requested
            fadeRemaining = fadeFrames
        }
        return true
    }

    private fun render(buffer: FloatArray, offset: Int, frames: Int) {
        var position = offset
        var chain = checkNotNull(active)
        val incoming = next
        if (incoming != null) {
            val count = minOf(frames, fadeRemaining)
            repeat(count) {
                if (incoming.bypass && fadeRemaining == 1) {
                    // The last frame is fully dry and retains the original sample bits.
                    position += channels
                } else {
                    val blend = (fadeFrames - fadeRemaining + 1).toFloat() / fadeFrames
                    for (channel in 0 until channels) {
                        val sample = buffer[position]
                        val previous = chain.process(sample, channel)
                        val updated = if (incoming.bypass) sample else incoming.process(sample, channel)
                        buffer[position++] = (previous + (updated - previous) * blend).coerceIn(-1f, 1f)
                    }
                }
                fadeRemaining--
            }
            if (fadeRemaining == 0) {
                active = incoming
                chain = incoming
                next = null
            }
        }
        if (chain.bypass) return
        val end = offset + frames * channels
        while (position < end) {
            for (channel in 0 until channels) {
                buffer[position] = chain.process(buffer[position], channel)
                position++
            }
        }
    }

    private companion object {
        const val PCM_SCALE = 32768f
    }
}

private data class DspSettings(
    val preamp: Float = 0f,
    val gains: List<Float> = EqPreset.Flat.gains,
    val bassStrength: Float = 0f,
    val reverb: ReverbSettings? = null,
)

private class EffectChain(val settings: DspSettings, sampleRate: Int, channels: Int) {
    private val preampGain = 10.0.pow(settings.preamp / 20.0)
    private val filters = buildList {
        val nyquist = sampleRate / 2.0
        for ((index, frequency) in EqPreset.FrequenciesHz.withIndex()) {
            val gain = settings.gains[index]
            if (gain != 0f && frequency < nyquist) {
                add(Biquad.peaking(sampleRate, frequency, gain, channels))
            }
        }
        if (settings.bassStrength > 0f && 100 < nyquist) {
            add(Biquad.lowShelf(sampleRate, frequency = 100, settings.bassStrength * 12f, channels))
        }
    }.toTypedArray()
    private val reverb = settings.reverb?.let { SchroederReverb(sampleRate, channels, it) }
    val bypass = settings.preamp == 0f && filters.isEmpty() && reverb == null

    fun process(sample: Float, channel: Int): Float {
        val input = if (sample.isFinite()) sample else 0f
        if (bypass) return softLimit(input) // Only reached on the dry side of a crossfade.
        var value = input.coerceIn(-32f, 32f) * preampGain
        for (filter in filters) {
            value = filter.process(value, channel)
        }
        val dry = value.toFloat()
        return softLimit(reverb?.process(dry, channel) ?: dry)
    }

    fun reset() {
        for (filter in filters) filter.reset()
        reverb?.reset()
    }
}

private fun softLimit(value: Float): Float {
    val magnitude = abs(value)
    if (magnitude <= 0.9f) return value
    // Unity slope at the knee, approaching full scale asymptotically.
    val limited = 1f - 0.1f / (1f + (magnitude - 0.9f) / 0.1f)
    return if (value < 0f) -limited else limited
}
