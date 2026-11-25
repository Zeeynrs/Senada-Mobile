@file:OptIn(ExperimentalForeignApi::class)

package senada.platform

import coil3.PlatformContext
import kotlin.math.roundToLong
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerTimeControlStatusPaused
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.asset
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.loadTracksWithMediaType
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setAudioMix
import platform.AVFoundation.setAudioTimePitchAlgorithm
import platform.AVFoundation.setDefaultRate
import platform.AVFoundation.setForwardPlaybackEndTime
import platform.AVFoundation.setRate
import platform.AVFoundation.setVolume
import platform.AVFoundation.timeControlStatus
import platform.AVFoundation.volume
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataTaskWithURL
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillTerminateNotification
import platform.UIKit.UIImage
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_sync
import platform.darwin.dispatch_get_main_queue
import senada.app.createPlayback
import senada.audio.AudioEffects
import senada.audio.AudioEffectsController
import senada.audio.IosAudioEffectsHub
import senada.audio.audioEffectsStore
import senada.audio.audioTimePitchAlgorithm
import senada.audio.nowPlayingPlaybackRate
import senada.playback.PlaybackController
import senada.playback.PlaybackState
import senada.playback.QueueStore
import senada.playback.SampleAudioOutput
import senada.playback.SampleAudioStatus

/** Pemutar dan controller hidup selama proses aplikasi, terpisah dari layar Compose. */
internal object IosPlayback {
    val audioEffects: AudioEffectsController by lazy { AudioEffectsController(audioEffectsStore(PlatformContext.INSTANCE)) }
    val controller: PlaybackController by lazy(::create)
    private val output by lazy { AvAudioOutput(audioEffects.effects.value) }

    fun position(): Long = output.positionMs

    // Nilai dari AVAudioSessionInterruptionType dan AVAudioSessionInterruptionOptions.
    private const val INTERRUPTION_BEGAN = 1uL
    private const val INTERRUPTION_SHOULD_RESUME = 1uL

    private val observers = mutableListOf<Any>()

    /** Sampul lagu terakhir yang sudah diunduh; layar kunci hanya memakai sampul lagu yang sedang dipilih. */
    private var artwork: Pair<String, MPMediaItemArtwork>? = null
    private var artworkLoading: String? = null
    private var refreshNowPlaying: () -> Unit = {}

    private fun create(): PlaybackController {
        // Kategori playback membuat audio tetap berjalan saat layar terkunci (bersama UIBackgroundModes audio).
        AVAudioSession.sharedInstance().setCategory(
            category = AVAudioSessionCategoryPlayback,
            mode = AVAudioSessionModeDefault,
            options = 0u,
            error = null,
        )
        val controller = createPlayback(
            output, IosQueueStore(), IosLocalAudioFiles(), PlatformContext.INSTANCE, audioEffects,
        ).controller
        for (name in listOf(UIApplicationDidEnterBackgroundNotification, UIApplicationWillTerminateNotification)) {
            observers += NSNotificationCenter.defaultCenter.addObserverForName(name, null, NSOperationQueue.mainQueue) {
                controller.saveQueueNow()
            }
        }
        observeInterruptions(controller)
        registerRemoteCommands(controller)
        controller.addListener(::updateNowPlaying)
        // Sampul tiba belakangan; posisi diambil langsung dari pemutar karena status bisa sudah lama.
        refreshNowPlaying = {
            val state = controller.state.value
            val position = if (state.audio.selection?.isPending == false && state.audio.isActive) {
                output.positionMs
            } else {
                state.audio.positionMs
            }
            updateNowPlaying(state.copy(audio = state.audio.copy(positionMs = position)))
        }
        MainScope().launch {
            audioEffects.effects.collect { effects ->
                if (output.updateEffects(effects)) refreshNowPlaying()
            }
        }
        return controller
    }

    private fun observeInterruptions(controller: PlaybackController) {
        observers += NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVAudioSessionInterruptionNotification,
            `object` = AVAudioSession.sharedInstance(),
            queue = NSOperationQueue.mainQueue,
        ) { notification ->
            val info = notification?.userInfo
            val type = (info?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
            if (type == INTERRUPTION_BEGAN) {
                controller.onInterruptionBegan()
            } else if (type != null) {
                val options = (info?.get(AVAudioSessionInterruptionOptionKey) as? NSNumber)?.unsignedLongValue ?: 0uL
                controller.onInterruptionEnded(shouldResume = options and INTERRUPTION_SHOULD_RESUME != 0uL)
            }
        }
    }

    private fun registerRemoteCommands(controller: PlaybackController) {
        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        commands.playCommand.addTargetWithHandler { _ ->
            controller.resume()
            MPRemoteCommandHandlerStatusSuccess
        }
        commands.pauseCommand.addTargetWithHandler { _ ->
            controller.pause()
            MPRemoteCommandHandlerStatusSuccess
        }
        commands.togglePlayPauseCommand.addTargetWithHandler { _ ->
            if (controller.state.value.audio.isActive) controller.pause() else controller.resume()
            MPRemoteCommandHandlerStatusSuccess
        }
        commands.nextTrackCommand.addTargetWithHandler { _ ->
            controller.next()
            MPRemoteCommandHandlerStatusSuccess
        }
        commands.previousTrackCommand.addTargetWithHandler { _ ->
            controller.previous()
            MPRemoteCommandHandlerStatusSuccess
        }
        commands.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val position = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime
            if (position == null) {
                MPRemoteCommandHandlerStatusCommandFailed
            } else {
                controller.seekTo((position * 1_000).roundToLong())
                MPRemoteCommandHandlerStatusSuccess
            }
        }
    }

    /** Dipanggil saat lagu, status, durasi atau posisi hasil seek berubah, bukan setiap tick. */
    private fun updateNowPlaying(state: PlaybackState) {
        val center = MPNowPlayingInfoCenter.defaultCenter()
        val selection = state.audio.selection
        MPRemoteCommandCenter.sharedCommandCenter().nextTrackCommand.enabled = state.canGoNext
        if (selection == null) {
            center.nowPlayingInfo = null
            return
        }

        val track = state.currentEntry?.track
        val info = linkedMapOf<String, Any>(
            MPMediaItemPropertyTitle to (track?.title ?: selection.title),
            MPNowPlayingInfoPropertyElapsedPlaybackTime to NSNumber(double = state.audio.positionMs / 1_000.0),
            MPNowPlayingInfoPropertyPlaybackRate to NSNumber(
                double = nowPlayingPlaybackRate(state.audio.status == SampleAudioStatus.Playing, output.playbackRate),
            ),
        )
        track?.artists?.joinToString()?.takeIf { it.isNotEmpty() }?.let { info[MPMediaItemPropertyArtist] = it }
        state.audio.durationMs?.let { info[MPMediaItemPropertyPlaybackDuration] = NSNumber(double = it / 1_000.0) }
        artworkFor(track?.artworkUrl)?.let { info[MPMediaItemPropertyArtwork] = it }
        // Kirim NSDictionary asli; Map Kotlin dibungkus kelas Kotlin yang bisa ditolak saat dikirim ke sistem.
        val dictionary = NSMutableDictionary()
        info.forEach { (key, value) -> dictionary.setObject(value, forKey = key as NSString) }
        @Suppress("UNCHECKED_CAST")
        center.nowPlayingInfo = dictionary as Map<Any?, *>
    }

    /** Sampul yang sudah siap, atau null sambil mengunduhnya lalu memperbarui layar kunci. */
    private fun artworkFor(url: String?): MPMediaItemArtwork? {
        if (url == null) return null
        artwork?.let { (loadedUrl, loaded) -> if (loadedUrl == url) return loaded }
        if (artworkLoading == url) return null
        val nsUrl = NSURL.URLWithString(url) ?: return null

        artworkLoading = url
        NSURLSession.sharedSession.dataTaskWithURL(nsUrl) { data, _, _ ->
            val image = data?.let { UIImage.imageWithData(it) }
            dispatch_async(dispatch_get_main_queue()) {
                // Lagu bisa sudah berganti dan sampul lain sedang diunduh.
                if (artworkLoading != url) return@dispatch_async
                artworkLoading = null
                if (image != null) {
                    artwork = url to MPMediaItemArtwork(boundsSize = image.size) { _ -> image }
                    refreshNowPlaying()
                }
            }
        }.resume()
        return null
    }
}

private class IosQueueStore : QueueStore {
    private val writeQueue = dispatch_queue_create("senada.playback.queue", null)

    override fun scheduleSave(flush: Boolean, write: () -> Unit) {
        if (flush) dispatch_sync(writeQueue) { write() }
        else dispatch_async(writeQueue) { write() }
    }

    private fun filePath(): String? = NSFileManager.defaultManager.URLForDirectory(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )?.path?.let { "$it/playback-queue.json" }

    override fun load(): String? = filePath()?.let { path ->
        NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
    }

    override fun save(text: String) {
        val path = checkNotNull(filePath())
        check((text as NSString).writeToFile(path, true, NSUTF8StringEncoding, null))
    }
}

private class AvAudioOutput(initialEffects: AudioEffects) : SampleAudioOutput {
    private val player = AVPlayer()
    private val effectsHub = IosAudioEffectsHub(initialEffects)
    private var currentEffects = initialEffects.bounded()
    private var pendingSeek: PendingSeek? = null
    private var reachedEnd = false
    /** Akhir audio yang sebenarnya dari sumber; AVPlayer bisa menebak durasi MP4 terfragmen terlalu panjang. */
    private var knownEndMs: Long? = null
    private val observers = mutableListOf<Any>()

    override var onEvent: (() -> Unit)? = null

    val playbackRate: Float get() = currentEffects.speed

    init {
        player.setDefaultRate(currentEffects.speed)
        val center = NSNotificationCenter.defaultCenter
        for (name in listOf(AVPlayerItemDidPlayToEndTimeNotification, AVPlayerItemFailedToPlayToEndTimeNotification)) {
            observers += center.addObserverForName(name, null, NSOperationQueue.mainQueue) { notification ->
                if (notification?.`object` == player.currentItem()) {
                    if (name == AVPlayerItemDidPlayToEndTimeNotification && pendingSeek == null) reachedEnd = true
                    onEvent?.invoke()
                }
            }
        }
        // AVPlayer juga memanggil blok ini saat audio mulai atau berhenti, misalnya saat headset dilepas.
        observers += player.addPeriodicTimeObserverForInterval(CMTimeMake(5, 1), dispatch_get_main_queue()) { _ ->
            onEvent?.invoke()
        }
    }

    fun updateEffects(effects: AudioEffects): Boolean {
        val bounded = effects.bounded()
        val speedChanged = bounded.speed != currentEffects.speed
        val pitchChanged = bounded.pitchLock != currentEffects.pitchLock
        currentEffects = bounded
        effectsHub.update(bounded)
        player.setDefaultRate(bounded.speed)
        if (speedChanged && player.rate() != 0f) {
            player.setRate(bounded.speed)
        }
        if (pitchChanged) {
            player.currentItem()?.let { applyPitchAlgorithm(it, bounded.pitchLock) }
        }
        return speedChanged
    }

    override val status: SampleAudioStatus
        get() {
            val item = player.currentItem() ?: return SampleAudioStatus.Failed
            if (item.status == AVPlayerItemStatusFailed) return SampleAudioStatus.Failed
            if (reachedEnd) return SampleAudioStatus.Ended
            return when (player.timeControlStatus()) {
                AVPlayerTimeControlStatusPlaying -> SampleAudioStatus.Playing
                AVPlayerTimeControlStatusPaused -> {
                    val duration = durationMs
                    when {
                        pendingSeek != null -> SampleAudioStatus.Loading
                        duration != null && positionMs >= duration - 20 -> SampleAudioStatus.Ended
                        else -> SampleAudioStatus.Paused
                    }
                }
                else -> SampleAudioStatus.Loading
            }
        }

    override val positionMs: Long
        get() = pendingSeek?.positionMs ?: run {
            val seconds = CMTimeGetSeconds(player.currentTime())
            if (seconds.isFinite()) (seconds * 1_000).roundToLong() else 0
        }

    override val durationMs: Long?
        get() {
            val reported = player.currentItem()?.let { CMTimeGetSeconds(it.duration()) }
                ?.takeIf { it.isFinite() && it > 0 }
                ?.let { (it * 1_000).roundToLong() }
            val known = knownEndMs
            return if (known != null && reported != null) minOf(known, reported) else known ?: reported
        }

    override var volume: Float
        get() = player.volume()
        set(value) { player.setVolume(value) }

    override fun play(uri: String, restart: Boolean, startAtMs: Long, endAtMs: Long?) {
        if (restart) {
            pendingSeek = null
            reachedEnd = false
            player.pause()
            player.currentItem()?.setAudioMix(null)
            effectsHub.clearActiveTap()
            knownEndMs = endAtMs?.takeIf { it > 0 }
            val item = NSURL.URLWithString(uri)?.let { AVPlayerItem.playerItemWithURL(it) }
            if (item != null) {
                // AVPlayer mengirim DidPlayToEndTime saat mencapai akhir audio dari sumber.
                knownEndMs?.let { item.setForwardPlaybackEndTime(CMTimeMake(it, 1_000)) }
                applyPitchAlgorithm(item, currentEffects.pitchLock)
            }
            player.replaceCurrentItemWithPlayerItem(item)
            if (item != null) {
                attachAudioTap(item)
                // AVPlayer menahan permintaan seek sampai item baru siap diputar.
                if (startAtMs > 0) seekTo(startAtMs)
            }
        }
        if (player.currentItem() != null) {
            player.setDefaultRate(currentEffects.speed)
            player.play()
            if (player.rate() != currentEffects.speed) {
                player.setRate(currentEffects.speed)
            }
        }
    }

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) {
        reachedEnd = false
        effectsHub.requestReset()
        val request = PendingSeek(positionMs)
        pendingSeek = request
        val zeroTolerance = CMTimeMake(0, 1_000)
        player.seekToTime(CMTimeMake(positionMs, 1_000), zeroTolerance, zeroTolerance) { _ ->
            dispatch_async(dispatch_get_main_queue()) {
                if (pendingSeek === request) pendingSeek = null
                onEvent?.invoke()
            }
        }
    }

    override fun release() {
        pendingSeek = null
        player.pause()
        player.currentItem()?.setAudioMix(null)
        effectsHub.clearActiveTap()
        player.replaceCurrentItemWithPlayerItem(null)
    }

    private fun applyPitchAlgorithm(item: AVPlayerItem, pitchLock: Boolean) {
        audioTimePitchAlgorithm(pitchLock)?.let { item.setAudioTimePitchAlgorithm(it) }
    }

    private fun attachAudioTap(item: AVPlayerItem) {
        val mediaType = AVMediaTypeAudio ?: run {
            println("[SenadaFX] AVMediaTypeAudio tidak tersedia; putar tanpa efek")
            return
        }
        item.asset().loadTracksWithMediaType(mediaType) { tracks, error ->
            dispatch_async(dispatch_get_main_queue()) {
                if (player.currentItem() !== item) return@dispatch_async
                if (error != null) {
                    println("[SenadaFX] Gagal memuat track audio: ${error.localizedDescription}")
                    return@dispatch_async
                }
                val track = tracks?.filterIsInstance<AVAssetTrack>()?.firstOrNull()
                if (track == null) {
                    println("[SenadaFX] Track audio tidak ditemukan; putar tanpa efek")
                    return@dispatch_async
                }
                if (!effectsHub.attachToItem(item, track)) {
                    println("[SenadaFX] Tap efek gagal dipasang; putar tanpa efek")
                }
            }
        }
    }

    private class PendingSeek(val positionMs: Long)
}
