package senada.app.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import senada.app.CollectionBackButton
import senada.app.LibraryActions
import senada.app.TrackRow
import senada.app.minTouchTarget
import senada.model.Track

internal fun LazyListScope.mostPlayedScreen(
    tracks: List<Track>,
    favoriteIds: Set<String>,
    actions: LibraryActions,
    onClose: () -> Unit,
) {
    item(key = "most-played-header") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CollectionBackButton("Beranda", onClose)
            Text(
                "Paling sering diputar",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (tracks.isEmpty()) {
                Text("Belum ada lagu yang diputar dalam 30 hari terakhir.")
            } else {
                Text("30 hari terakhir", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { actions.playAllMostPlayed(tracks) },
                    modifier = Modifier.minTouchTarget(),
                ) { Text("Putar semua") }
            }
        }
    }
    items(tracks, key = { it.id }) { track ->
        TrackRow(track, favorite = track.id in favoriteIds) {
            TextButton(
                onClick = { actions.playAll(listOf(track)) },
                modifier = Modifier.minTouchTarget().semantics { contentDescription = "Putar ${track.title}" },
            ) { Text("Putar", modifier = Modifier.clearAndSetSemantics {}) }
        }
    }
}
