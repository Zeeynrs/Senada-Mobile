package senada.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Transposed direct form II, with independent sample history per channel. */
internal class Biquad private constructor(
    b0: Double,
    b1: Double,
    b2: Double,
    a0: Double,
    a1: Double,
    a2: Double,
    channels: Int,
) {
    private val b0 = b0 / a0
    private val b1 = b1 / a0
    private val b2 = b2 / a0
    private val a1 = a1 / a0
    private val a2 = a2 / a0
    private val z1 = DoubleArray(channels)
    private val z2 = DoubleArray(channels)

    fun process(input: Double, channel: Int): Double {
        val output = b0 * input + z1[channel]
        z1[channel] = b1 * input - a1 * output + z2[channel]
        z2[channel] = b2 * input - a2 * output
        return output
    }

    fun reset() {
        z1.fill(0.0)
        z2.fill(0.0)
    }

    companion object {
        // RBJ Audio EQ Cookbook coefficient equations: https://www.w3.org/TR/audio-eq-cookbook/
        fun peaking(sampleRate: Int, frequency: Int, gain: Float, channels: Int): Biquad {
            val omega = 2 * PI * frequency / sampleRate
            val alpha = sin(omega) / (2 * 1.41)
            val amplitude = 10.0.pow(gain / 40.0)
            val middle = -2 * cos(omega)
            return Biquad(
                1 + alpha * amplitude, middle, 1 - alpha * amplitude,
                1 + alpha / amplitude, middle, 1 - alpha / amplitude,
                channels,
            )
        }

        fun lowShelf(sampleRate: Int, frequency: Int, gain: Float, channels: Int): Biquad {
            val omega = 2 * PI * frequency / sampleRate
            val amplitude = 10.0.pow(gain / 40.0)
            val sum = amplitude + 1
            val difference = amplitude - 1
            val cosine = cos(omega)
            // Shelf slope S = 1, the steepest monotonic transition.
            val slope = sqrt(2 * amplitude) * sin(omega)
            return Biquad(
                amplitude * (sum - difference * cosine + slope),
                2 * amplitude * (difference - sum * cosine),
                amplitude * (sum - difference * cosine - slope),
                sum + difference * cosine + slope,
                -2 * (difference + sum * cosine),
                sum + difference * cosine - slope,
                channels,
            )
        }
    }
}
