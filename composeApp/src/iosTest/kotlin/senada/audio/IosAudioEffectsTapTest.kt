@file:OptIn(ExperimentalForeignApi::class)

package senada.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.NativePlacement
import kotlinx.cinterop.alignOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import platform.AVFoundation.AVAudioTimePitchAlgorithmTimeDomain
import platform.AVFoundation.AVAudioTimePitchAlgorithmVarispeed
import platform.CoreAudioTypes.AudioBuffer
import platform.CoreAudioTypes.AudioBufferList
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatFlagIsBigEndian
import platform.CoreAudioTypes.kAudioFormatFlagIsFloat
import platform.CoreAudioTypes.kAudioFormatFlagIsNonInterleaved
import platform.CoreAudioTypes.kAudioFormatFlagIsPacked
import platform.CoreAudioTypes.kAudioFormatFlagIsSignedInteger
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreFoundation.CFRelease
import platform.MediaToolbox.MTAudioProcessingTapGetStorage

class IosAudioEffectsTapTest {
    @Test
    fun pitch_algorithm_follows_pitch_lock() {
        assertEquals(AVAudioTimePitchAlgorithmTimeDomain, audioTimePitchAlgorithm(pitchLock = true))
        assertEquals(AVAudioTimePitchAlgorithmVarispeed, audioTimePitchAlgorithm(pitchLock = false))
    }

    @Test
    fun now_playing_playback_rate_follows_speed_only_while_playing() {
        assertEquals(1.25, nowPlayingPlaybackRate(isPlaying = true, speed = 1.25f))
        assertEquals(0.75, nowPlayingPlaybackRate(isPlaying = true, speed = 0.75f))
        assertEquals(0.0, nowPlayingPlaybackRate(isPlaying = false, speed = 1.25f))
        assertEquals(0.0, nowPlayingPlaybackRate(isPlaying = true, speed = Float.NaN))
    }

    @Test
    fun format_parser_accepts_float32_and_rejects_unsupported_formats() = memScoped {
        val nonInterleaved = allocAsbd(sampleRate = 48_000.0, channels = 2, nonInterleaved = true)
        assertEquals(PcmFloatFormat(48_000, 2, nonInterleaved = true), parseSupportedFloat32Format(nonInterleaved))

        val interleaved = allocAsbd(sampleRate = 44_100.0, channels = 2, nonInterleaved = false)
        assertEquals(PcmFloatFormat(44_100, 2, nonInterleaved = false), parseSupportedFloat32Format(interleaved))

        val mono = allocAsbd(sampleRate = 48_000.0, channels = 1, nonInterleaved = true)
        assertEquals(PcmFloatFormat(48_000, 1, nonInterleaved = true), parseSupportedFloat32Format(mono))

        val pcm16 = alloc<AudioStreamBasicDescription>().apply {
            mSampleRate = 48_000.0
            mFormatID = kAudioFormatLinearPCM
            mFormatFlags = kAudioFormatFlagIsSignedInteger or kAudioFormatFlagIsPacked
            mBitsPerChannel = 16u
            mChannelsPerFrame = 2u
            mFramesPerPacket = 1u
            mBytesPerFrame = 4u
            mBytesPerPacket = 4u
        }
        assertNull(parseSupportedFloat32Format(pcm16))

        val bigEndian = allocAsbd(sampleRate = 48_000.0, channels = 2, nonInterleaved = true).apply {
            mFormatFlags = mFormatFlags or kAudioFormatFlagIsBigEndian
        }
        assertNull(parseSupportedFloat32Format(bigEndian))

        val float64 = allocAsbd(sampleRate = 48_000.0, channels = 2, nonInterleaved = true).apply {
            mBitsPerChannel = 64u
        }
        assertNull(parseSupportedFloat32Format(float64))

        val zeroRate = allocAsbd(sampleRate = 0.0, channels = 2, nonInterleaved = true)
        assertNull(parseSupportedFloat32Format(zeroRate))
    }

    @Test
    fun interleave_and_deinterleave_round_trip_with_frame_offset() = memScoped {
        val (listPtr, channels) = allocNonInterleavedBufferList(channels = 2, frames = 5)
        val left = channels[0]
        val right = channels[1]
        for (i in 0 until 5) {
            left[i] = (i + 1).toFloat()
            right[i] = -(i + 1).toFloat()
        }

        val scratch = FloatArray(3 * 2)
        assertTrue(copyToInterleaved(listPtr.pointed, frameOffset = 1, frames = 3, channels = 2, nonInterleaved = true, interleaved = scratch))
        assertContentEquals(floatArrayOf(2f, -2f, 3f, -3f, 4f, -4f), scratch)

        for (i in scratch.indices) scratch[i] *= 10f
        copyFromInterleaved(scratch, listPtr.pointed, frameOffset = 1, frames = 3, channels = 2, nonInterleaved = true)

        assertContentEquals(floatArrayOf(1f, 20f, 30f, 40f, 5f), FloatArray(5) { left[it] })
        assertContentEquals(floatArrayOf(-1f, -20f, -30f, -40f, -5f), FloatArray(5) { right[it] })
    }

    @Test
    fun tap_context_bypasses_bit_identically_applies_effects_and_resets_tail() = memScoped {
        val hub = IosAudioEffectsHub(AudioEffects())
        val context = IosAudioTapContext(hub)
        hub.setActiveTap(context)

        val asbd = allocAsbd(sampleRate = 48_000.0, channels = 2, nonInterleaved = true)
        context.onPrepare(maxFrames = 64, processingFormat = asbd.ptr)
        assertNotNull(context.prepared)

        val (listPtr, channels) = allocNonInterleavedBufferList(channels = 2, frames = 6)
        val leftInput = floatArrayOf(-0f, Float.fromBits(0x7fc01234), -0.43f, 0.17f, 0.99f, -0.99f)
        val rightInput = floatArrayOf(0.25f, -0.25f, 0f, -0f, Float.fromBits(0x7fc04321), 0.12f)
        for (i in 0 until 6) {
            channels[0][i] = leftInput[i]
            channels[1][i] = rightInput[i]
        }
        val expectedLeftBits = IntArray(6) { leftInput[it].toRawBits() }
        val expectedRightBits = IntArray(6) { rightInput[it].toRawBits() }

        context.onProcess(listPtr.pointed, frames = 6, startOfStream = false)
        assertContentEquals(expectedLeftBits, IntArray(6) { channels[0][it].toRawBits() })
        assertContentEquals(expectedRightBits, IntArray(6) { channels[1][it].toRawBits() })

        hub.update(
            AudioEffects(
                bassBoost = BassBoostSettings(enabled = true, strength = 1f),
                reverb = ReverbSettings(enabled = true, room = ReverbRoom.Katedral, mix = 0.7f),
            ),
        )
        val (impulseList, impulseChannels) = allocNonInterleavedBufferList(channels = 2, frames = 64)
        for (i in 0 until 64) {
            impulseChannels[0][i] = if (i == 0) 0.5f else 0f
            impulseChannels[1][i] = 0f
        }
        context.onProcess(impulseList.pointed, frames = 64, startOfStream = false)
        assertTrue(abs(impulseChannels[0][0] - 0.5f) > 0.01f)
        assertTrue((0 until 64).all { impulseChannels[0][it].isFinite() && abs(impulseChannels[0][it]) <= 1f })

        // Verify filter/reverb tail exists before reset, then disappears after reset.
        val (silenceList, silenceChannels) = allocNonInterleavedBufferList(channels = 2, frames = 64)
        context.onProcess(silenceList.pointed, frames = 64, startOfStream = false)
        assertTrue((0 until 64).any { abs(silenceChannels[0][it]) > 1e-6f })

        for (i in 0 until 64) {
            silenceChannels[0][i] = 0f
            silenceChannels[1][i] = 0f
        }
        hub.requestReset()
        context.onProcess(silenceList.pointed, frames = 64, startOfStream = false)
        assertTrue((0 until 64).all { silenceChannels[0][it] == 0f && silenceChannels[1][it] == 0f })

        context.onUnprepare()
        assertNull(context.prepared)
    }

    @Test
    fun tap_context_chunks_frames_larger_than_max_frames() = memScoped {
        val hub = IosAudioEffectsHub(AudioEffects(equalizer = EqualizerSettings(enabled = true, preamp = 6f)))
        val context = IosAudioTapContext(hub)
        val asbd = allocAsbd(sampleRate = 48_000.0, channels = 2, nonInterleaved = true)
        context.onPrepare(maxFrames = 8, processingFormat = asbd.ptr)

        val (listPtr, channels) = allocNonInterleavedBufferList(channels = 2, frames = 20)
        for (i in 0 until 20) {
            channels[0][i] = 0.1f
            channels[1][i] = -0.1f
        }
        context.onProcess(listPtr.pointed, frames = 20, startOfStream = false)
        for (i in 0 until 20) {
            assertTrue(channels[0][i] > 0.18f, "Left frame $i was ${channels[0][i]}")
            assertTrue(channels[1][i] < -0.18f, "Right frame $i was ${channels[1][i]}")
        }
    }

    @Test
    fun tap_context_processes_interleaved_float32_and_bypasses_unsupported_format() = memScoped {
        val hub = IosAudioEffectsHub(AudioEffects(equalizer = EqualizerSettings(enabled = true, preamp = 6f)))
        val context = IosAudioTapContext(hub)

        val interleavedAsbd = allocAsbd(sampleRate = 48_000.0, channels = 2, nonInterleaved = false)
        context.onPrepare(maxFrames = 16, processingFormat = interleavedAsbd.ptr)
        assertNotNull(context.prepared)

        val list = alloc<AudioBufferList>()
        val data = allocArray<FloatVar>(16 * 2)
        for (i in 0 until 32) data[i] = 0.1f
        list.mNumberBuffers = 1u
        list.mBuffers[0].mNumberChannels = 2u
        list.mBuffers[0].mDataByteSize = (32 * Float.SIZE_BYTES).toUInt()
        list.mBuffers[0].mData = data

        context.onProcess(list, frames = 16, startOfStream = false)
        assertTrue((0 until 32).all { data[it] > 0.18f })

        val unsupportedAsbd = alloc<AudioStreamBasicDescription>().apply {
            mSampleRate = 48_000.0
            mFormatID = kAudioFormatLinearPCM
            mFormatFlags = kAudioFormatFlagIsSignedInteger or kAudioFormatFlagIsPacked
            mBitsPerChannel = 16u
            mChannelsPerFrame = 2u
            mFramesPerPacket = 1u
            mBytesPerFrame = 4u
            mBytesPerPacket = 4u
        }
        context.onPrepare(maxFrames = 16, processingFormat = unsupportedAsbd.ptr)
        assertNull(context.prepared)
    }

    @Test
    fun create_audio_processing_tap_initializes_and_finalizes_cleanly() {
        val hub = IosAudioEffectsHub(AudioEffects())
        val context = IosAudioTapContext(hub)
        val tap = createAudioProcessingTap(context)
        assertNotNull(tap)
        assertFalse(context.isFinalized)
        val stored = MTAudioProcessingTapGetStorage(tap)
        assertNotNull(stored)
        assertSame(context, stored.asStableRef<IosAudioTapContext>().get())
        CFRelease(tap)
        assertTrue(context.isFinalized)
    }

    private fun NativePlacement.allocAsbd(
        sampleRate: Double,
        channels: Int,
        nonInterleaved: Boolean,
    ): AudioStreamBasicDescription = alloc<AudioStreamBasicDescription>().apply {
        mSampleRate = sampleRate
        mFormatID = kAudioFormatLinearPCM
        mFormatFlags = kAudioFormatFlagIsFloat or kAudioFormatFlagIsPacked or
            if (nonInterleaved) kAudioFormatFlagIsNonInterleaved else 0u
        mBitsPerChannel = 32u
        mChannelsPerFrame = channels.toUInt()
        mFramesPerPacket = 1u
        mBytesPerFrame = if (nonInterleaved) Float.SIZE_BYTES.toUInt() else (Float.SIZE_BYTES * channels).toUInt()
        mBytesPerPacket = mBytesPerFrame
    }

    private fun NativePlacement.allocNonInterleavedBufferList(
        channels: Int,
        frames: Int,
    ): Pair<CPointer<AudioBufferList>, List<CPointer<FloatVar>>> {
        val totalSize = sizeOf<AudioBufferList>() + maxOf(0, channels - 1) * sizeOf<AudioBuffer>()
        val list = alloc(totalSize, alignOf<AudioBufferList>()).reinterpret<AudioBufferList>()
        list.mNumberBuffers = channels.toUInt()
        val buffers = list.mBuffers
        val dataPointers = List(channels) { channel ->
            val data = allocArray<FloatVar>(frames)
            buffers[channel].mNumberChannels = 1u
            buffers[channel].mDataByteSize = (frames * Float.SIZE_BYTES).toUInt()
            buffers[channel].mData = data
            data
        }
        return list.ptr to dataPointers
    }
}
