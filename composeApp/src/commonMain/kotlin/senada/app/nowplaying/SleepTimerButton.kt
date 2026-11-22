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
import senada.app.minTouchTarget
import senada.app.shell.SenadaIcons
import senada.playback.PlaybackController
import senada.playback.SleepTimerState
import senada.playback.sleepTimerMinutes

internal fun SleepTimerState.statusText(minuteUnit: String): String? = when (this) {
    SleepTimerState.Off -> null
    SleepTimerState.EndOfTrack -> "Berhenti di akhir lagu"
    is SleepTimerState.CountingDown -> "Berhenti dalam $minutesRemaining $minuteUnit"
}

@Composable
internal fun SleepTimerButton(state: SleepTimerState, canStopAfterTrack: Boolean, playback: PlaybackController) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val running = state != SleepTimerState.Off
    val label = state.statusText("menit")?.let { "Timer tidur. $it" } ?: "Timer tidur"

    Box {
        PlayerIcon(
            icon = SenadaIcons.Clock,
            label = label,
            tint = if (running) colors.primary else colors.onSurfaceVariant,
        ) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sleepTimerMinutes.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text("$minutes menit") },
                    onClick = {
                        playback.startSleepTimer(minutes)
                        expanded = false
                    },
                    modifier = Modifier.minTouchTarget(),
                )
            }
            DropdownMenuItem(
                text = { Text("Akhir lagu ini") },
                enabled = canStopAfterTrack,
                onClick = {
                    playback.stopAfterCurrentTrack()
                    expanded = false
                },
                modifier = Modifier.minTouchTarget(),
            )
            DropdownMenuItem(
                text = { Text("Matikan") },
                enabled = running,
                onClick = {
                    playback.cancelSleepTimer()
                    expanded = false
                },
                modifier = Modifier.minTouchTarget(),
            )
        }
    }
}
