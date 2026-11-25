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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import senada.app.AppLanguage
import senada.app.AppStrings
import senada.app.LocalAppLanguage
import senada.app.OverflowActionItem
import senada.app.appString
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToLong
import senada.app.accessibleDuration
import senada.app.excludeSystemGestures
import senada.app.playbackTime
import senada.app.shell.SenadaIcons
import senada.app.theme.LocalSenadaLook
import senada.playback.PlaybackController
import senada.playback.PlaybackState
import senada.playback.RepeatMode
import senada.playback.SampleAudioSource
import senada.playback.SampleAudioState
import senada.playback.SampleAudioStatus
import senada.resources.Res
import senada.resources.action_pause
import senada.resources.action_play
import senada.resources.playback_audio_failed
import senada.resources.player_loading_audio
import senada.resources.player_next
import senada.resources.player_paused_position
import senada.resources.player_position_label
import senada.resources.player_position_of
import senada.resources.player_previous
import senada.resources.player_repeat_all
import senada.resources.player_repeat_label
import senada.resources.player_repeat_off
import senada.resources.player_repeat_one
import senada.resources.player_shuffle_label
import senada.resources.player_shuffle_off
import senada.resources.player_shuffle_on
import senada.resources.player_status_ended
import senada.resources.player_status_https
import senada.resources.player_status_local
import senada.resources.player_status_offline
import senada.resources.player_status_paused
import senada.resources.player_status_ytm

internal val PlayerGlassTint = Color.White.copy(alpha = .12f)
internal val PlayerGlassBorder = Color.White.copy(alpha = .14f)

internal data class PlayerGlassStyle(
    val useBackdropBlur: Boolean,
    val fillColor: Color = PlayerGlassTint,
    val borderColor: Color = PlayerGlassBorder,
)

internal fun playerGlassStyle(glassEnabled: Boolean): PlayerGlassStyle =
    PlayerGlassStyle(useBackdropBlur = glassEnabled)

@Composable
internal fun Modifier.playerGlass(shape: Shape, borderWidth: Dp = 1.dp): Modifier {
    val style = playerGlassStyle(LocalSenadaLook.current.glass)
    return clip(shape)
        .then(if (style.useBackdropBlur) Modifier.playerGlassBackdrop() else Modifier)
        .background(style.fillColor)
        .border(borderWidth, style.borderColor, shape)
}

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
                .playerGlass(CircleShape, borderWidth = 1.dp * scale),
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
            NowPlayingIcons.Previous, appString(Res.string.player_previous), 64.dp * scale, scale,
            enabled = audio.selection != null, onClick = playback::previous,
        )
        TransportButton(
            if (audio.isActive) NowPlayingIcons.Pause else NowPlayingIcons.Play,
            appString(if (audio.isActive) Res.string.action_pause else Res.string.action_play),
            76.dp * scale, scale,
            enabled = audio.selection != null || state.queue.entries.isNotEmpty(),
        ) { if (audio.isActive) playback.pause() else playback.resume() }
        TransportButton(
            NowPlayingIcons.Next, appString(Res.string.player_next), 64.dp * scale, scale,
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
internal fun PlayerPlaybackMenu(state: PlaybackState, playback: PlaybackController, select: (() -> Unit) -> Unit) {
    val queue = state.queue
    val shuffle = appString(if (queue.shuffleEnabled) Res.string.player_shuffle_on else Res.string.player_shuffle_off)
    val (repeat, nextMode) = when (queue.repeatMode) {
        RepeatMode.Off -> appString(Res.string.player_repeat_off) to RepeatMode.All
        RepeatMode.All -> appString(Res.string.player_repeat_all) to RepeatMode.One
        RepeatMode.One -> appString(Res.string.player_repeat_one) to RepeatMode.Off
    }
    val shuffleLabel = appString(Res.string.player_shuffle_label, shuffle)
    val repeatLabel = appString(Res.string.player_repeat_label, repeat)
    OverflowActionItem(
        label = shuffleLabel,
        icon = SenadaIcons.Shuffle,
        modifier = Modifier.semantics { contentDescription = shuffleLabel },
    ) { select { playback.setShuffleEnabled(!queue.shuffleEnabled) } }
    OverflowActionItem(
        label = repeatLabel,
        icon = if (queue.repeatMode == RepeatMode.One) SenadaIcons.RepeatOne else SenadaIcons.Repeat,
        modifier = Modifier.semantics { contentDescription = repeatLabel },
    ) { select { playback.setRepeatMode(nextMode) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingPosition(audio: SampleAudioState, playback: PlaybackController, scale: Float) {
    val duration = audio.durationMs
    val positionState = remember(playback) {
        playback.state.map { it.audio.positionMs }.distinctUntilChanged()
    }.collectAsState(playback.state.value.audio.positionMs)
    val scrubState = remember(audio.selection, audio.canSeek) { mutableStateOf<Float?>(null) }
    val shownSecond by remember(duration, positionState, scrubState) {
        derivedStateOf {
            val scrub = scrubState.value
            val raw = if (duration != null && scrub != null) {
                (scrub * duration.toDouble()).roundToLong()
            } else {
                positionState.value
            }
            raw.coerceAtLeast(0L) / 1_000L
        }
    }
    val hasRestoredPosition by remember(positionState) {
        derivedStateOf { positionState.value > 0L }
    }
    val sliderValue by remember(duration, positionState, scrubState) {
        derivedStateOf {
            scrubState.value ?: duration?.takeIf { it > 0L }?.let {
                val sec = positionState.value.coerceAtLeast(0L) / 1_000L
                ((sec * 1_000L).toDouble() / it).toFloat().coerceIn(0f, 1f)
            } ?: 0f
        }
    }
    val shownPosition = shownSecond * 1_000L
    val language = LocalAppLanguage.current
    val spokenPosition = accessibleDuration(shownPosition, language)
    val spokenProgress = if (duration != null) {
        appString(Res.string.player_position_of, spokenPosition, accessibleDuration(duration, language))
    } else {
        spokenPosition
    }
    val pausedPositionDesc = appString(Res.string.player_paused_position, spokenPosition)
    val defaultPositionDesc = appString(Res.string.player_position_label)
    val trackHeight = 6.dp * scale
    val trackShape = RoundedCornerShape(3.dp * scale)
    val touchHeight = (48.dp * scale).coerceAtLeast(48.dp)

    Box(Modifier.fillMaxWidth().heightIn(min = touchHeight)) {
        NowPlayingProgressBar(
            duration = duration,
            positionProvider = { positionState.value },
            scrubFractionProvider = { scrubState.value },
            trackHeight = trackHeight,
            trackShape = trackShape,
            scale = scale,
        )
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
            value = sliderValue,
            onValueChange = { scrubState.value = it },
            onValueChangeFinished = {
                if (duration != null) scrubState.value?.let { playback.seekTo((it * duration.toDouble()).roundToLong()) }
                scrubState.value = null
            },
            enabled = audio.canSeek,
            // Bilah tipis di atas tetap dapat digeser lewat area sentuh 48 dp.
            thumb = {},
            track = { Spacer(Modifier.fillMaxWidth().height(trackHeight)) },
            modifier = Modifier.fillMaxWidth().height(touchHeight).excludeSystemGestures().semantics {
                contentDescription = if (duration == null && audio.status == SampleAudioStatus.Paused &&
                    audio.selection?.isPending == true && hasRestoredPosition
                ) {
                    pausedPositionDesc
                } else {
                    defaultPositionDesc
                }
                stateDescription = spokenProgress
            },
        )
    }
}

@Composable
private fun NowPlayingProgressBar(
    duration: Long?,
    positionProvider: () -> Long,
    scrubFractionProvider: () -> Float?,
    trackHeight: Dp,
    trackShape: RoundedCornerShape,
    scale: Float,
) {
    Box(
        Modifier.padding(top = 8.dp * scale)
            .fillMaxWidth()
            .height(trackHeight)
            .clip(trackShape)
            .background(Color.White.copy(alpha = .25f))
            .drawBehind {
                val fraction = scrubFractionProvider()
                    ?: duration?.takeIf { it > 0L }?.let { (positionProvider().toDouble() / it).toFloat().coerceIn(0f, 1f) }
                    ?: 0f
                val fillWidth = size.width * fraction
                if (fillWidth > 0f) {
                    val corner = size.height / 2f
                    drawRoundRect(
                        color = PlayerWhite,
                        size = Size(fillWidth, size.height),
                        cornerRadius = CornerRadius(corner, corner),
                    )
                }
            },
    )
}

internal fun playbackStatus(
    state: PlaybackState,
    language: AppLanguage = AppStrings.currentLanguage,
): String? = when (state.audio.status) {
    SampleAudioStatus.Idle -> null
    SampleAudioStatus.Loading -> AppStrings.get(Res.string.player_loading_audio, language)
    SampleAudioStatus.Playing -> when (state.audio.selection?.source) {
        SampleAudioSource.Local -> AppStrings.get(Res.string.player_status_local, language)
        SampleAudioSource.Offline -> AppStrings.get(Res.string.player_status_offline, language)
        SampleAudioSource.Https -> AppStrings.get(Res.string.player_status_https, language)
        SampleAudioSource.YouTubeMusic -> AppStrings.get(Res.string.player_status_ytm, language)
        null -> null
    }
    SampleAudioStatus.Paused -> AppStrings.get(Res.string.player_status_paused, language)
    SampleAudioStatus.Ended -> AppStrings.get(Res.string.player_status_ended, language)
    SampleAudioStatus.Failed -> state.problemText?.resolve(language)
        ?: state.problem
        ?: AppStrings.get(Res.string.playback_audio_failed, language)
}
