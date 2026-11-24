package senada.app.audiolab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import senada.app.AdaptiveRow
import senada.app.SenadaCard
import senada.app.SenadaSwitch
import senada.app.minTouchTarget
import senada.app.theme.MonoLabel
import senada.app.theme.SenadaPalette
import senada.app.theme.SenadaType
import senada.audio.AudioEffects
import senada.audio.AudioEffectsController
import senada.audio.EqPreset

internal fun LazyListScope.equalizerScreen(
    effects: AudioEffects,
    audioEffects: AudioEffectsController?,
    previousLabel: String,
    onBack: () -> Unit,
) {
    val controlsEnabled = audioEffects != null
    val eq = effects.equalizer

    item(key = "eq-topbar") {
        AudioLabTopBar(previousLabel = previousLabel, onBack = onBack)
    }
    item(key = "eq-title") {
        AdaptiveRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            minPrimaryWidth = 140.dp,
            primary = {
                Text(
                    text = "Equalizer",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.04).em,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
            },
            trailing = {
                SenadaSwitch(
                    checked = eq.enabled,
                    onCheckedChange = { enabled ->
                        audioEffects?.update { it.copy(equalizer = it.equalizer.copy(enabled = enabled)) }
                    },
                    enabled = controlsEnabled,
                    modifier = Modifier.semantics { contentDescription = "Equalizer" },
                )
            },
        )
    }
    if (!controlsEnabled) {
        item(key = "eq-unavailable") { AudioLabUnavailableNotice() }
    }
    item(key = "eq-presets") {
        EqualizerPresetChips(
            effects = effects,
            enabled = controlsEnabled,
            onSelect = { preset -> audioEffects?.update { selectPreset(it, preset) } },
            onDeleteCustom = { presetId -> audioEffects?.update { deleteCustomPreset(it, presetId) } },
        )
    }
    item(key = "eq-bands") {
        EqualizerBandCard(
            gains = eq.gains,
            presetLabel = effects.activePresetName(),
            eqEnabled = eq.enabled,
            controlsEnabled = controlsEnabled,
            onBandChange = { index, gain ->
                audioEffects?.update { updateBandGain(it, index, gain) }
            },
        )
    }
    item(key = "eq-preamp") {
        EqualizerPreampCard(
            preamp = eq.preamp,
            enabled = controlsEnabled,
            onPreampChange = { value ->
                val snapped = snapHalfDb(value, -12f, 6f)
                audioEffects?.update { it.copy(equalizer = it.equalizer.copy(preamp = snapped)) }
            },
        )
    }
    item(key = "eq-actions") {
        EqualizerActionsRow(
            enabled = controlsEnabled,
            onReset = { audioEffects?.update(::resetEqualizer) },
            onSavePreset = { name -> audioEffects?.update { saveCustomPreset(it, name) } },
        )
    }
}

@Composable
private fun EqualizerPresetChips(
    effects: AudioEffects,
    enabled: Boolean,
    onSelect: (EqPreset) -> Unit,
    onDeleteCustom: (String) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<EqPreset?>(null) }
    val activePreset = effects.activePreset()
    val showCustomChip = activePreset == null

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        for (preset in EqPreset.BuiltIns) {
            AudioLabChip(
                label = preset.name,
                selected = activePreset?.id == preset.id,
                enabled = enabled,
                onClick = { onSelect(preset) },
            )
        }
        for (preset in effects.customPresets) {
            AudioLabChip(
                label = preset.name,
                selected = activePreset?.id == preset.id,
                enabled = enabled,
                onClick = { onSelect(preset) },
                onLongClick = { pendingDelete = preset },
                onLongClickLabel = "Hapus preset ${preset.name}",
            )
        }
        if (showCustomChip) {
            AudioLabChip(
                label = EqPreset.CustomName,
                selected = true,
                enabled = enabled,
                onClick = {},
            )
        }
    }

    val target = pendingDelete ?: return
    AlertDialog(
        onDismissRequest = { pendingDelete = null },
        title = { Text("Hapus preset?", modifier = Modifier.semantics { heading() }) },
        text = { Text("Preset \"${target.name}\" akan dihapus.") },
        confirmButton = {
            TextButton(
                onClick = {
                    onDeleteCustom(target.id)
                    pendingDelete = null
                },
                modifier = Modifier.minTouchTarget(),
            ) { Text("Hapus") }
        },
        dismissButton = {
            TextButton(
                onClick = { pendingDelete = null },
                modifier = Modifier.minTouchTarget(),
            ) { Text("Batal") }
        },
    )
}

@Composable
private fun EqualizerBandCard(
    gains: List<Float>,
    presetLabel: String,
    eqEnabled: Boolean,
    controlsEnabled: Boolean,
    onBandChange: (Int, Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val hzStyle = SenadaType.monoLabel.copy(fontSize = 10.sp, letterSpacing = 0.em)

    SenadaCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonoLabel("+12 dB")
                MonoLabel(presetLabel)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 2.dp),
            ) {
                // Garis tengah 0 dB di posisi 110 dp.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .height(1.dp)
                        .background(colors.outline),
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    EqFrequencyLabels.indices.forEach { index ->
                        val gain = gains.getOrElse(index) { 0f }
                        VerticalBandSlider(
                            bandIndex = index,
                            gainDb = gain,
                            eqEnabled = eqEnabled,
                            controlsEnabled = controlsEnabled,
                            onGainChange = { next -> onBandChange(index, next) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for (hz in EqFrequencyLabels) {
                    Text(
                        text = hz,
                        style = hzStyle,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.weight(1f).clearAndSetSemantics {},
                    )
                }
            }

            Box(modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp)) {
                MonoLabel("−12 dB")
            }
        }
    }
}

@Composable
private fun VerticalBandSlider(
    bandIndex: Int,
    gainDb: Float,
    eqEnabled: Boolean,
    controlsEnabled: Boolean,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background == SenadaPalette.DarkBackground
    val trackColor = colors.outline
    val fillColor = if (eqEnabled) colors.primary else colors.primary.copy(alpha = 0.45f)
    val thumbColor = if (dark) SenadaPalette.DarkText else Color.White
    val density = LocalDensity.current
    val thumbRadiusPx = with(density) { 11.dp.toPx() }
    val clamped = gainDb.coerceIn(-12f, 12f)
    val latestOnChange by rememberUpdatedState(onGainChange)

    fun updateFromY(y: Float, height: Float) {
        val centerY = height / 2f
        val usableHalf = (centerY - thumbRadiusPx).coerceAtLeast(1f)
        val normalized = ((centerY - y) / usableHalf).coerceIn(-1f, 1f)
        latestOnChange(snapHalfDb(normalized * 12f, -12f, 12f))
    }

    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (controlsEnabled) 1f else 0.38f }
            .semantics {
                contentDescription = bandContentDescription(bandIndex, clamped)
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = clamped,
                    range = -12f..12f,
                    steps = 47,
                )
                if (controlsEnabled) {
                    setProgress { target ->
                        latestOnChange(snapHalfDb(target, -12f, 12f))
                        true
                    }
                } else {
                    disabled()
                }
            }
            .then(
                if (controlsEnabled) {
                    Modifier
                        .pointerInput(bandIndex) {
                            detectTapGestures { offset -> updateFromY(offset.y, size.height.toFloat()) }
                        }
                        .pointerInput(bandIndex) {
                            detectVerticalDragGestures(
                                onDragStart = { offset -> updateFromY(offset.y, size.height.toFloat()) },
                                onVerticalDrag = { change, _ ->
                                    change.consume()
                                    updateFromY(change.position.y, size.height.toFloat())
                                },
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackWidth = 4.dp.toPx()
            val trackRadius = 2.dp.toPx()
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val usableHalf = (centerY - thumbRadiusPx).coerceAtLeast(0f)
            val thumbY = centerY - (clamped / 12f) * usableHalf

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(centerX - trackWidth / 2f, 0f),
                size = Size(trackWidth, size.height),
                cornerRadius = CornerRadius(trackRadius, trackRadius),
            )
            val fillTop = minOf(thumbY, centerY)
            val fillHeight = kotlin.math.abs(centerY - thumbY)
            if (fillHeight > 0.5f) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(centerX - trackWidth / 2f, fillTop),
                    size = Size(trackWidth, fillHeight),
                    cornerRadius = CornerRadius(trackRadius, trackRadius),
                )
            }
            // Bayangan bulatan 22 dp (0 2px 6px rgba(0,0,0,.5)).
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = if (dark) 0.50f else 0.25f), Color.Transparent),
                    center = Offset(centerX, thumbY + 2.dp.toPx()),
                    radius = thumbRadiusPx + 5.dp.toPx(),
                ),
                radius = thumbRadiusPx + 5.dp.toPx(),
                center = Offset(centerX, thumbY + 2.dp.toPx()),
            )
            drawCircle(
                color = thumbColor,
                radius = thumbRadiusPx,
                center = Offset(centerX, thumbY),
            )
        }
    }
}

@Composable
private fun EqualizerPreampCard(
    preamp: Float,
    enabled: Boolean,
    onPreampChange: (Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val valueText = "${formatDb(preamp)} dB"

    SenadaCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Preamp",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colors.onSurface,
                )
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colors.onSurfaceVariant,
                )
            }
            AudioLabHorizontalSlider(
                value = preamp,
                valueRange = -12f..6f,
                step = 0.5f,
                enabled = enabled,
                contentDescription = "Preamp",
                stateDescription = valueText,
                onValueChange = onPreampChange,
            )
            Text(
                text = "Turunkan sedikit bila suara pecah setelah band dinaikkan.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EqualizerActionsRow(
    enabled: Boolean,
    onReset: () -> Unit,
    onSavePreset: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    var saveDialogOpen by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
                .clip(shape)
                .border(BorderStroke(1.dp, colors.outline), shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onReset)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Atur ulang",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
                .clip(shape)
                .background(colors.onSurface)
                .clickable(enabled = enabled, role = Role.Button) {
                    draftName = ""
                    saveDialogOpen = true
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Simpan preset",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = colors.background,
                textAlign = TextAlign.Center,
            )
        }
    }

    if (saveDialogOpen) {
        val canSave = draftName.trim().isNotEmpty()
        AlertDialog(
            onDismissRequest = { saveDialogOpen = false },
            title = { Text("Simpan preset", modifier = Modifier.semantics { heading() }) },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = { Text("Nama preset") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = canSave,
                    onClick = {
                        onSavePreset(draftName)
                        saveDialogOpen = false
                    },
                    modifier = Modifier.minTouchTarget(),
                ) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(
                    onClick = { saveDialogOpen = false },
                    modifier = Modifier.minTouchTarget(),
                ) { Text("Batal") }
            },
        )
    }
}
