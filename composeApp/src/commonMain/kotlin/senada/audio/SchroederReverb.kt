package senada.audio

import kotlin.math.roundToInt

/** Four parallel damped feedback combs followed by two serial all-pass diffusers per channel. */
internal class SchroederReverb(sampleRate: Int, channels: Int, settings: ReverbSettings) {
    private val mix = settings.mix
    private val dryMix = 1f - mix
    private val room = when (settings.room) {
        ReverbRoom.Kamar -> Room(delayScale = 0.8, feedback = 0.64f, damping = 0.50f)
        ReverbRoom.Studio -> Room(delayScale = 1.0, feedback = 0.72f, damping = 0.42f)
        ReverbRoom.Aula -> Room(delayScale = 1.4, feedback = 0.82f, damping = 0.33f)
        ReverbRoom.Katedral -> Room(delayScale = 1.8, feedback = 0.90f, damping = 0.25f)
    }
    private val sizeScale = room.delayScale * (0.7 + 0.6 * settings.size)
    private val feedback = room.feedback * (0.72f + 0.28f * settings.size)
    private val combs = Array(channels) { channel ->
        Array(COMB_MS.size) { index -> Comb(delaySamples(COMB_MS[index], sampleRate, channel), feedback, room.damping) }
    }
    private val diffusers = Array(channels) { channel ->
        Array(DIFFUSER_MS.size) { index -> AllPass(delaySamples(DIFFUSER_MS[index], sampleRate, channel)) }
    }

    fun process(input: Float, channel: Int): Float {
        var sum = 0f
        for (comb in combs[channel]) {
            sum += comb.process(input)
        }
        var wet = sum * 0.25f
        for (diffuser in diffusers[channel]) {
            wet = diffuser.process(wet)
        }
        return input * dryMix + wet * mix
    }

    fun reset() {
        for (channel in combs.indices) {
            for (comb in combs[channel]) comb.reset()
            for (diffuser in diffusers[channel]) diffuser.reset()
        }
    }

    private fun delaySamples(ms: Double, sampleRate: Int, channel: Int): Int =
        ((ms * sizeScale + 0.53 * channel) * sampleRate / 1000).roundToInt().coerceAtLeast(1)

    private class Room(val delayScale: Double, val feedback: Float, val damping: Float)

    private class Comb(length: Int, private val feedback: Float, private val damping: Float) {
        private val delay = FloatArray(length)
        private var cursor = 0
        private var lowPass = 0f

        fun process(input: Float): Float {
            val delayed = delay[cursor]
            lowPass = (1f - damping) * delayed + damping * lowPass
            delay[cursor] = input + feedback * lowPass
            if (++cursor == delay.size) cursor = 0
            return delayed
        }

        fun reset() {
            delay.fill(0f)
            cursor = 0
            lowPass = 0f
        }
    }

    private class AllPass(length: Int) {
        private val delay = FloatArray(length)
        private var cursor = 0

        fun process(input: Float): Float {
            val output = delay[cursor] - 0.5f * input
            delay[cursor] = input + 0.5f * output
            if (++cursor == delay.size) cursor = 0
            return output
        }

        fun reset() {
            delay.fill(0f)
            cursor = 0
        }
    }

    private companion object {
        val COMB_MS = doubleArrayOf(23.7, 29.3, 31.1, 37.1)
        val DIFFUSER_MS = doubleArrayOf(5.1, 1.9)
    }
}
