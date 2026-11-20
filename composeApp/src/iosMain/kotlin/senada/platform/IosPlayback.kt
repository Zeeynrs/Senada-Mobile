@file:OptIn(ExperimentalForeignApi::class)

package senada.platform

import kotlin.math.roundToLong
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerTimeControlStatusPaused
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSDictionary
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import senada.app.createPlaybackController
import senada.playback.PlaybackController
import senada.playback.PlaybackState
import senada.playback.SampleAudioOutput
import senada.playback.SampleAudioStatus

/** Pemutar dan controller hidup selama proses aplikasi, terpisah dari layar Compose. */
internal object IosPlayback {
    val controller: PlaybackController by lazy(::create)

    // Nilai dari AVAudioSessionInterruptionType dan AVAudioSessionInterruptionOptions.
    private const val INTERRUPTION_BEGAN = 1uL
    private const val INTERRUPTION_SHOULD_RESUME = 1uL

    private val observers = mutableListOf<Any>()

    private fun create(): PlaybackController {
        // Kategori playback membuat audio tetap berjalan saat layar terkunci (bersama UIBackgroundModes audio).
        AVAudioSession.sharedInstance().setCategory(
            category = AVAudioSessionCategoryPlayback,
            mode = AVAudioSessionModeDefault,
            options = 0u,
            error = null,
        )
        val output = AvAudioOutput()
        val controller = createPlaybackController(output)
        observeInterruptions(controller)
        registerRemoteCommands(controller)
        UIApplication.sharedApplication.beginReceivingRemoteControlEvents()
        controller.addListener(::updateNowPlaying)
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
            MPNowPlayingInfoPropertyPlaybackRate to
                NSNumber(double = if (state.audio.status == SampleAudioStatus.Playing) 1.0 else 0.0),
        )
        track?.artists?.joinToString()?.takeIf { it.isNotEmpty() }?.let { info[MPMediaItemPropertyArtist] = it }
        state.audio.durationMs?.let { info[MPMediaItemPropertyPlaybackDuration] = NSNumber(double = it / 1_000.0) }
        // Kirim NSDictionary asli; Map Kotlin dibungkus kelas Kotlin yang bisa ditolak saat dikirim ke sistem.
        @Suppress("UNCHECKED_CAST")
        center.nowPlayingInfo = NSDictionary.dictionaryWithObjects(
            objects = info.values.toList(),
            forKeys = info.keys.toList(),
        ) as Map<Any?, *>
    }
}

private class AvAudioOutput : SampleAudioOutput {
    private val player = AVPlayer()
    private var pendingSeek: PendingSeek? = null
    private val observers = mutableListOf<Any>()

    override var onEvent: (() -> Unit)? = null

    init {
        val center = NSNotificationCenter.defaultCenter
        for (name in listOf(AVPlayerItemDidPlayToEndTimeNotification, AVPlayerItemFailedToPlayToEndTimeNotification)) {
            observers += center.addObserverForName(name, null, NSOperationQueue.mainQueue) { _ -> onEvent?.invoke() }
        }
        // AVPlayer juga memanggil blok ini saat audio mulai atau berhenti, misalnya saat headset dilepas.
        observers += player.addPeriodicTimeObserverForInterval(CMTimeMake(5, 1), dispatch_get_main_queue()) { _ ->
            onEvent?.invoke()
        }
    }

    override val status: SampleAudioStatus
        get() {
            val item = player.currentItem() ?: return SampleAudioStatus.Failed
            if (item.status == AVPlayerItemStatusFailed) return SampleAudioStatus.Failed
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
        get() = player.currentItem()?.let { CMTimeGetSeconds(it.duration()) }
            ?.takeIf { it.isFinite() && it > 0 }
            ?.let { (it * 1_000).roundToLong() }

    override fun play(uri: String, restart: Boolean) {
        if (restart) {
            pendingSeek = null
            player.pause()
            val url = NSURL.URLWithString(uri)
            player.replaceCurrentItemWithPlayerItem(url?.let { AVPlayerItem.playerItemWithURL(it) })
        }
        if (player.currentItem() != null) {
            // Sesi audio aktif membuat Senada menjadi aplikasi "Now Playing" di layar kunci.
            AVAudioSession.sharedInstance().setActive(true, error = null)
            player.play()
        }
    }

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) {
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
        player.replaceCurrentItemWithPlayerItem(null)
    }

    private class PendingSeek(val positionMs: Long)
}
