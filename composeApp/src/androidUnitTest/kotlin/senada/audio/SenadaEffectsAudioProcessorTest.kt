package senada.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(UnstableApi::class)
class SenadaEffectsAudioProcessorTest {
    @Test
    fun bypassKeepsEveryByteAcrossFormatChanges() {
        val processor = SenadaEffectsAudioProcessor()
        for (format in FORMATS) {
            processor.configureAndFlush(format)
            // Include noncanonical NaNs and signed zero in float PCM to test byte-level bypass.
            val input = pcmBytes(format, 9_000)
            assertContentEquals(input, processor.render(input))
        }
    }

    @Test
    fun appliesBassPresetAndStaysActiveWhenBypassingAgain() {
        for (format in FORMATS) {
            val processor = SenadaEffectsAudioProcessor()
            processor.configureAndFlush(format)
            val input = pcmBytes(format, 1_024, impulse = true)
            assertContentEquals(input, processor.render(input))

            val bass = EqPreset.BuiltIns.first { it.id == "bass" }
            processor.update(AudioEffects(equalizer = EqualizerSettings(true, bass.id, bass.gains)))
            assertFalse(input.contentEquals(processor.render(input)))

            processor.update(AudioEffects())
            processor.render(input) // Let the common DSP finish fading out.
            assertTrue(processor.isActive)
            assertContentEquals(input, processor.render(input))
        }
    }

    @Test
    fun flushClearsFilterHistoryAndRetainsEffects() {
        val format = AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT)
        val processor = SenadaEffectsAudioProcessor()
        processor.update(AudioEffects(bassBoost = BassBoostSettings(enabled = true)))
        processor.configureAndFlush(format)
        val impulse = pcmBytes(format, 1, impulse = true)
        val silence = ByteArray(format.bytesPerFrame * 256)
        val firstImpulse = processor.render(impulse)
        assertFalse(impulse.contentEquals(firstImpulse))
        assertFalse(silence.contentEquals(processor.render(silence)), "The shelf filter still has a tail")

        processor.flush()
        assertContentEquals(silence, processor.render(silence))
        assertContentEquals(firstImpulse, processor.render(impulse))

        processor.reset()
        assertFalse(processor.isActive)
        processor.configureAndFlush(format)
        assertContentEquals(firstImpulse, processor.render(impulse))
    }

    @Test
    fun rejectsUnsupportedEncodings() {
        val processor = SenadaEffectsAudioProcessor()
        assertFailsWith<UnhandledAudioFormatException> {
            processor.configure(AudioFormat(44_100, 2, C.ENCODING_PCM_8BIT))
        }
    }

    private fun SenadaEffectsAudioProcessor.configureAndFlush(format: AudioFormat) {
        assertEquals(format, configure(format))
        assertTrue(isActive)
        flush()
    }

    private fun SenadaEffectsAudioProcessor.render(bytes: ByteArray): ByteArray {
        val input = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        input.put(bytes).flip()
        val output = ByteArray(bytes.size)
        var written = 0
        while (input.hasRemaining()) {
            val position = input.position()
            queueInput(input)
            assertTrue(input.position() > position)
            val block = getOutput()
            val size = block.remaining()
            block.get(output, written, size)
            written += size
        }
        assertEquals(bytes.size, written)
        return output
    }

    private fun pcmBytes(format: AudioFormat, frames: Int, impulse: Boolean = false): ByteArray {
        val buffer = ByteBuffer.allocate(frames * format.bytesPerFrame).order(ByteOrder.nativeOrder())
        repeat(frames * format.channelCount) { sample ->
            if (format.encoding == C.ENCODING_PCM_16BIT) {
                buffer.putShort(
                    when {
                        !impulse -> (sample * 19_999).toShort()
                        sample == 0 -> 12_000
                        else -> 0
                    },
                )
            } else {
                val bits = when {
                    !impulse && sample % 2 == 0 -> 0x7fa12345
                    !impulse -> Int.MIN_VALUE
                    sample == 0 -> 0.5f.toRawBits()
                    else -> 0
                }
                buffer.putInt(bits)
            }
        }
        return buffer.array()
    }

    private companion object {
        val FORMATS = listOf(
            AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT),
            AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT),
            AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT),
            AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT),
        )
    }
}
