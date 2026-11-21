package senada.app.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.SampleAudioControls
import senada.app.SenadaSources
import senada.app.TrackMenu
import senada.app.TrackRow
import senada.app.minTouchTarget
import senada.app.shell.SenadaIcons
import senada.lyrics.LyricsPanel
import senada.playback.PlaybackController
import senada.playback.PlaybackState

/**
 * Sedang diputar sebagai layar penuh di atas tab. Untuk Fase 2 isinya masih kontrol, lirik dan antrean
 * yang lama; gaya imersif menyusul di Fase 3.
 */
@Composable
internal fun NowPlayingScreen(
    state: PlaybackState,
    playback: PlaybackController,
    library: LibraryState,
    actions: LibraryActions,
    screenVisible: Boolean,
    onClose: () -> Unit,
) {
    val audio = state.audio
    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "Tutup" }) {
                        Icon(SenadaIcons.ChevronDown, contentDescription = null)
                    }
                    Text(
                        "Sedang diputar",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            item(key = "controls") {
                SampleAudioControls(
                    state = audio,
                    onPause = playback::pause,
                    onResume = playback::resume,
                    canResume = audio.selection != null || state.queue.entries.isNotEmpty(),
                    onSeek = playback::seekTo,
                    onPrevious = playback::previous,
                    onNext = playback::next,
                    canGoPrevious = audio.selection != null,
                    canGoNext = state.canGoNext,
                    repeatMode = state.queue.repeatMode,
                    onRepeatModeChange = playback::setRepeatMode,
                    shuffleEnabled = state.queue.shuffleEnabled,
                    onShuffleChange = playback::setShuffleEnabled,
                    problem = state.problem,
                )
            }
            state.currentEntry?.track?.let { track ->
                item(key = "lyrics-toggle") {
                    TextButton(
                        onClick = { lyricsOpen = !lyricsOpen },
                        modifier = Modifier.minTouchTarget().semantics {
                            stateDescription = if (lyricsOpen) "Terbuka" else "Tertutup"
                        },
                    ) { Text(if (lyricsOpen) "Tutup lirik" else "Lirik") }
                }
                if (lyricsOpen && screenVisible) {
                    item(key = "lyrics-${track.id}") { LyricsPanel(track, audio.positionMs, SenadaSources.lyrics) }
                }
            }
            item(key = "queue-heading") {
                Text("Antrean", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            if (state.queue.entries.isEmpty()) {
                item(key = "queue-empty") { Text("Antrean kosong.") }
            }
            items(state.queue.entries, key = { "entry-${it.id}" }) { entry ->
                val isPlayingEntry = audio.isActive && audio.selection?.queueEntryId == entry.id
                TrackRow(entry.track, favorite = entry.track.id in library.favoriteIds) {
                    TextButton(
                        onClick = { if (isPlayingEntry) playback.pause() else playback.playEntry(entry.id) },
                        modifier = Modifier.minTouchTarget().semantics {
                            contentDescription = if (isPlayingEntry) "Jeda ${entry.track.title}" else "Putar ${entry.track.title}"
                        },
                    ) { Text(if (isPlayingEntry) "Jeda" else "Putar") }
                    TextButton(
                        onClick = { playback.remove(entry.id) },
                        modifier = Modifier.minTouchTarget().semantics {
                            contentDescription = "Hapus ${entry.track.title} dari antrean"
                        },
                    ) { Text("Hapus") }
                    TrackMenu(entry.track, library, actions)
                }
            }
        }
    }
}
