package senada.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import senada.playback.SampleAudioOutput
import senada.playback.SampleAudioStatus

@Composable
internal actual fun rememberSampleAudioOutput(): SampleAudioOutput {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidSampleAudioOutput(context) }
}

private class AndroidSampleAudioOutput(context: Context) : SampleAudioOutput {
    private val player = ExoPlayer.Builder(context).build().apply {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        setAudioAttributes(audioAttributes, true)
    }

    override val status: SampleAudioStatus
        get() = when {
            player.playerError != null -> SampleAudioStatus.Failed
            player.playbackState == Player.STATE_ENDED -> SampleAudioStatus.Ended
            player.isPlaying -> SampleAudioStatus.Playing
            !player.playWhenReady && player.playbackState == Player.STATE_READY -> SampleAudioStatus.Paused
            else -> SampleAudioStatus.Loading
        }

    override val positionMs get() = player.currentPosition

    override val durationMs get() = player.duration.takeIf { it > 0 }

    override fun play(uri: String, restart: Boolean) {
        if (restart) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
        }
        player.play()
    }

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun release() = player.release()
}
