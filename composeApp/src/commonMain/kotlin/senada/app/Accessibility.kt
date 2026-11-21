package senada.app

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import senada.model.Track

/** Memformat durasi atau posisi pemutaran menjadi frasa bahasa Indonesia untuk pembaca layar. */
internal fun accessibleDuration(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    val parts = buildList {
        if (hours > 0) add("$hours jam")
        if (minutes > 0) add("$minutes menit")
        if (seconds > 0 || isEmpty()) add("$seconds detik")
    }
    return parts.joinToString(" ")
}

/** Ringkasan lagu untuk pembaca layar: judul, status favorit, artis, dan durasi tanpa simbol dekoratif. */
internal fun trackAccessibilityLabel(track: Track, favorite: Boolean): String = listOfNotNull(
    track.title.takeIf { it.isNotBlank() },
    "Favorit".takeIf { favorite },
    track.artists.filter { it.isNotBlank() }.joinToString().takeIf { it.isNotEmpty() },
    track.durationMs?.let(::accessibleDuration),
).joinToString(", ")

/**
 * Menyusun konten utama dan aksi secara mendatar selama ruang konten utama masih memadai;
 * bila skala font besar atau layar sempit membuat aksi menekan konten utama, aksi turun ke baris berikutnya.
 */
@Composable
internal fun AdaptiveRow(
    modifier: Modifier = Modifier,
    minPrimaryWidth: Dp = 180.dp,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 4.dp,
    primary: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Box { primary() }
            Box { trailing() }
        },
    ) { measurables, constraints ->
        val hGap = horizontalSpacing.roundToPx()
        val vGap = verticalSpacing.roundToPx()
        val minPrimary = minPrimaryWidth.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val trailingPlaceable = measurables[1].measure(loose)
        val hasTrailing = trailingPlaceable.width > 0 || trailingPlaceable.height > 0
        val effectiveHGap = if (hasTrailing) hGap else 0
        val availableWidth = constraints.maxWidth
        val remaining = availableWidth - trailingPlaceable.width - effectiveHGap
        val sideBySide = !constraints.hasBoundedWidth || !hasTrailing || remaining >= minPrimary
        if (sideBySide) {
            val primaryConstraints = if (constraints.hasBoundedWidth) {
                val targetWidth = remaining.coerceAtLeast(0)
                loose.copy(minWidth = targetWidth, maxWidth = targetWidth)
            } else {
                loose
            }
            val primaryPlaceable = measurables[0].measure(primaryConstraints)
            val width = if (constraints.hasBoundedWidth) {
                availableWidth
            } else {
                primaryPlaceable.width + effectiveHGap + trailingPlaceable.width
            }.coerceIn(constraints.minWidth, constraints.maxWidth)
            val height = maxOf(primaryPlaceable.height, trailingPlaceable.height)
                .coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                primaryPlaceable.placeRelative(0, (height - primaryPlaceable.height) / 2)
                if (hasTrailing) {
                    trailingPlaceable.placeRelative(
                        width - trailingPlaceable.width,
                        (height - trailingPlaceable.height) / 2,
                    )
                }
            }
        } else {
            val primaryPlaceable = measurables[0].measure(
                loose.copy(minWidth = availableWidth, maxWidth = availableWidth),
            )
            val width = availableWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
            val height = (primaryPlaceable.height + vGap + trailingPlaceable.height)
                .coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                primaryPlaceable.placeRelative(0, 0)
                trailingPlaceable.placeRelative(0, primaryPlaceable.height + vGap)
            }
        }
    }
}
