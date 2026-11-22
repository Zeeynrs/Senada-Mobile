package senada.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import senada.app.shell.SenadaIcons
import senada.app.theme.SenadaPalette
import senada.catalog.youtube.resizeGoogleArtwork
import senada.model.Track

/**
 * Baris lagu dengan sampul 48 dp (atau nomor urut [trackNumber] di halaman album);
 * [favorite] menandai lagu favorit dengan ikon hati, dan [onClick] memutar lagu saat baris diketuk.
 */
@Composable
internal fun TrackRow(
    track: Track,
    favorite: Boolean = false,
    trackNumber: Int? = null,
    onClick: (() -> Unit)? = null,
    action: @Composable () -> Unit,
) {
    val metadata = listOfNotNull(
        track.artists.joinToString().takeIf { it.isNotEmpty() },
        track.durationMs?.let(::playbackTime),
    ).joinToString(" · ")
    val accessibilityLabel = if (trackNumber != null) {
        "$trackNumber. ${trackAccessibilityLabel(track, favorite)}"
    } else {
        trackAccessibilityLabel(track, favorite)
    }
    AdaptiveRow(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 2.dp),
        minPrimaryWidth = 180.dp,
        horizontalSpacing = 12.dp,
        primary = {
            val rowModifier = if (onClick != null) {
                Modifier
                    .fillMaxWidth()
                    .minTouchTarget()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = "Putar", role = Role.Button, onClick = onClick)
                    .padding(vertical = 4.dp)
            } else {
                Modifier.fillMaxWidth()
            }
            Row(
                modifier = rowModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (trackNumber != null) {
                    Box(
                        modifier = Modifier.widthIn(min = 28.dp).clearAndSetSemantics {},
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$trackNumber",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                } else {
                    // Kotak sampul 48 dp radius 8 dp tetap ada tanpa gambar agar daftar sejajar; murni dekoratif.
                    Box(
                        modifier = Modifier.size(48.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = SenadaIcons.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp),
                        )
                        track.artworkUrl?.let { url ->
                            AsyncImage(
                                model = remember(url) { resizeGoogleArtwork(url, 120) },
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).clearAndSetSemantics {
                        contentDescription = accessibilityLabel
                    },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (favorite || metadata.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            if (favorite) {
                                Icon(
                                    imageVector = SenadaIcons.HeartFilled,
                                    contentDescription = null,
                                    tint = SenadaPalette.Favorite,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                            if (metadata.isNotEmpty()) {
                                Text(
                                    metadata,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
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
