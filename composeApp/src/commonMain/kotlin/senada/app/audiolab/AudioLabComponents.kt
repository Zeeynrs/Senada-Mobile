package senada.app.audiolab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import senada.app.appString
import senada.resources.Res
import senada.resources.audiolab_ios_preparing
import senada.resources.profile_back_to
import senada.resources.state_not_selected
import senada.resources.state_selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import senada.app.SenadaCard
import senada.app.excludeSystemGestures
import senada.app.minTouchTarget
import senada.app.shell.SenadaIcons
import senada.app.theme.MonoLabel
import senada.app.theme.SenadaPalette

internal val AudioLabMoonIcon: ImageVector = ImageVector.Builder(
    name = "AudioLabMoon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(
        pathData = addPathNodes("M19 14.5A7.5 7.5 0 1 1 9.5 5a6 6 0 0 0 9.5 9.5Z"),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}.build()

/** Tombol kembali bulat 44 dp (#1D1F23) dengan area sentuh minimal 48 dp. */
@Composable
internal fun AudioLabBackButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh)
                .indication(interactionSource, LocalIndication.current),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SenadaIcons.ChevronLeft,
                contentDescription = null,
                tint = colors.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun AudioLabTopBar(previousLabel: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Geser 2 dp ke kiri agar lingkaran visual 44 dp tetap sejajar di tepi 16 dp.
        AudioLabBackButton(
            description = appString(Res.string.profile_back_to, previousLabel),
            onClick = onBack,
            modifier = Modifier.offset(x = (-2).dp),
        )
        MonoLabel(previousLabel)
    }
}

/** Label status mono dengan titik 7 dp berwarna aksen bila aktif. */
@Composable
internal fun AudioLabStatusLabel(status: CardStatus, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (status.active) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        MonoLabel(status.text)
    }
}

@Composable
internal fun AudioLabUnavailableNotice() {
    SenadaCard {
        Text(
            text = appString(Res.string.audiolab_ios_preparing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        )
    }
}

/**
 * Chip pilihan 40 dp (radius 20 dp) yang membungkus target sentuh 48 dp.
 * Gunakan jarak vertikal 0 dp pada FlowRow agar jarak visual antar baris tetap 8 dp (4 dp atas + 4 dp bawah).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AudioLabChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val background = if (selected) colors.onSurface else colors.surfaceContainerHigh
    val contentColor = if (selected) colors.background else colors.onSurface
    val shape = RoundedCornerShape(20.dp)
    val stateLabel = appString(if (selected) Res.string.state_selected else Res.string.state_not_selected)

    Box(
        modifier = modifier
            .minTouchTarget()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics {
                this.selected = selected
                stateDescription = stateLabel
                if (!enabled) disabled()
                if (enabled && onLongClick != null && onLongClickLabel != null) {
                    customActions = listOf(CustomAccessibilityAction(onLongClickLabel) {
                        onLongClick()
                        true
                    })
                }
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
                    fontWeight = FontWeight.Bold,
                ),
                color = contentColor,
            )
        }
    }
}

/**
 * Slider mendatar gaya Audio Lab: trek 6 dp (#2A2C31), isi teal, bulatan 24 dp putih,
 * dengan tinggi sentuh 48 dp dan semantik ProgressBarRangeInfo / setProgress.
 */
@Composable
internal fun AudioLabHorizontalSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    enabled: Boolean,
    contentDescription: String,
    stateDescription: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background == SenadaPalette.DarkBackground
    val trackColor = colors.outline
    val fillColor = colors.primary
    val thumbColor = if (dark) SenadaPalette.DarkText else Color.White
    val density = LocalDensity.current
    val thumbRadiusPx = with(density) { 12.dp.toPx() }
    val min = valueRange.start
    val max = valueRange.endInclusive
    val span = (max - min).coerceAtLeast(0.0001f)
    val steps = ((span / step).roundToInt() - 1).coerceAtLeast(0)
    val clamped = value.coerceIn(min, max)
    val latestOnChange by rememberUpdatedState(onValueChange)

    fun snapValue(raw: Float): Float {
        val count = ((raw.coerceIn(min, max) - min) / step).roundToInt()
        return (min + count * step).coerceIn(min, max)
    }

    fun updateFromX(x: Float, width: Float) {
        val usable = (width - thumbRadiusPx * 2f).coerceAtLeast(1f)
        val fraction = ((x - thumbRadiusPx) / usable).coerceIn(0f, 1f)
        latestOnChange(snapValue(min + fraction * span))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .excludeSystemGestures()
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = stateDescription
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, valueRange, steps)
                if (enabled) {
                    setProgress { target ->
                        latestOnChange(snapValue(target))
                        true
                    }
                } else {
                    disabled()
                }
            }
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(min, max, step) {
                            detectTapGestures { offset -> updateFromX(offset.x, size.width.toFloat()) }
                        }
                        .pointerInput(min, max, step) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset -> updateFromX(offset.x, size.width.toFloat()) },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    updateFromX(change.position.x, size.width.toFloat())
                                },
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackHeight = 6.dp.toPx()
            val trackRadius = 3.dp.toPx()
            val centerY = size.height / 2f
            val fraction = ((clamped - min) / span).coerceIn(0f, 1f)
            val usable = (size.width - thumbRadiusPx * 2f).coerceAtLeast(0f)
            val thumbX = thumbRadiusPx + fraction * usable

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackRadius, trackRadius),
            )
            if (fraction > 0f) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(thumbX, trackHeight),
                    cornerRadius = CornerRadius(trackRadius, trackRadius),
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = if (dark) 0.40f else 0.22f), Color.Transparent),
                    center = Offset(thumbX, centerY + 1.5.dp.toPx()),
                    radius = thumbRadiusPx + 4.dp.toPx(),
                ),
                radius = thumbRadiusPx + 4.dp.toPx(),
                center = Offset(thumbX, centerY + 1.5.dp.toPx()),
            )
            drawCircle(
                color = thumbColor,
                radius = thumbRadiusPx,
                center = Offset(thumbX, centerY),
            )
        }
    }
}
