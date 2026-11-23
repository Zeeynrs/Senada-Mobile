package senada.app.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.roundToLong
import senada.app.accessibleDuration
import senada.app.excludeSystemGestures
import senada.app.minTouchTarget
import senada.app.playbackTime
import senada.app.shell.SenadaIcons
import senada.playback.PlaybackController
import senada.playback.PlaybackState
import senada.playback.RepeatMode
import senada.playback.SampleAudioSource
import senada.playback.SampleAudioState
import senada.playback.SampleAudioStatus

internal val LocalPlayerGlass = staticCompositionLocalOf<HazeState> { error("Sumber kaca pemutar belum dipasang") }
internal val PlayerGlassTint = Color.White.copy(alpha = .12f)
internal val PlayerGlassBorder = Color.White.copy(alpha = .14f)

@Composable
internal fun PlayerGlassButton(
    icon: ImageVector,
    label: String,
    scale: Float,
    enabled: Boolean = true,
    tint: Color = PlayerWhite,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size((44.dp * scale).coerceAtLeast(48.dp))
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(44.dp * scale)
                .clip(CircleShape)
                .hazeEffect(
                    state = LocalPlayerGlass.current,
                    style = HazeStyle(
                        backgroundColor = Color(0xFF0B0C0E),
                        tint = HazeTint(PlayerGlassTint),
                        blurRadius = 16.dp * scale,
                        noiseFactor = 0f,
                        fallbackTint = HazeTint(PlayerGlassTint),
                    ),
                )
                .border(1.dp * scale, PlayerGlassBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(20.dp * scale), tint = if (enabled) tint else tint.copy(alpha = .38f))
        }
    }
}

@Composable
internal fun NowPlayingControls(state: PlaybackState, playback: PlaybackController, scale: Float) {
    val audio = state.audio
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(
            NowPlayingIcons.Previous, "Sebelumnya", 64.dp * scale, scale,
            enabled = audio.selection != null, onClick = playback::previous,
        )
        TransportButton(
            if (audio.isActive) NowPlayingIcons.Pause else NowPlayingIcons.Play,
            if (audio.isActive) "Jeda" else "Putar",
            76.dp * scale, scale,
            enabled = audio.selection != null || state.queue.entries.isNotEmpty(),
        ) { if (audio.isActive) playback.pause() else playback.resume() }
        TransportButton(
            NowPlayingIcons.Next, "Berikutnya", 64.dp * scale, scale,
            enabled = state.canGoNext, onClick = playback::next,
        )
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    label: String,
    target: Dp,
    scale: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(target.coerceAtLeast(48.dp))
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, null, Modifier.size(icon.defaultWidth * scale, icon.defaultHeight * scale),
            tint = if (enabled) PlayerWhite else PlayerWhite.copy(alpha = .38f),
        )
    }
}

@Composable
internal fun PlayerPlaybackMenu(state: PlaybackState, playback: PlaybackController, close: () -> Unit) {
    val queue = state.queue
    val shuffle = if (queue.shuffleEnabled) "nyala" else "mati"
    val (repeat, nextMode) = when (queue.repeatMode) {
        RepeatMode.Off -> "mati" to RepeatMode.All
        RepeatMode.All -> "semua" to RepeatMode.One
        RepeatMode.One -> "satu" to RepeatMode.Off
    }
    DropdownMenuItem(
        text = { Text("Acak: $shuffle") },
        leadingIcon = { Icon(SenadaIcons.Shuffle, null) },
        modifier = Modifier.minTouchTarget().semantics { contentDescription = "Acak: $shuffle" },
        onClick = {
            close()
            playback.setShuffleEnabled(!queue.shuffleEnabled)
        },
    )
    DropdownMenuItem(
        text = { Text("Ulangi: $repeat") },
        leadingIcon = { Icon(if (queue.repeatMode == RepeatMode.One) SenadaIcons.RepeatOne else SenadaIcons.Repeat, null) },
        modifier = Modifier.minTouchTarget().semantics { contentDescription = "Ulangi: $repeat" },
        onClick = {
            close()
            playback.setRepeatMode(nextMode)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingPosition(audio: SampleAudioState, scale: Float, onSeek: (Long) -> Unit) {
    val duration = audio.durationMs
    var scrubFraction by remember(audio.selection, audio.canSeek) { mutableStateOf<Float?>(null) }
    val progress = scrubFraction ?: duration?.let { (audio.positionMs.toDouble() / it).toFloat().coerceIn(0f, 1f) } ?: 0f
    val shownPosition = if (duration != null && scrubFraction != null) {
        (progress * duration.toDouble()).roundToLong()
    } else {
        audio.positionMs
    }
    val spokenPosition = accessibleDuration(shownPosition)
    val spokenProgress = if (duration != null) "$spokenPosition dari ${accessibleDuration(duration)}" else spokenPosition
    val trackHeight = 6.dp * scale
    val trackShape = RoundedCornerShape(3.dp * scale)
    val touchHeight = (48.dp * scale).coerceAtLeast(48.dp)

    Box(Modifier.fillMaxWidth().heightIn(min = touchHeight)) {
        Box(
            Modifier.padding(top = 8.dp * scale).fillMaxWidth().height(trackHeight)
                .background(Color.White.copy(alpha = .25f), trackShape),
        ) {
            Box(Modifier.fillMaxWidth(progress).height(trackHeight).background(PlayerWhite, trackShape))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 22.dp * scale).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                playbackTime(shownPosition), color = PlayerWhite.copy(alpha = .60f),
                fontWeight = FontWeight.SemiBold, fontSize = playerSp(12, scale), lineHeight = playerSp(16, scale),
            )
            if (duration != null) {
                Text(
                    "-${playbackTime((duration - shownPosition).coerceAtLeast(0))}", color = PlayerWhite.copy(alpha = .60f),
                    fontWeight = FontWeight.SemiBold, fontSize = playerSp(12, scale), lineHeight = playerSp(16, scale),
                )
            }
        }
        Slider(
            value = progress,
            onValueChange = { scrubFraction = it },
            onValueChangeFinished = {
                if (duration != null) scrubFraction?.let { onSeek((it * duration.toDouble()).roundToLong()) }
                scrubFraction = null
            },
            enabled = audio.canSeek,
            // Bilah tipis di atas tetap dapat digeser lewat area sentuh 48 dp.
            thumb = {},
            track = { Spacer(Modifier.fillMaxWidth().height(trackHeight)) },
            modifier = Modifier.fillMaxWidth().height(touchHeight).excludeSystemGestures().semantics {
                contentDescription = if (duration == null && audio.status == SampleAudioStatus.Paused &&
                    audio.selection?.isPending == true && shownPosition > 0
                ) {
                    "Posisi jeda $spokenPosition"
                } else {
                    "Posisi pemutaran"
                }
                stateDescription = spokenProgress
            },
        )
    }
}

internal fun playbackStatus(state: PlaybackState): String? = when (state.audio.status) {
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
