package senada.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import senada.catalog.youtube.resizeGoogleArtwork
import senada.playback.PlaybackState

private val MiniArtworkSize = 48.dp
private const val OpenNowPlayingLabel = "Buka Sedang diputar"

/** Mini player tampil bila ada yang bisa diputar: antrean berisi lagu, atau sampel uji sedang dipilih. */
internal fun showsMiniPlayer(state: PlaybackState): Boolean =
    state.queue.entries.isNotEmpty() || state.audio.selection != null

/**
 * Mini player mengambang di atas navigasi. Ketuk atau geser ke atas untuk membuka Sedang diputar.
 */
@Composable
internal fun MiniPlayer(
    state: PlaybackState,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.currentEntry?.track ?: state.queue.entries.firstOrNull()?.track
    val title = track?.title ?: state.audio.selection?.title.orEmpty()
    val subtitle = track?.artists?.joinToString().orEmpty()
    val playing = state.audio.isActive
    GlassSurface(shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = modifier
                .clickable(onClick = onOpen)
                .semantics {
                    contentDescription = OpenNowPlayingLabel
                    customActions = listOf(CustomAccessibilityAction(OpenNowPlayingLabel) {
                        onOpen()
                        true
                    })
                }
                .heightIn(min = FloatingBarHeight)
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Sampul bundar mengisi tinggi pil dengan jarak yang sama di setiap sisi; tanpa sampul tampil ikon lagu.
            Box(
                Modifier.size(MiniArtworkSize).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SenadaIcons.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                track?.artworkUrl?.let { url ->
                    AsyncImage(
                        model = remember(url) { resizeGoogleArtwork(url, 144) },
                        contentDescription = null,
                        modifier = Modifier.size(MiniArtworkSize),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.semantics { contentDescription = if (playing) "Jeda" else "Putar" },
            ) {
                Icon(if (playing) SenadaIcons.Pause else SenadaIcons.Play, contentDescription = null)
            }
            IconButton(
                onClick = onNext,
                enabled = state.canGoNext,
                modifier = Modifier.semantics { contentDescription = "Berikutnya" },
            ) {
                Icon(SenadaIcons.Next, contentDescription = null)
            }
        }
    }
}
