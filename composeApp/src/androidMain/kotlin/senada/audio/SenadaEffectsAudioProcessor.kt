package senada.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

@OptIn(UnstableApi::class)
internal class SenadaEffectsAudioProcessor : BaseAudioProcessor() {
    // Only settings updates and format changes need to agree on which DSP gets the new settings.
    private val configurationLock = Any()
    private var effects = AudioEffects()
    private var processing: Processing? = null

    fun update(effects: AudioEffects) {
        synchronized(configurationLock) {
            this.effects = effects
            processing?.dsp?.update(effects)
        }
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        // Stay in the sink's pipeline when bypassed so edits don't reconfigure playback.
        return inputAudioFormat
    }

    override fun onFlush() {
        synchronized(configurationLock) {
            processing?.dsp?.reset()
            if (inputAudioFormat != AudioFormat.NOT_SET && processing?.format != inputAudioFormat) {
                processing = Processing(inputAudioFormat).also { it.dsp.update(effects) }
                // Reserve the reusable output buffer up front, leaving no output pending.
                replaceOutputBuffer(BLOCK_FRAMES * inputAudioFormat.bytesPerFrame)
                getOutput()
            }
        }
    }

    override fun onReset() {
        synchronized(configurationLock) {
            processing?.dsp?.reset()
            processing = null
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val current = checkNotNull(processing)
        val format = current.format
        val frames = minOf(inputBuffer.remaining() / format.bytesPerFrame, BLOCK_FRAMES)
        if (frames == 0) return
        val bytes = frames * format.bytesPerFrame
        val output = replaceOutputBuffer(bytes)
        val samples = frames * format.channelCount
        val shorts = current.shorts
        if (current.dsp.tryBypass()) {
            val limit = inputBuffer.limit()
            inputBuffer.limit(inputBuffer.position() + bytes)
            output.put(inputBuffer)
            inputBuffer.limit(limit)
        } else if (shorts != null) {
            for (index in 0 until samples) shorts[index] = inputBuffer.short
            current.dsp.processShort(shorts, 0, frames)
            for (index in 0 until samples) output.putShort(shorts[index])
        } else {
            val floats = checkNotNull(current.floats)
            for (index in 0 until samples) floats[index] = inputBuffer.float
            current.dsp.process(floats, 0, frames)
            for (index in 0 until samples) output.putFloat(floats[index])
        }
        output.flip()
    }

    private class Processing(val format: AudioFormat) {
        private val blockSamples = BLOCK_FRAMES * format.channelCount
        val shorts = if (format.encoding == C.ENCODING_PCM_16BIT) ShortArray(blockSamples) else null
        val floats = if (format.encoding == C.ENCODING_PCM_FLOAT) FloatArray(blockSamples) else null
        val dsp = AudioEffectsProcessor(format.sampleRate, format.channelCount, BLOCK_FRAMES)
    }

    private companion object {
        const val BLOCK_FRAMES = 4096
    }
}
