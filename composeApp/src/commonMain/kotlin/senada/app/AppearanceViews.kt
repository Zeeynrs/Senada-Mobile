package senada.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import senada.app.nowplaying.FullViewThemeCard
import senada.app.theme.AccentChoice
import senada.app.theme.LocalSenadaLook
import senada.app.theme.MonoLabel

private val ThemeModesInDisplayOrder = listOf(ThemeMode.Dark, ThemeMode.Light, ThemeMode.System)
private val CoverPreviewDarkColor = Color(0xFFD9D9DC)
private val CoverPreviewLightColor = Color(0xFFD0CEC8)
private const val ControlTransitionMs = 150
private const val CoverPreviewTransitionMs = 120

/** Mengubah jarak geser horizontal menjadi sudut sampul kelipatan 2 dp dalam rentang 0–28 dp. */
internal fun dragCoverRadius(
    startRadius: Int,
    deltaPx: Float,
    dragSpanPx: Float,
): Int {
    if (dragSpanPx <= 0f) return clampCoverRadius(startRadius)
    val span = MAX_COVER_RADIUS_DP - MIN_COVER_RADIUS_DP
    val raw = startRadius + (deltaPx / dragSpanPx) * span
    val stepped = (raw / COVER_RADIUS_STEP_DP).roundToInt() * COVER_RADIUS_STEP_DP
    return clampCoverRadius(stepped)
}

/** Menggeser sudut sampul sebanyak [deltaSteps] langkah 2 dp, di-clamp pada 0–28 dp. */
internal fun stepCoverRadiusBy(currentRadius: Int, deltaSteps: Int): Int =
    clampCoverRadius(currentRadius + deltaSteps * COVER_RADIUS_STEP_DP)

/** Halaman Tampilan v2: Tema, Warna aksen, Sudut sampul, serta Pemutar & efek persis mockup. */
@Composable
internal fun AppearanceSection(preferences: AppearancePreferences) {
    val settings = preferences.settings ?: Appearance()
    val ready = preferences.settings != null
    val look = LocalSenadaLook.current
    val followCover = settings.accent == AccentChoice.FollowCover

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ThemeModeCard(
            selected = settings.mode,
            enabled = ready,
            onSelect = preferences::selectMode,
        )
        AccentCard(
            selectedSwatch = if (followCover) preferences.lastFixedAccent else settings.accent,
            followCover = followCover,
            enabled = ready,
            onSelectSwatch = preferences::selectAccent,
            onFollowCoverChange = preferences::setFollowCover,
        )
        CoverRadiusCard(
            radiusDp = settings.coverRadius,
            enabled = ready,
            onRadiusChange = preferences::setCoverRadius,
        )
        PlayerAndEffectsCard(
            playerBackdropFromCover = settings.playerBackdropFromCover,
            glass = settings.glass,
            animation = settings.animation,
            systemReduceMotion = look.systemReduceMotion,
            enabled = ready,
            onPlayerBackdropChange = preferences::setPlayerBackdropFromCover,
            onGlassChange = preferences::setGlass,
            onSelectAnimation = preferences::selectAnimationSpeed,
        )
        FullViewThemeCard(settings.fullViewTheme, ready, preferences::selectFullViewTheme)
    }
}

@Composable
private fun ThemeModeCard(
    selected: ThemeMode,
    enabled: Boolean,
    onSelect: (ThemeMode) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    SenadaCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(1.dp).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MonoLabel("Tema", Modifier.semantics { heading() })
            if (largeFont) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(colors.background)
                        .padding(4.dp)
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (mode in ThemeModesInDisplayOrder) {
                        ThemeSegment(
                            mode = mode,
                            selected = mode == selected,
                            enabled = enabled,
                            largeFont = true,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelect(mode) },
                        )
                    }
                }
            } else {
                // Container 52 dp = 44 dp pil + 4 dp padding atas/bawah; target sentuh 48 dp memakai 2 dp di tiap sisi.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(colors.background)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (mode in ThemeModesInDisplayOrder) {
                        ThemeSegment(
                            mode = mode,
                            selected = mode == selected,
                            enabled = enabled,
                            largeFont = false,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(mode) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSegment(
    mode: ThemeMode,
    selected: Boolean,
    enabled: Boolean,
    largeFont: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val look = LocalSenadaLook.current
    val background by animateColorAsState(
        targetValue = if (selected) colors.onSurface else Color.Transparent,
        animationSpec = look.tweenOrSnap(ControlTransitionMs),
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.background else colors.onSurfaceVariant,
        animationSpec = look.tweenOrSnap(ControlTransitionMs),
    )
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { stateDescription = if (selected) "Dipilih" else "Tidak dipilih" }
            .padding(vertical = if (largeFont) 0.dp else 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (largeFont) 48.dp else 44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(background)
                .padding(horizontal = 8.dp, vertical = if (largeFont) 10.dp else 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = mode.label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = if (largeFont) TextUnit.Unspecified else 18.sp,
                    letterSpacing = 0.em,
                ),
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = if (largeFont) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AccentCard(
    selectedSwatch: AccentChoice,
    followCover: Boolean,
    enabled: Boolean,
    onSelectSwatch: (AccentChoice) -> Unit,
    onFollowCoverChange: (Boolean) -> Unit,
) {
    SenadaCard {
        // Target sentuh bulatan 48 dp menambah 2 dp di atas/bawah bulatan 44 dp -> 12 + 2 = 14 dp jarak visual.
        Column(
            modifier = Modifier.fillMaxWidth().padding(1.dp).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonoLabel("Warna aksen", Modifier.semantics { heading() })
            AccentSwatchRow(
                selected = selectedSwatch,
                enabled = enabled,
                onSelect = onSelectSwatch,
            )
            AppearanceSwitchRow(
                title = "Warna mengikuti sampul",
                subtitle = "Aksen berubah sesuai lagu yang diputar",
                checked = followCover,
                enabled = enabled,
                onCheckedChange = onFollowCoverChange,
            )
        }
    }
}

@Composable
private fun CoverRadiusCard(
    radiusDp: Int,
    enabled: Boolean,
    onRadiusChange: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val look = LocalSenadaLook.current
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    val previewColor = if (colors.background.luminance() < 0.5f) CoverPreviewDarkColor else CoverPreviewLightColor
    val animatedRadius by animateDpAsState(
        targetValue = radiusDp.dp,
        animationSpec = look.tweenOrSnap(CoverPreviewTransitionMs),
    )
    val currentRadius by rememberUpdatedState(radiusDp)
    val currentOnChange by rememberUpdatedState(onRadiusChange)
    var cardWidthPx by remember { mutableStateOf(0f) }

    val heightModifier = if (largeFont) Modifier.heightIn(min = 150.dp) else Modifier.height(150.dp)
    SenadaCard(modifier = heightModifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (largeFont) Modifier.heightIn(min = 150.dp) else Modifier.fillMaxHeight())
                .onSizeChanged { cardWidthPx = it.width.toFloat() }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    var startRadius = currentRadius
                    var dragTotal = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            startRadius = currentRadius
                            dragTotal = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragTotal += dragAmount
                            val dragSpanPx = (cardWidthPx * 0.6f).coerceAtLeast(1f)
                            val next = dragCoverRadius(startRadius, dragTotal, dragSpanPx)
                            if (next != currentRadius) currentOnChange(next)
                        },
                    )
                }
                .semantics {
                    stateDescription = "$radiusDp dp"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = radiusDp.toFloat(),
                        range = MIN_COVER_RADIUS_DP.toFloat()..MAX_COVER_RADIUS_DP.toFloat(),
                        steps = (MAX_COVER_RADIUS_DP - MIN_COVER_RADIUS_DP) / COVER_RADIUS_STEP_DP - 1,
                    )
                    setProgress { target ->
                        if (!enabled) return@setProgress false
                        val stepped = (target / COVER_RADIUS_STEP_DP).roundToInt() * COVER_RADIUS_STEP_DP
                        currentOnChange(clampCoverRadius(stepped))
                        true
                    }
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (largeFont) Modifier.heightIn(min = 150.dp) else Modifier.fillMaxHeight()),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (largeFont) Modifier.heightIn(min = 150.dp) else Modifier.fillMaxHeight())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Sudut\nsampul",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 18.4.sp,
                            letterSpacing = 0.em,
                        ),
                        color = colors.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = buildAnnotatedString {
                            append(radiusDp.toString())
                            withStyle(SpanStyle(fontSize = 18.sp, color = colors.onSurfaceVariant)) {
                                append("dp")
                            }
                        },
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = TextUnit.Unspecified,
                            letterSpacing = (-0.04).em,
                        ),
                        color = colors.onSurface,
                        maxLines = 1,
                    )
                }
                Box(
                    modifier = Modifier.width(150.dp).height(150.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = 30.dp, y = 24.dp)
                            .size(150.dp)
                            .clip(RoundedCornerShape(animatedRadius))
                            .background(previewColor),
                    )
                }
            }
            // Tombol −/+ 48 dp tak kasat mata di sisi kiri dan kanan agar kartu dapat diatur tanpa gestur geser.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .clickable(
                        enabled = enabled && radiusDp > MIN_COVER_RADIUS_DP,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = { onRadiusChange(stepCoverRadiusBy(radiusDp, -1)) },
                    )
                    .semantics {
                        contentDescription = "Kurangi sudut sampul"
                        stateDescription = "$radiusDp dp"
                    },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(48.dp)
                    .clickable(
                        enabled = enabled && radiusDp < MAX_COVER_RADIUS_DP,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = { onRadiusChange(stepCoverRadiusBy(radiusDp, 1)) },
                    )
                    .semantics {
                        contentDescription = "Tambah sudut sampul"
                        stateDescription = "$radiusDp dp"
                    },
            )
        }
    }
}

@Composable
private fun PlayerAndEffectsCard(
    playerBackdropFromCover: Boolean,
    glass: Boolean,
    animation: AnimationSpeed,
    systemReduceMotion: Boolean,
    enabled: Boolean,
    onPlayerBackdropChange: (Boolean) -> Unit,
    onGlassChange: (Boolean) -> Unit,
    onSelectAnimation: (AnimationSpeed) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val effectiveSpeed = if (systemReduceMotion) AnimationSpeed.Off else animation
    val chipsEnabled = enabled && !systemReduceMotion
    SenadaCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(1.dp).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MonoLabel("Pemutar & efek", Modifier.semantics { heading() })
            AppearanceSwitchRow(
                title = "Latar pemutar dari sampul",
                subtitle = "Warna sampul diblur di layar Sedang diputar",
                checked = playerBackdropFromCover,
                enabled = enabled,
                onCheckedChange = onPlayerBackdropChange,
            )
            AppearanceSwitchRow(
                title = "Efek kaca",
                subtitle = "Matikan bila HP terasa berat",
                checked = glass,
                enabled = enabled,
                onCheckedChange = onGlassChange,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Kecepatan animasi",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp,
                        letterSpacing = 0.em,
                    ),
                    color = colors.onSurface,
                )
                if (systemReduceMotion) {
                    Text(
                        text = "Diatur oleh sistem",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp,
                            letterSpacing = 0.em,
                        ),
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                for (speed in AnimationSpeed.entries) {
                    AnimationSpeedChip(
                        speed = speed,
                        selected = speed == effectiveSpeed,
                        enabled = chipsEnabled,
                        lockedBySystem = systemReduceMotion,
                        onClick = { onSelectAnimation(speed) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimationSpeedChip(
    speed: AnimationSpeed,
    selected: Boolean,
    enabled: Boolean,
    lockedBySystem: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val look = LocalSenadaLook.current
    val background by animateColorAsState(
        targetValue = if (selected) colors.onSurface else colors.surfaceContainerHigh,
        animationSpec = look.tweenOrSnap(ControlTransitionMs),
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.background else colors.onSurface,
        animationSpec = look.tweenOrSnap(ControlTransitionMs),
    )
    val stateLabel = when {
        lockedBySystem && selected -> "Dipilih, diatur oleh sistem"
        lockedBySystem -> "Dikunci oleh sistem"
        selected -> "Dipilih"
        else -> "Tidak dipilih"
    }
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { stateDescription = stateLabel }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .graphicsLayer { alpha = if (lockedBySystem && !selected) 0.45f else 1f }
                .clip(RoundedCornerShape(20.dp))
                .background(background)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = speed.label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp,
                    letterSpacing = 0.em,
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AppearanceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AdaptiveRow(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics { stateDescription = if (checked) "Aktif" else "Nonaktif" }
            .heightIn(min = 48.dp),
        minPrimaryWidth = 160.dp,
        horizontalSpacing = 12.dp,
        verticalSpacing = 8.dp,
        primary = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp,
                        letterSpacing = 0.em,
                    ),
                    color = colors.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp,
                        letterSpacing = 0.em,
                    ),
                    color = colors.onSurfaceVariant,
                )
            }
        },
        trailing = {
            SenadaSwitch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
    )
}
