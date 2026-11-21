package senada.app.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import senada.app.minTouchTarget
import senada.app.shell.SenadaIcons
import senada.playback.PlaybackController
import senada.playback.PlaybackState
import senada.playback.RepeatMode
import senada.playback.SampleAudioSource
import senada.playback.SampleAudioStatus

@Composable
internal fun NowPlayingControls(state: PlaybackState, playback: PlaybackController) {
    val audio = state.audio
    val queue = state.queue
    val colors = MaterialTheme.colorScheme
    val (repeatLabel, nextMode) = when (queue.repeatMode) {
        RepeatMode.Off -> "mati" to RepeatMode.All
        RepeatMode.All -> "semua" to RepeatMode.One
        RepeatMode.One -> "satu" to RepeatMode.Off
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerIcon(
            SenadaIcons.Shuffle, if (queue.shuffleEnabled) "Acak: nyala" else "Acak: mati",
            tint = if (queue.shuffleEnabled) colors.primary else colors.onSurfaceVariant,
        ) { playback.setShuffleEnabled(!queue.shuffleEnabled) }
        PlayerIcon(SenadaIcons.Previous, "Sebelumnya", enabled = audio.selection != null, onClick = playback::previous)
        val canPlay = audio.selection != null || queue.entries.isNotEmpty()
        Surface(
            onClick = { if (audio.isActive) playback.pause() else playback.resume() },
            enabled = canPlay,
            shape = CircleShape,
            color = if (canPlay) Color.White else colors.surfaceContainerHighest,
            contentColor = Color.Black,
            modifier = Modifier.size(76.dp).semantics {
                role = Role.Button
                contentDescription = if (audio.isActive) "Jeda" else "Putar"
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (audio.isActive) SenadaIcons.Pause else SenadaIcons.Play, null, Modifier.size(34.dp))
            }
        }
        PlayerIcon(SenadaIcons.Next, "Berikutnya", enabled = state.canGoNext, onClick = playback::next)
        PlayerIcon(
            if (queue.repeatMode == RepeatMode.One) SenadaIcons.RepeatOne else SenadaIcons.Repeat,
            "Ulangi: $repeatLabel",
            tint = if (queue.repeatMode == RepeatMode.Off) colors.onSurfaceVariant else colors.primary,
        ) { playback.setRepeatMode(nextMode) }
    }
}

@Composable
internal fun PlayerIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.minTouchTarget().semantics { contentDescription = label },
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else tint.copy(alpha = 0.38f))
    }
}

@Composable
internal fun PlaybackStatus(state: PlaybackState) {
    val status = when (state.audio.status) {
        SampleAudioStatus.Idle -> null
        SampleAudioStatus.Loading -> "Memuat audio…"
        SampleAudioStatus.Playing -> when (state.audio.selection?.source) {
            SampleAudioSource.Local -> "Memutar file lokal"
            SampleAudioSource.Https -> "Memutar dari HTTPS"
            SampleAudioSource.YouTubeMusic -> "Memutar dari YouTube Music"
            null -> null
        }
        SampleAudioStatus.Paused -> "Dijeda"
        SampleAudioStatus.Ended -> "Pemutaran selesai"
        SampleAudioStatus.Failed -> state.problem ?: "Audio gagal diputar. Coba lagi."
    }
    if (status != null) {
        Text(
            status, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
