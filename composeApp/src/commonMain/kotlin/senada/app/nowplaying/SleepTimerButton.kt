package senada.app.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import senada.app.AppLanguage
import senada.app.AppStrings
import senada.app.LocalAppLanguage
import senada.app.appString
import senada.app.minTouchTarget
import senada.playback.PlaybackController
import senada.playback.SleepTimerState
import senada.playback.sleepTimerMinutes
import senada.resources.Res
import senada.resources.sleep_timer_a11y_active
import senada.resources.sleep_timer_end_of_this_track
import senada.resources.sleep_timer_end_of_track
import senada.resources.sleep_timer_stop_in
import senada.resources.sleep_timer_title
import senada.resources.sleep_timer_turn_off
import senada.resources.sleep_minutes

internal fun SleepTimerState.statusText(minuteUnit: String): String? =
    statusText(AppStrings.currentLanguage, minuteUnit)

internal fun SleepTimerState.statusText(
    language: AppLanguage = AppStrings.currentLanguage,
    minuteUnit: String? = null,
): String? = when (this) {
    SleepTimerState.Off -> null
    SleepTimerState.EndOfTrack -> AppStrings.get(Res.string.sleep_timer_end_of_track, language)
    is SleepTimerState.CountingDown -> {
        val duration = if (minuteUnit != null) {
            "$minutesRemaining $minuteUnit"
        } else {
            AppStrings.plural(Res.plurals.sleep_minutes, minutesRemaining, language, minutesRemaining)
        }
        AppStrings.get(Res.string.sleep_timer_stop_in, language, duration)
    }
}

@Composable
internal fun SleepTimerButton(state: SleepTimerState, canStopAfterTrack: Boolean, playback: PlaybackController, scale: Float) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val running = state != SleepTimerState.Off
    val language = LocalAppLanguage.current
    val label = state.statusText(language)?.let { appString(Res.string.sleep_timer_a11y_active, it) }
        ?: appString(Res.string.sleep_timer_title)

    Box {
        PlayerGlassButton(
            icon = NowPlayingIcons.Moon,
            label = label,
            scale = scale,
            tint = if (running) colors.primary else PlayerWhite,
        ) { expanded = true }
        SleepTimerMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            state = state,
            canStopAfterTrack = canStopAfterTrack,
            playback = playback,
        )
    }
}

@Composable
internal fun SleepTimerMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    state: SleepTimerState,
    canStopAfterTrack: Boolean,
    playback: PlaybackController,
) {
    val running = state != SleepTimerState.Off
    val language = LocalAppLanguage.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        sleepTimerMinutes.forEach { minutes ->
            DropdownMenuItem(
                text = { Text(AppStrings.plural(Res.plurals.sleep_minutes, minutes, language, minutes)) },
                onClick = {
                    playback.startSleepTimer(minutes)
                    onDismiss()
                },
                modifier = Modifier.minTouchTarget(),
            )
        }
        DropdownMenuItem(
            text = { Text(appString(Res.string.sleep_timer_end_of_this_track)) },
            enabled = canStopAfterTrack,
            onClick = {
                playback.stopAfterCurrentTrack()
                onDismiss()
            },
            modifier = Modifier.minTouchTarget(),
        )
        DropdownMenuItem(
            text = { Text(appString(Res.string.sleep_timer_turn_off)) },
            enabled = running,
            onClick = {
                playback.cancelSleepTimer()
                onDismiss()
            },
            modifier = Modifier.minTouchTarget(),
        )
    }
}
