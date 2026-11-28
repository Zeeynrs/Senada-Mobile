// Portions adapted from Mochy Music (https://github.com/realidkroo/Mochy-Music, commit 3071023), GPL-3.0.
package senada.lyrics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import senada.app.AppLanguage
import senada.app.LocalAppLanguage
import senada.app.SenadaCard
import senada.app.appString
import senada.app.minTouchTarget
import senada.resources.Res
import senada.resources.action_reset
import senada.resources.lyrics_offset_current_a11y
import senada.resources.lyrics_offset_earlier_a11y
import senada.resources.lyrics_offset_hint
import senada.resources.lyrics_offset_later_a11y
import senada.resources.lyrics_offset_reset_a11y
import senada.resources.lyrics_offset_sheet_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsOffsetSheet(
    offsetMs: Int,
    onAdjust: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val language = LocalAppLanguage.current
    val clamped = clampLyricsOffsetMs(offsetMs)
    val formatted = formatLyricsOffset(clamped, language)
    val currentA11y = appString(Res.string.lyrics_offset_current_a11y, formatted)
    val resetA11y = appString(Res.string.lyrics_offset_reset_a11y)

    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = appString(Res.string.lyrics_offset_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            SenadaCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 36.sp,
                            lineHeight = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.02).em,
                        ),
                        color = if (clamped == 0) colors.onSurface else colors.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.semantics {
                            contentDescription = currentA11y
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                    Text(
                        text = appString(Res.string.lyrics_offset_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            OffsetStepButtons(
                offsetMs = clamped,
                language = language,
                onAdjust = onAdjust,
            )
            OutlinedButton(
                onClick = onReset,
                enabled = clamped != 0,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, colors.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minTouchTarget()
                    .semantics { contentDescription = resetA11y },
            ) {
                Text(
                    text = appString(Res.string.action_reset),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun OffsetStepButtons(
    offsetMs: Int,
    language: AppLanguage,
    onAdjust: (Int) -> Unit,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = largeFont || maxWidth < 310.dp
        if (compact) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (stepMs in LYRICS_OFFSET_STEPS_MS.take(2)) {
                        OffsetStepButton(stepMs, offsetMs, language, Modifier.weight(1f), onAdjust)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (stepMs in LYRICS_OFFSET_STEPS_MS.drop(2)) {
                        OffsetStepButton(stepMs, offsetMs, language, Modifier.weight(1f), onAdjust)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (stepMs in LYRICS_OFFSET_STEPS_MS) {
                    OffsetStepButton(stepMs, offsetMs, language, Modifier.weight(1f), onAdjust)
                }
            }
        }
    }
}

@Composable
private fun OffsetStepButton(
    stepMs: Int,
    currentOffsetMs: Int,
    language: AppLanguage,
    modifier: Modifier,
    onAdjust: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val enabled = if (stepMs < 0) currentOffsetMs > MIN_LYRICS_OFFSET_MS else currentOffsetMs < MAX_LYRICS_OFFSET_MS
    val label = formatLyricsOffset(stepMs, language)
    val magnitude = formatLyricsOffsetMagnitude(stepMs, language)
    val a11yLabel = appString(
        if (stepMs > 0) Res.string.lyrics_offset_earlier_a11y else Res.string.lyrics_offset_later_a11y,
        magnitude,
    )
    Button(
        onClick = { onAdjust(stepMs) },
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surfaceContainerHighest,
            contentColor = colors.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = a11yLabel },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
