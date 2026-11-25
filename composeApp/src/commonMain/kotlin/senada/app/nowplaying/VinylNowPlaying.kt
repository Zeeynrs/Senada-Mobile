package senada.app.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import senada.app.shell.NowPlayingPanelState
import senada.app.shell.SenadaIcons
import senada.app.shell.nowPlayingSwipe
import senada.playback.PlaybackController
import senada.playback.PlaybackState

@Composable
internal fun VinylNowPlaying(
    state: PlaybackState,
    playback: PlaybackController,
    artwork: String?,
    visible: Boolean,
    safeTop: Dp,
    safeBottom: Dp,
    panel: NowPlayingPanelState,
    onClose: () -> Unit,
    onTheme: () -> Unit,
    onLyricsSettings: () -> Unit,
    onQueue: () -> Unit,
    lyrics: @Composable () -> Unit,
    trackInfo: @Composable (Float) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 540.dp && maxWidth > maxHeight
        val controlsScale = (maxWidth.value / 480f).coerceIn(.70f, .90f)
        val record: @Composable (Modifier) -> Unit = { modifier ->
            Box(modifier) {
                VinylTurntable(artwork, state.audio, playback, visible, Modifier.fillMaxSize())
                val timer by playback.sleepTimerState.collectAsState()
                Row(
                    Modifier.fillMaxWidth().padding(top = safeTop)
                        .nowPlayingSwipe(panel, opening = false).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerGlassButton(SenadaIcons.Close, "Tutup", scale = .85f, onClick = onClose)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerGlassButton(SenadaIcons.Appearance, "Tema Full View", scale = .85f, onClick = onTheme)
                        PlayerGlassButton(SenadaIcons.Settings, "Pengaturan lirik", scale = .85f, onClick = onLyricsSettings)
                        SleepTimerButton(timer, state.audio.canStopAfterTrack, playback, scale = .85f)
                        PlayerGlassButton(NowPlayingIcons.Queue, "Antrean", scale = .85f, onClick = onQueue)
                    }
                }
            }
        }
        val details: @Composable (Modifier) -> Unit = { modifier ->
            Column(
                modifier.padding(start = 20.dp, end = 20.dp, bottom = maxOf(8.dp, safeBottom)),
            ) {
                Box(Modifier.weight(1f).fillMaxWidth()) { lyrics() }
                trackInfo(controlsScale)
                NowPlayingPosition(state.audio, playback, controlsScale)
                NowPlayingControls(state, playback, controlsScale)
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                record(Modifier.weight(1.1f).fillMaxHeight().padding(bottom = safeBottom))
                details(Modifier.weight(1f).fillMaxHeight().padding(top = maxOf(safeTop, 8.dp)))
            }
        } else {
            val recordHeight = minOf(maxWidth * .99f, maxHeight * .52f)
            Column(Modifier.fillMaxSize()) {
                record(Modifier.fillMaxWidth().height(recordHeight))
                details(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}
