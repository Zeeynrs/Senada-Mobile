package senada.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.roundToLong
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
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
import platform.CoreMedia.kCMTimeZero
import platform.Foundation.NSURL
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import senada.playback.SampleAudioOutput
import senada.playback.SampleAudioStatus

@Composable
internal actual fun rememberSampleAudioOutput(): SampleAudioOutput = remember { IosSampleAudioOutput() }

@OptIn(ExperimentalForeignApi::class)
private class IosSampleAudioOutput : SampleAudioOutput {
    private val player = AVPlayer()
    private var pendingSeek: PendingSeek? = null

    override val status: SampleAudioStatus
        get() {
            val item = player.currentItem() ?: return SampleAudioStatus.Failed
            if (item.status == AVPlayerItemStatusFailed) return SampleAudioStatus.Failed
            if (player.timeControlStatus() == AVPlayerTimeControlStatusPlaying) return SampleAudioStatus.Playing
            if (pendingSeek != null) return SampleAudioStatus.Loading

            val duration = durationMs
            return if (duration != null && positionMs >= duration - 20) {
                SampleAudioStatus.Ended
            } else {
                SampleAudioStatus.Loading
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
            AVAudioSession.sharedInstance().setCategory(
                category = AVAudioSessionCategoryPlayback,
                mode = AVAudioSessionModeDefault,
                options = 0u,
                error = null,
            )
            player.play()
        }
    }

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) {
        val request = PendingSeek(positionMs)
        pendingSeek = request
        player.seekToTime(CMTimeMake(positionMs, 1_000), kCMTimeZero, kCMTimeZero) { _ ->
            dispatch_async(dispatch_get_main_queue()) {
                if (pendingSeek === request) pendingSeek = null
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
