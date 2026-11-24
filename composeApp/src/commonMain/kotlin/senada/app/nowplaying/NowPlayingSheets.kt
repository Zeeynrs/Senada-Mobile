package senada.app.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.TrackMenu
import senada.app.TrackRow
import senada.app.shell.SenadaIcons
import senada.playback.PlaybackController
import senada.playback.PlaybackState

@Composable
internal fun PlayerSheetContent(
    title: String,
    closeLabel: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
            .padding(horizontal = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title, modifier = Modifier.weight(1f).semantics { heading() },
                color = PlayerWhite, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold,
            )
            PlayerGlassButton(SenadaIcons.Close, closeLabel, scale = 1f, onClick = onClose)
        }
        content()
    }
}

@Composable
internal fun PlayerQueue(state: PlaybackState, playback: PlaybackController, library: LibraryState, actions: LibraryActions) {
    if (state.queue.entries.isEmpty()) {
        Text("Antrean kosong.", color = PlayerWhite)
        return
    }
    val shape = RoundedCornerShape(16.dp)
    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.queue.entries, key = { it.id }) { entry ->
            Box(
                Modifier.fillMaxWidth()
                    .playerGlass(shape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                val playing = state.audio.isActive && state.audio.selection?.queueEntryId == entry.id
                TrackRow(
                    track = entry.track,
                    favorite = entry.track.id in library.favoriteIds,
                    onClick = { if (playing) playback.pause() else playback.playEntry(entry.id) },
                    playing = playing,
                    onClickLabel = if (playing) "Jeda ${entry.track.title}" else "Putar ${entry.track.title}",
                ) {
                    TrackMenu(
                        entry.track, library, actions,
                        onRemoveFromQueue = { playback.remove(entry.id) },
                    )
                }
            }
        }
    }
}
