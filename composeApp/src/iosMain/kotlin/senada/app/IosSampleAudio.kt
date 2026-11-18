package senada.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSURL
import senada.playback.SampleAudioOutput
import senada.playback.SampleAudioSource
import senada.playback.SampleAudioStatus

@Composable
internal actual fun rememberSampleAudioOutput(): SampleAudioOutput = remember { IosSampleAudioOutput() }

@OptIn(ExperimentalForeignApi::class)
private class IosSampleAudioOutput : SampleAudioOutput {
    private val player = AVPlayer()

    override val status: SampleAudioStatus
        get() {
            val item = player.currentItem ?: return SampleAudioStatus.Failed
            if (item.status == AVPlayerItemStatusFailed) return SampleAudioStatus.Failed
            if (player.timeControlStatus == AVPlayerTimeControlStatusPlaying) return SampleAudioStatus.Playing

            val duration = CMTimeGetSeconds(item.duration)
            val position = CMTimeGetSeconds(player.currentTime())
            return if (duration.isFinite() && duration > 0 && position >= duration - 0.02) {
                SampleAudioStatus.Ended
            } else {
                SampleAudioStatus.Loading
            }
        }

    override fun play(source: SampleAudioSource, restart: Boolean) {
        if (restart) {
            player.pause()
            val url = NSURL.URLWithString(source.uri())
            player.replaceCurrentItemWithPlayerItem(url?.let { AVPlayerItem.playerItemWithURL(it) })
        }
        if (player.currentItem != null) {
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

    override fun release() {
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
    }
}
