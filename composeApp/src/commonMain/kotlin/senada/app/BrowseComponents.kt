package senada.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import senada.app.theme.LocalSenadaLook
import senada.app.theme.MonoLabel

private const val FilterChipTransitionMs = 150

/** Judul halaman display v2 (40 sp, 800, -0,05 em) untuk tab Cari dan Library. */
@Composable
internal fun browseDisplayTitleStyle(): TextStyle =
    MaterialTheme.typography.displayMedium.copy(
        fontSize = 40.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.05).em,
    )

/** Judul seksi v2 (20 sp, 800, -0,02 em), sejajar dengan judul bagian di Profil. */
@Composable
internal fun browseSectionHeadingStyle(): TextStyle =
    MaterialTheme.typography.headlineSmall.copy(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.02).em,
    )

/** Judul baris v2 (15 sp, 700) untuk daftar lagu, riwayat, dan entitas katalog. */
@Composable
internal fun browseRowTitleStyle(): TextStyle =
    MaterialTheme.typography.titleSmall.copy(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.em,
    )

/** Subjudul baris v2 (13 sp, 500) agar metadata lagu dan koleksi tetap nyaman dibaca. */
@Composable
internal fun browseRowSubtitleStyle(): TextStyle =
    MaterialTheme.typography.bodySmall.copy(
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.em,
    )

/**
 * Pil filter monokrom v2 setinggi visual 40 dp (radius 20 dp) dalam target sentuh minimal 48 dp,
 * mengikuti gaya chip Equalizer, Efek suara, dan Tampilan.
 */
@Composable
internal fun SenadaFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Tab,
    enabled: Boolean = true,
    stateLabel: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val look = LocalSenadaLook.current
    val interactionSource = remember { MutableInteractionSource() }
    val background by animateColorAsState(
        targetValue = if (selected) colors.onSurface else colors.surfaceContainerHigh,
        animationSpec = look.tweenOrSnap(FilterChipTransitionMs),
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.background else colors.onSurface,
        animationSpec = look.tweenOrSnap(FilterChipTransitionMs),
    )
    val shape = RoundedCornerShape(20.dp)
    val resolvedState = stateLabel ?: if (selected) "Dipilih" else "Tidak dipilih"

    Box(
        modifier = modifier
            .minTouchTarget()
            .selectable(
                selected = selected,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = role,
                onClick = onClick,
            )
            .semantics {
                stateDescription = resolvedState
                if (!enabled) disabled()
            }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
                .clip(shape)
                .background(background)
                .indication(interactionSource, LocalIndication.current)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.em,
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Kepala seksi v2 dengan judul tebal 20 sp dan label mono atau aksi kanan opsional. */
@Composable
internal fun BrowseSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    monoLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val titleContent: @Composable () -> Unit = {
        Text(
            text = title,
            style = browseSectionHeadingStyle(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
    }
    val rightContent: (@Composable () -> Unit)? = trailing ?: monoLabel?.takeIf { it.isNotBlank() }?.let { label ->
        { MonoLabel(label) }
    }
    if (rightContent != null) {
        AdaptiveRow(
            modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
            minPrimaryWidth = 150.dp,
            horizontalSpacing = 12.dp,
            verticalSpacing = 4.dp,
            primary = titleContent,
            trailing = rightContent,
        )
    } else {
        Box(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            titleContent()
        }
    }
}

/** Label seksi mono kecil (JetBrains Mono 11 sp, kapital) untuk tajuk kategori daftar. */
@Composable
internal fun BrowseMonoHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    MonoLabel(
        text = text,
        modifier = modifier
            .padding(start = 4.dp, end = 4.dp, top = 4.dp)
            .semantics { heading() },
    )
}

/** Kartu pemberitahuan / keadaan kosong / petunjuk v2 berbasis [SenadaCard]. */
@Composable
internal fun BrowseNoticeCard(
    message: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    polite: Boolean = true,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    primaryAction: Boolean = false,
    onAction: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    SenadaCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            eyebrow?.takeIf { it.isNotBlank() }?.let { MonoLabel(it) }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (actionLabel != null && !primaryAction) colors.onSurface else colors.onSurfaceVariant,
                modifier = if (polite) {
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                } else {
                    Modifier
                },
            )
            if (actionLabel != null && onAction != null) {
                if (primaryAction) {
                    Button(
                        onClick = onAction,
                        enabled = actionEnabled,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.onSurface,
                            contentColor = colors.background,
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        modifier = Modifier.minTouchTarget(),
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                        )
                    }
                } else {
                    Button(
                        onClick = onAction,
                        enabled = actionEnabled,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceContainerHighest,
                            contentColor = colors.onSurface,
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.minTouchTarget(),
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Tombol pil bergaris tipis v2 (radius 24 dp, tinggi minimal 48 dp) untuk "Muat lebih banyak". */
@Composable
internal fun BrowseLoadMoreButton(
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        enabled = !loading,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(
            text = if (loading) "Memuat…" else "Muat lebih banyak",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/**
 * Placeholder daftar statis (hemat baterai, tanpa animasi terus-menerus) di dalam [SenadaCard]
 * saat hasil pencarian atau halaman katalog sedang dimuat.
 */
@Composable
internal fun BrowseRowSkeletonCard(
    statusLabel: String,
    modifier: Modifier = Modifier,
    rowCount: Int = 3,
    circularLeading: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val blockColor = colors.surfaceContainerHigh
    val leadingShape = if (circularLeading) CircleShape else RoundedCornerShape(LocalSenadaLook.current.listCoverRadius)
    SenadaCard(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = statusLabel
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(rowCount.coerceAtLeast(1)) { index ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(leadingShape)
                            .background(blockColor)
                            .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), leadingShape),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val titleFraction = when (index % 3) {
                            0 -> 0.72f
                            1 -> 0.58f
                            else -> 0.80f
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(titleFraction)
                                .height(14.dp)
                                .clip(CircleShape)
                                .background(blockColor),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.42f)
                                .height(11.dp)
                                .clip(CircleShape)
                                .background(blockColor),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}
