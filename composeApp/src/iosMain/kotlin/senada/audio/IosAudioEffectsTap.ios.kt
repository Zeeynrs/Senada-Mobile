@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)

package senada.audio

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.roundToInt
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVAudioTimePitchAlgorithm
import platform.AVFoundation.AVAudioTimePitchAlgorithmTimeDomain
import platform.AVFoundation.AVAudioTimePitchAlgorithmVarispeed
import platform.AVFoundation.AVMutableAudioMix
import platform.AVFoundation.AVMutableAudioMixInputParameters
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.setAudioMix
import platform.CoreAudioTypes.AudioBufferList
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatFlagIsBigEndian
import platform.CoreAudioTypes.kAudioFormatFlagIsFloat
import platform.CoreAudioTypes.kAudioFormatFlagIsNonInterleaved
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreMedia.CMItemCount
import platform.CoreMedia.CMItemCountVar
import platform.MediaToolbox.MTAudioProcessingTapCallbacks
import platform.MediaToolbox.MTAudioProcessingTapCreate
import platform.MediaToolbox.MTAudioProcessingTapFlags
import platform.MediaToolbox.MTAudioProcessingTapFlagsVar
import platform.MediaToolbox.MTAudioProcessingTapGetSourceAudio
import platform.MediaToolbox.MTAudioProcessingTapGetStorage
import platform.MediaToolbox.MTAudioProcessingTapRef
import platform.MediaToolbox.MTAudioProcessingTapRefVar
import platform.MediaToolbox.kMTAudioProcessingTapCallbacksVersion_0
import platform.MediaToolbox.kMTAudioProcessingTapCreationFlag_PostEffects
import platform.MediaToolbox.kMTAudioProcessingTapFlag_StartOfStream

private const val MAX_CHANNELS = 8
private const val MAX_CHUNK_FRAMES = 16_384
private val START_OF_STREAM_FLAG: MTAudioProcessingTapFlags = kMTAudioProcessingTapFlag_StartOfStream.convert()

internal fun audioTimePitchAlgorithm(pitchLock: Boolean): AVAudioTimePitchAlgorithm? =
    if (pitchLock) AVAudioTimePitchAlgorithmTimeDomain else AVAudioTimePitchAlgorithmVarispeed

internal fun nowPlayingPlaybackRate(isPlaying: Boolean, speed: Float): Double =
    if (isPlaying && speed.isFinite() && speed > 0f) speed.toDouble() else 0.0

internal data class PcmFloatFormat(
    val sampleRate: Int,
    val channels: Int,
    val nonInterleaved: Boolean,
)

internal fun parseSupportedFloat32Format(asbd: AudioStreamBasicDescription): PcmFloatFormat? {
    if (asbd.mFormatID != kAudioFormatLinearPCM) return null
    val flags = asbd.mFormatFlags
    if ((flags and kAudioFormatFlagIsFloat) == 0u) return null
    if ((flags and kAudioFormatFlagIsBigEndian) != 0u) return null
    if (asbd.mBitsPerChannel != 32u || asbd.mFramesPerPacket != 1u) return null
    val sampleRate = asbd.mSampleRate.takeIf { it.isFinite() && it > 0.0 }?.roundToInt() ?: return null
    if (sampleRate <= 0) return null
    val channels = asbd.mChannelsPerFrame.toInt()
    if (channels !in 1..MAX_CHANNELS) return null
    val nonInterleaved = (flags and kAudioFormatFlagIsNonInterleaved) != 0u
    val expectedBytesPerFrame = if (nonInterleaved) Float.SIZE_BYTES.toUInt() else (Float.SIZE_BYTES * channels).toUInt()
    if (asbd.mBytesPerFrame != expectedBytesPerFrame) return null
    return PcmFloatFormat(sampleRate, channels, nonInterleaved)
}

internal fun interleaveChannel(
    source: CPointer<FloatVar>,
    frameOffset: Int,
    interleaved: FloatArray,
    channel: Int,
    channels: Int,
    frames: Int,
) {
    var dst = channel
    for (frame in 0 until frames) {
        interleaved[dst] = source[frameOffset + frame]
        dst += channels
    }
}

internal fun deinterleaveChannel(
    interleaved: FloatArray,
    channel: Int,
    channels: Int,
    destination: CPointer<FloatVar>,
    frameOffset: Int,
    frames: Int,
) {
    var src = channel
    for (frame in 0 until frames) {
        destination[frameOffset + frame] = interleaved[src]
        src += channels
    }
}

/**
 * Copies [frames] frames starting at [frameOffset] from [bufferList] into [interleaved].
 * Returns false if [bufferList] does not match [channels]/[nonInterleaved] or lacks enough bytes.
 */
internal fun copyToInterleaved(
    bufferList: AudioBufferList,
    frameOffset: Int,
    frames: Int,
    channels: Int,
    nonInterleaved: Boolean,
    interleaved: FloatArray,
): Boolean {
    if (frames <= 0 || channels <= 0 || interleaved.size < frames * channels) return false
    val buffers = bufferList.mBuffers
    val bufferCount = bufferList.mNumberBuffers.toInt()
    return if (nonInterleaved) {
        if (bufferCount != channels) return false
        val requiredBytes = (frameOffset + frames).toLong() * Float.SIZE_BYTES
        for (channel in 0 until channels) {
            val buffer = buffers[channel]
            if (buffer.mNumberChannels.toInt() != 1) return false
            val data = buffer.mData ?: return false
            if (buffer.mDataByteSize.toLong() < requiredBytes) return false
            interleaveChannel(data.reinterpret(), frameOffset, interleaved, channel, channels, frames)
        }
        true
    } else {
        if (bufferCount != 1) return false
        val buffer = buffers[0]
        if (buffer.mNumberChannels.toInt() != channels) return false
        val data = buffer.mData ?: return false
        val sampleOffset = frameOffset * channels
        val sampleCount = frames * channels
        val requiredBytes = (sampleOffset + sampleCount).toLong() * Float.SIZE_BYTES
        if (buffer.mDataByteSize.toLong() < requiredBytes) return false
        val samples = data.reinterpret<FloatVar>()
        for (index in 0 until sampleCount) {
            interleaved[index] = samples[sampleOffset + index]
        }
        true
    }
}

internal fun copyFromInterleaved(
    interleaved: FloatArray,
    bufferList: AudioBufferList,
    frameOffset: Int,
    frames: Int,
    channels: Int,
    nonInterleaved: Boolean,
) {
    val buffers = bufferList.mBuffers
    if (nonInterleaved) {
        for (channel in 0 until channels) {
            val data = buffers[channel].mData ?: return
            deinterleaveChannel(interleaved, channel, channels, data.reinterpret(), frameOffset, frames)
        }
    } else {
        val data = buffers[0].mData ?: return
        val samples = data.reinterpret<FloatVar>()
        val sampleOffset = frameOffset * channels
        val sampleCount = frames * channels
        for (index in 0 until sampleCount) {
            samples[sampleOffset + index] = interleaved[index]
        }
    }
}

internal class PreparedTap(
    val format: PcmFloatFormat,
    val maxFrames: Int,
    val dsp: AudioEffectsProcessor,
) {
    val scratch = FloatArray(maxFrames * format.channels)
}

internal fun processAudioBufferList(
    prepared: PreparedTap,
    bufferList: AudioBufferList,
    frames: Int,
    resetRequested: Boolean,
) {
    if (resetRequested) prepared.dsp.reset()
    if (frames <= 0) return
    val scratch = prepared.scratch
    val channels = prepared.format.channels
    val nonInterleaved = prepared.format.nonInterleaved
    var frameOffset = 0
    while (frameOffset < frames) {
        if (prepared.dsp.tryBypass()) return
        val chunk = minOf(frames - frameOffset, prepared.maxFrames)
        if (!copyToInterleaved(bufferList, frameOffset, chunk, channels, nonInterleaved, scratch)) return
        prepared.dsp.process(scratch, 0, chunk)
        copyFromInterleaved(scratch, bufferList, frameOffset, chunk, channels, nonInterleaved)
        frameOffset += chunk
    }
}

/**
 * Lock-free bridge between main-thread settings updates and whichever [IosAudioTapContext] is
 * attached to the active `AVPlayerItem`.
 */
internal class IosAudioEffectsHub(initialEffects: AudioEffects) {
    private val latestEffects = AtomicReference(initialEffects.bounded())
    private val activeTap = AtomicReference<IosAudioTapContext?>(null)

    fun currentEffects(): AudioEffects = latestEffects.load()

    fun update(effects: AudioEffects) {
        val bounded = effects.bounded()
        latestEffects.store(bounded)
        activeTap.load()?.updateEffects(bounded)
    }

    fun requestReset() {
        activeTap.load()?.requestReset()
    }

    fun clearActiveTap() {
        activeTap.store(null)
    }

    fun setActiveTap(context: IosAudioTapContext) {
        activeTap.store(context)
        context.updateEffects(latestEffects.load())
    }

    fun attachToItem(item: AVPlayerItem, track: AVAssetTrack): Boolean {
        val context = IosAudioTapContext(this)
        val tap = createAudioProcessingTap(context) ?: return false
        return try {
            val inputParameters = AVMutableAudioMixInputParameters.audioMixInputParametersWithTrack(track)
            inputParameters.audioTapProcessor = tap
            val audioMix = AVMutableAudioMix.audioMix()
            audioMix.inputParameters = listOf(inputParameters)
            setActiveTap(context)
            item.setAudioMix(audioMix)
            true
        } catch (t: Throwable) {
            println("[SenadaFX] Gagal memasang AVMutableAudioMix: ${t.message}")
            false
        } finally {
            CFRelease(tap)
        }
    }
}

/** Per-item tap storage kept alive via [StableRef] until MediaToolbox calls finalize. */
internal class IosAudioTapContext(private val hub: IosAudioEffectsHub) {
    private val activeDsp = AtomicReference<AudioEffectsProcessor?>(null)
    private val resetRequested = AtomicBoolean(false)
    private val finalized = AtomicBoolean(false)

    var prepared: PreparedTap? = null
        private set

    val isFinalized: Boolean get() = finalized.load()

    fun updateEffects(effects: AudioEffects) {
        activeDsp.load()?.update(effects)
    }

    fun requestReset() {
        resetRequested.store(true)
    }

    fun onPrepare(maxFrames: Long, processingFormat: CPointer<AudioStreamBasicDescription>?) {
        unprepareInternal()
        val asbd = processingFormat?.pointed ?: run {
            println("[SenadaFX] Format audio kosong saat prepare; bypass")
            return
        }
        val format = parseSupportedFloat32Format(asbd) ?: run {
            println(
                "[SenadaFX] Format audio tidak didukung " +
                    "(formatID=${asbd.mFormatID}, flags=${asbd.mFormatFlags}, " +
                    "bits=${asbd.mBitsPerChannel}, sr=${asbd.mSampleRate}, ch=${asbd.mChannelsPerFrame}); bypass",
            )
            return
        }
        val chunkFrames = maxFrames.coerceIn(1L, MAX_CHUNK_FRAMES.toLong()).toInt()
        val dsp = AudioEffectsProcessor(format.sampleRate, format.channels, chunkFrames)
        dsp.update(hub.currentEffects())
        val state = PreparedTap(format, chunkFrames, dsp)
        prepared = state
        resetRequested.store(false)
        activeDsp.store(dsp)
        // Re-apply latest settings after publishing activeDsp so concurrent updates are never missed.
        dsp.update(hub.currentEffects())
    }

    fun onUnprepare() {
        unprepareInternal()
    }

    fun onProcess(bufferList: AudioBufferList, frames: Int, startOfStream: Boolean) {
        val state = prepared ?: return
        val shouldReset = resetRequested.exchange(false) || startOfStream
        processAudioBufferList(state, bufferList, frames, shouldReset)
    }

    fun tryMarkFinalized(): Boolean = finalized.compareAndSet(false, true)

    private fun unprepareInternal() {
        activeDsp.store(null)
        prepared = null
    }
}

internal fun createAudioProcessingTap(context: IosAudioTapContext): MTAudioProcessingTapRef? = memScoped {
    val stableRef = StableRef.create(context)
    val callbacks = alloc<MTAudioProcessingTapCallbacks>().apply {
        version = kMTAudioProcessingTapCallbacksVersion_0.convert()
        clientInfo = stableRef.asCPointer()
        init = staticCFunction(::tapInit)
        finalize = staticCFunction(::tapFinalize)
        prepare = staticCFunction(::tapPrepare)
        unprepare = staticCFunction(::tapUnprepare)
        process = staticCFunction(::tapProcess)
    }
    val tapOut = alloc<MTAudioProcessingTapRefVar>()
    val status = MTAudioProcessingTapCreate(
        allocator = kCFAllocatorDefault,
        callbacks = callbacks.ptr,
        flags = kMTAudioProcessingTapCreationFlag_PostEffects.convert(),
        tapOut = tapOut.ptr,
    )
    val tap = tapOut.value
    if (status != 0 || tap == null) {
        println("[SenadaFX] MTAudioProcessingTapCreate gagal (status=$status)")
        if (context.tryMarkFinalized()) {
            stableRef.dispose()
        }
        return null
    }
    tap
}

private fun tapInit(
    tap: MTAudioProcessingTapRef?,
    clientInfo: COpaquePointer?,
    tapStorageOut: CPointer<COpaquePointerVar>?,
) {
    tapStorageOut?.set(0, clientInfo)
}

private fun tapFinalize(tap: MTAudioProcessingTapRef?) {
    val storage = tap?.let { MTAudioProcessingTapGetStorage(it) } ?: return
    val ref = storage.asStableRef<IosAudioTapContext>()
    val context = ref.get()
    context.onUnprepare()
    if (context.tryMarkFinalized()) {
        ref.dispose()
    }
}

private fun tapPrepare(
    tap: MTAudioProcessingTapRef?,
    maxFrames: CMItemCount,
    processingFormat: CPointer<AudioStreamBasicDescription>?,
) {
    val storage = tap?.let { MTAudioProcessingTapGetStorage(it) } ?: return
    val context = storage.asStableRef<IosAudioTapContext>().get()
    try {
        context.onPrepare(maxFrames, processingFormat)
    } catch (t: Throwable) {
        println("[SenadaFX] Gagal menyiapkan tap audio: ${t.message}")
        context.onUnprepare()
    }
}

private fun tapUnprepare(tap: MTAudioProcessingTapRef?) {
    val storage = tap?.let { MTAudioProcessingTapGetStorage(it) } ?: return
    storage.asStableRef<IosAudioTapContext>().get().onUnprepare()
}

private fun tapProcess(
    tap: MTAudioProcessingTapRef?,
    numberFrames: CMItemCount,
    flags: MTAudioProcessingTapFlags,
    bufferListInOut: CPointer<AudioBufferList>?,
    numberFramesOut: CPointer<CMItemCountVar>?,
    flagsOut: CPointer<MTAudioProcessingTapFlagsVar>?,
) {
    if (tap == null || bufferListInOut == null) return
    val status = MTAudioProcessingTapGetSourceAudio(
        tap = tap,
        numberFrames = numberFrames,
        bufferListInOut = bufferListInOut,
        flagsOut = flagsOut,
        timeRangeOut = null,
        numberFramesOut = numberFramesOut,
    )
    if (status != 0) return
    val storage = MTAudioProcessingTapGetStorage(tap) ?: return
    val context = storage.asStableRef<IosAudioTapContext>().get()
    val actualFrames = (numberFramesOut?.get(0) ?: numberFrames).toInt()
    val sourceFlags = flagsOut?.get(0) ?: 0u
    val startOfStream = ((flags or sourceFlags) and START_OF_STREAM_FLAG) != 0u
    try {
        context.onProcess(bufferListInOut.pointed, actualFrames, startOfStream)
    } catch (_: Throwable) {
        // Tetap aman di thread audio real-time: biarkan audio sumber apa adanya bila gagal.
    }
}
