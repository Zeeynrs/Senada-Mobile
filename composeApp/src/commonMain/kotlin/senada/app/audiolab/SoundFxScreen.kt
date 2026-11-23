package senada.app.audiolab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import senada.app.SenadaCard
import senada.app.SenadaSwitch
import senada.audio.AudioEffects
import senada.audio.AudioEffectsController
import senada.audio.ReverbRoom
import senada.audio.ReverbSettings
import senada.audio.BassBoostSettings

/** Indeks item kartu "Kecepatan & nada" di LazyColumn halaman Efek suara. */
internal fun soundFxSpeedItemIndex(effectsAvailable: Boolean): Int = if (effectsAvailable) 4 else 5

internal fun LazyListScope.soundFxScreen(
    effects: AudioEffects,
    audioEffects: AudioEffectsController?,
    previousLabel: String,
    onBack: () -> Unit,
) {
    val controlsEnabled = audioEffects != null

    item(key = "soundfx-topbar") {
        AudioLabTopBar(previousLabel = previousLabel, onBack = onBack)
    }
    item(key = "soundfx-title") {
        Text(
            text = "Efek\nsuara",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 46.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.05).em,
                lineHeight = (46f * 0.98f).sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 6.dp)
                .semantics { heading() },
        )
    }
    if (!controlsEnabled) {
        item(key = "soundfx-unavailable") { AudioLabUnavailableNotice() }
    }
    item(key = "soundfx-reverb") {
        ReverbCard(
            reverb = effects.reverb,
            enabled = controlsEnabled,
            onToggle = { on -> audioEffects?.update { it.copy(reverb = it.reverb.copy(enabled = on)) } },
            onRoomSelect = { room -> audioEffects?.update { it.copy(reverb = it.reverb.copy(room = room)) } },
            onMixChange = { mix -> audioEffects?.update { it.copy(reverb = it.reverb.copy(mix = snapPercent(mix))) } },
            onSizeChange = { size -> audioEffects?.update { it.copy(reverb = it.reverb.copy(size = snapPercent(size))) } },
        )
    }
    item(key = "soundfx-bass") {
        BassBoostCard(
            bassBoost = effects.bassBoost,
            enabled = controlsEnabled,
            onToggle = { on -> audioEffects?.update { it.copy(bassBoost = it.bassBoost.copy(enabled = on)) } },
            onStrengthChange = { strength ->
                audioEffects?.update { it.copy(bassBoost = it.bassBoost.copy(strength = snapPercent(strength))) }
            },
        )
    }
    item(key = "soundfx-speed") {
        SpeedAndPitchCard(
            speed = effects.speed,
            pitchLock = effects.pitchLock,
            enabled = controlsEnabled,
            onSpeedSelect = { speed -> audioEffects?.update { it.copy(speed = speed) } },
            onPitchLockToggle = { locked -> audioEffects?.update { it.copy(pitchLock = locked) } },
        )
    }
}

@Composable
private fun ReverbCard(
    reverb: ReverbSettings,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onRoomSelect: (ReverbRoom) -> Unit,
    onMixChange: (Float) -> Unit,
    onSizeChange: (Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    SenadaCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reverb",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = colors.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "Memberi kesan ruang pada lagu",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.onSurfaceVariant,
                    )
                }
                SenadaSwitch(
                    checked = reverb.enabled,
                    onCheckedChange = onToggle,
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "Reverb" },
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                for (room in ReverbRoom.entries) {
                    AudioLabChip(
                        label = room.name,
                        selected = reverb.room == room,
                        enabled = enabled,
                        onClick = { onRoomSelect(room) },
                    )
                }
            }

            PercentSliderBlock(
                label = "Campuran",
                value = reverb.mix,
                enabled = enabled,
                accessibilityLabel = "Campuran reverb",
                onValueChange = onMixChange,
            )
            PercentSliderBlock(
                label = "Ukuran ruang",
                value = reverb.size,
                enabled = enabled,
                accessibilityLabel = "Ukuran ruang reverb",
                onValueChange = onSizeChange,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun BassBoostCard(
    bassBoost: BassBoostSettings,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onStrengthChange: (Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    SenadaCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bass boost",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = colors.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "Menebalkan nada rendah",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.onSurfaceVariant,
                    )
                }
                SenadaSwitch(
                    checked = bassBoost.enabled,
                    onCheckedChange = onToggle,
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "Bass boost" },
                )
            }
            PercentSliderBlock(
                label = "Kekuatan",
                value = bassBoost.strength,
                enabled = enabled,
                accessibilityLabel = "Kekuatan bass boost",
                onValueChange = onStrengthChange,
            )
        }
    }
}

@Composable
private fun SpeedAndPitchCard(
    speed: Float,
    pitchLock: Boolean,
    enabled: Boolean,
    onSpeedSelect: (Float) -> Unit,
    onPitchLockToggle: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    SenadaCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Kecepatan & nada",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                for (choice in SpeedChoices) {
                    AudioLabChip(
                        label = formatSpeed(choice),
                        selected = abs(speed - choice) < 0.01f,
                        enabled = enabled,
                        onClick = { onSpeedSelect(choice) },
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kunci nada",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = colors.onSurface,
                    )
                    Text(
                        text = "Mati: nada ikut naik-turun seperti piringan hitam",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.onSurfaceVariant,
                    )
                }
                SenadaSwitch(
                    checked = pitchLock,
                    onCheckedChange = onPitchLockToggle,
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "Kunci nada" },
                )
            }
        }
    }
}

@Composable
private fun PercentSliderBlock(
    label: String,
    value: Float,
    enabled: Boolean,
    accessibilityLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val percentText = formatPercent(value)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.onSurface,
            )
            Text(
                text = percentText,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.onSurfaceVariant,
            )
        }
        AudioLabHorizontalSlider(
            value = value,
            valueRange = 0f..1f,
            step = 0.01f,
            enabled = enabled,
            contentDescription = accessibilityLabel,
            stateDescription = percentText,
            onValueChange = onValueChange,
        )
    }
}
