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
import senada.app.LibraryActions
import senada.app.TrackRow
import senada.app.appString
import senada.app.minTouchTarget
import senada.model.Track
import senada.resources.Res
import senada.resources.a11y_play_track
import senada.resources.action_play
import senada.resources.action_play_all
import senada.resources.most_played_empty_period
import senada.resources.most_played_last_30_days

internal fun LazyListScope.mostPlayedScreen(
    tracks: List<Track>,
    favoriteIds: Set<String>,
    actions: LibraryActions,
) {
    item(key = "most-played-header") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tracks.isEmpty()) {
                Text(appString(Res.string.most_played_empty_period))
            } else {
                Text(appString(Res.string.most_played_last_30_days), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { actions.playAllMostPlayed(tracks) },
                    modifier = Modifier.minTouchTarget(),
                ) { Text(appString(Res.string.action_play_all)) }
            }
        }
    }
    items(tracks, key = { it.id }) { track ->
        TrackRow(track, favorite = track.id in favoriteIds) {
            val playDesc = appString(Res.string.a11y_play_track, track.title)
            TextButton(
                onClick = { actions.playAll(listOf(track)) },
                modifier = Modifier.minTouchTarget().semantics { contentDescription = playDesc },
            ) { Text(appString(Res.string.action_play), modifier = Modifier.clearAndSetSemantics {}) }
        }
    }
}
