package senada.app.audiolab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import senada.app.SenadaCard
import senada.app.nowplaying.SleepTimerMenu
import senada.app.theme.MonoLabel
import senada.app.theme.SenadaPalette
import senada.audio.AudioEffects
import senada.playback.PlaybackController
import senada.playback.SleepTimerState

internal fun LazyListScope.audioLabScreen(
    effects: AudioEffects,
    effectsAvailable: Boolean,
    saveMobileData: Boolean,
    sleepTimerState: SleepTimerState,
    canStopAfterTrack: Boolean,
    playback: PlaybackController,
    previousLabel: String,
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSoundFx: (focusSpeed: Boolean) -> Unit,
    onOpenPlayback: () -> Unit,
) {
    item(key = "audiolab-header") {
        AudioLabHeader(previousLabel = previousLabel, onBack = onBack)
    }
    if (!effectsAvailable) {
        item(key = "audiolab-unavailable") { AudioLabUnavailableNotice() }
    }
    item(key = "audiolab-equalizer") {
        AudioLabEqualizerCard(
            status = equalizerStatus(effects),
            onClick = onOpenEqualizer,
        )
    }
    item(key = "audiolab-soundfx") {
        AudioLabSoundFxCard(
            status = soundFxStatus(effects),
            onClick = { onOpenSoundFx(false) },
        )
    }
    item(key = "audiolab-grid") {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AudioLabGridCard(
                title = "Kecepatan & nada",
                status = speedStatus(effects),
                onClick = { onOpenSoundFx(true) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            AudioLabGridCard(
                title = "Kualitas audio",
                status = audioQualityStatus(saveMobileData),
                onClick = onOpenPlayback,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
    item(key = "audiolab-sleep-timer") {
        AudioLabSleepTimerCard(
            state = sleepTimerState,
            canStopAfterTrack = canStopAfterTrack,
            playback = playback,
        )
    }
}

@Composable
private fun AudioLabHeader(previousLabel: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // LazyColumn memakai padding horizontal 12 dp; tombol 48 dp di +2 dp menempatkan lingkaran 44 dp di 16 dp.
        AudioLabBackButton(
            description = "Kembali ke $previousLabel",
            onClick = onBack,
            modifier = Modifier.padding(start = 2.dp),
        )
        Spacer(Modifier.height(18.dp))
        // Judul berada di 20 dp dari tepi kiri (12 + 8), label mono di kanan bawah area judul.
        BottomAlignedAdaptiveRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            primary = {
                Text(
                    text = "Audio\nLab",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 46.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.05).em,
                        lineHeight = (46f * 0.98f).sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
            },
            trailing = {
                MonoLabel("Android · iPhone")
            },
        )
        // Bersama jarak 10 dp antar item LazyColumn, kartu pertama mulai di 252 dp pada skala baku.
        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun AudioLabEqualizerCard(status: CardStatus, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val decorationColor = if (colors.background == SenadaPalette.DarkBackground) {
        SenadaPalette.Decoration
    } else {
        colors.outline
    }
    val circleFill = colors.background

    SenadaCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .clipToBounds()
                .clickable(role = Role.Button, onClick = onClick),
        ) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = 6.dp)
                    .size(130.dp, 110.dp),
            ) {
                val strokePx = 5.dp.toPx()
                val scaleX = size.width / 130f
                val scaleY = size.height / 110f
                val lines = listOf(30f, 65f, 100f)
                for (x in lines) {
                    drawLine(
                        color = decorationColor,
                        start = Offset(x * scaleX, 10f * scaleY),
                        end = Offset(x * scaleX, 110f * scaleY),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round,
                    )
                }
                val knobs = listOf(Offset(30f, 70f), Offset(65f, 38f), Offset(100f, 62f))
                val radius = 13f * minOf(scaleX, scaleY)
                for (knob in knobs) {
                    val center = Offset(knob.x * scaleX, knob.y * scaleY)
                    drawCircle(color = circleFill, radius = radius, center = center)
                    drawCircle(
                        color = decorationColor,
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokePx),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                AudioLabStatusLabel(status)
                Text(
                    text = "Equalizer",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.03).em,
                    ),
                    color = colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AudioLabSoundFxCard(status: CardStatus, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val decorationColor = if (colors.background == SenadaPalette.DarkBackground) {
        SenadaPalette.Decoration
    } else {
        colors.outline
    }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    // Teks dekoratif "FX" berukuran tetap 104 dp agar tidak membesar berlebihan pada skala huruf 200%.
    val fxFontSize = with(density) { 104.dp.toSp() }
    val fxStrokePx = with(density) { 2.dp.toPx() }
    val fxStroke = remember(fxStrokePx) { Stroke(width = fxStrokePx, join = StrokeJoin.Round) }
    val fxStyle = MaterialTheme.typography.displayLarge.copy(
        fontSize = fxFontSize,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.06).em,
        lineHeight = fxFontSize,
        color = decorationColor,
        drawStyle = fxStroke,
    )
    val measured = remember(textMeasurer, fxStyle) {
        textMeasurer.measure(text = "FX", style = fxStyle)
    }
    val shear = remember { italicShearMatrix() }

    SenadaCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .clipToBounds()
                .clickable(role = Role.Button, onClick = onClick),
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val left = size.width - measured.size.width - 10.dp.toPx()
                val top = 4.dp.toPx()
                val pivotY = measured.size.height / 2f
                withTransform({
                    translate(left = left, top = top + pivotY)
                    transform(shear)
                    translate(left = 0f, top = -pivotY)
                }) {
                    drawText(measured, color = decorationColor, drawStyle = fxStroke)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                AudioLabStatusLabel(status)
                Text(
                    text = "Efek suara",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.03).em,
                    ),
                    color = colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AudioLabGridCard(
    title: String,
    status: CardStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SenadaCard(modifier = modifier.heightIn(min = 112.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .heightIn(min = 112.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            AudioLabStatusLabel(status)
        }
    }
}

@Composable
private fun AudioLabSleepTimerCard(
    state: SleepTimerState,
    canStopAfterTrack: Boolean,
    playback: PlaybackController,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val status = sleepTimerStatus(state)
    val colors = MaterialTheme.colorScheme

    Box(modifier = Modifier.fillMaxWidth()) {
        SenadaCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clickable(role = Role.Button) { menuOpen = true }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Timer tidur",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    AudioLabStatusLabel(status)
                }
                Icon(
                    imageVector = AudioLabMoonIcon,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        SleepTimerMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            state = state,
            canStopAfterTrack = canStopAfterTrack,
            playback = playback,
        )
    }
}

/**
 * Menyusun judul utama dan label mono di kanan bawah area judul; bila skala huruf besar membuat ruang
 * terlalu sempit, label turun ke baris bawah tanpa terpotong.
 */
@Composable
private fun BottomAlignedAdaptiveRow(
    modifier: Modifier = Modifier,
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
        val gap = 12.dp.roundToPx()
        val vGap = 8.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val primaryPlaceable = measurables[0].measure(loose)
        val trailingPlaceable = measurables[1].measure(loose)
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else {
            primaryPlaceable.width + gap + trailingPlaceable.width
        }
        val sideBySide = primaryPlaceable.width + gap + trailingPlaceable.width <= maxWidth
        if (sideBySide) {
            val height = maxOf(primaryPlaceable.height, trailingPlaceable.height)
            layout(maxWidth, height) {
                primaryPlaceable.placeRelative(0, 0)
                trailingPlaceable.placeRelative(
                    x = maxWidth - trailingPlaceable.width,
                    y = height - trailingPlaceable.height,
                )
            }
        } else {
            val wrappedPrimary = measurables[0].measure(loose.copy(maxWidth = maxWidth))
            val wrappedTrailing = measurables[1].measure(loose.copy(maxWidth = maxWidth))
            val height = wrappedPrimary.height + vGap + wrappedTrailing.height
            layout(maxWidth, height) {
                wrappedPrimary.placeRelative(0, 0)
                wrappedTrailing.placeRelative(0, wrappedPrimary.height + vGap)
            }
        }
    }
}
