package senada.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import senada.catalog.youtube.resizeGoogleArtwork
import senada.model.Track

/** Baris lagu dengan sampul; [favorite] menandai lagu favorit dengan ♥. */
@Composable
internal fun TrackRow(track: Track, favorite: Boolean = false, action: @Composable () -> Unit) {
    val details = listOfNotNull(
        "♥".takeIf { favorite },
        track.artists.joinToString().takeIf { it.isNotEmpty() },
        track.durationMs?.let(::playbackTime),
    )
    AdaptiveRow(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        minPrimaryWidth = 180.dp,
        primary = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Kotak tetap ada tanpa sampul agar judul di daftar sejajar; sampul murni dekoratif.
                Box(
                    Modifier.size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                ) {
                    track.artworkUrl?.let { url ->
                        AsyncImage(
                            model = remember(url) { resizeGoogleArtwork(url, 120) },
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Column(
                    Modifier.weight(1f).clearAndSetSemantics {
                        contentDescription = trackAccessibilityLabel(track, favorite)
                    },
                ) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        trailing = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                action()
            }
        },
    )
}
