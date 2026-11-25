package senada.app.audiolab

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import senada.app.LocalAppLanguage
import senada.app.SenadaCard
import senada.app.SenadaSwitch
import senada.app.appString
import senada.app.theme.LocalSenadaLook
import senada.audio.AudioEffects
import senada.audio.AudioEffectsController
import senada.audio.ReverbRoom
import senada.audio.ReverbSettings
import senada.audio.BassBoostSettings
import senada.resources.Res
import senada.resources.audiolab_fx_multiline
import senada.resources.audiolab_speed_pitch_title
import senada.resources.fx_bass_boost
import senada.resources.fx_bass_boost_desc
import senada.resources.fx_bass_strength
import senada.resources.fx_bass_strength_a11y
import senada.resources.fx_effect_off
import senada.resources.fx_loudness
import senada.resources.fx_loudness_desc
import senada.resources.fx_pitch_lock
import senada.resources.fx_pitch_lock_desc
import senada.resources.fx_reverb
import senada.resources.fx_reverb_desc
import senada.resources.fx_reverb_mix
import senada.resources.fx_reverb_mix_a11y
import senada.resources.fx_reverb_room
import senada.resources.fx_reverb_room_a11y
import senada.resources.state_not_selected
import senada.resources.state_selected

private const val EffectDimmedAlpha = 0.4f
private const val EffectDimAnimationMs = 150

@Composable
private fun rememberEffectControlAlpha(effectEnabled: Boolean): Float {
    val alpha by animateFloatAsState(
        targetValue = if (effectEnabled) 1f else EffectDimmedAlpha,
        animationSpec = LocalSenadaLook.current.tweenOrSnap(EffectDimAnimationMs),
    )
    return alpha
}

/** Indeks item kartu "Kecepatan & nada" di LazyColumn halaman Efek suara. */
internal fun soundFxSpeedItemIndex(effectsAvailable: Boolean): Int = if (effectsAvailable) 4 else 5

internal fun LazyListScope.soundFxScreen(
    effects: AudioEffects,
    audioEffects: AudioEffectsController?,
    loudnessSettings: AudioEffectsController?,
    previousLabel: String,
    onBack: () -> Unit,
) {
    val controlsEnabled = audioEffects != null

    item(key = "soundfx-topbar") {
        AudioLabTopBar(previousLabel = previousLabel, onBack = onBack)
    }
    item(key = "soundfx-title") {
        Text(
            text = appString(Res.string.audiolab_fx_multiline),
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
    item(key = "soundfx-loudness") {
        LoudnessCard(
            checked = effects.loudnessMatch,
            enabled = loudnessSettings != null,
            onToggle = { on -> loudnessSettings?.update { it.copy(loudnessMatch = on) } },
        )
    }
}

@Composable
private fun LoudnessCard(checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    SenadaCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val loudnessTitle = appString(Res.string.fx_loudness)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = loudnessTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = colors.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = appString(Res.string.fx_loudness_desc),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.onSurfaceVariant,
                )
            }
            SenadaSwitch(
                checked = checked,
                onCheckedChange = onToggle,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = loudnessTitle },
            )
        }
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
    val controlsAlpha = rememberEffectControlAlpha(reverb.enabled)

    SenadaCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val reverbTitle = appString(Res.string.fx_reverb)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reverbTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = colors.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = appString(Res.string.fx_reverb_desc),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.onSurfaceVariant,
                    )
                }
                SenadaSwitch(
                    checked = reverb.enabled,
                    onCheckedChange = onToggle,
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = reverbTitle },
                )
            }

            val language = LocalAppLanguage.current
            val offStateLabel = appString(Res.string.fx_effect_off)
            val selectedLabel = appString(Res.string.state_selected)
            val notSelectedLabel = appString(Res.string.state_not_selected)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    // AudioLabChip sudah meredupkan dirinya sendiri saat enabled=false.
                    .graphicsLayer { alpha = if (enabled) controlsAlpha else 1f },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                for (room in ReverbRoom.entries) {
                    val selected = reverb.room == room
                    AudioLabChip(
                        label = room.label(language),
                        selected = selected,
                        enabled = enabled,
                        onClick = { onRoomSelect(room) },
                        modifier = if (reverb.enabled) {
                            Modifier
                        } else {
                            Modifier.semantics {
                                stateDescription =
                                    "${if (selected) selectedLabel else notSelectedLabel}, $offStateLabel"
                            }
                        },
                    )
                }
            }

            PercentSliderBlock(
                label = appString(Res.string.fx_reverb_mix),
                value = reverb.mix,
                enabled = enabled,
                effectEnabled = reverb.enabled,
                controlsAlpha = controlsAlpha,
                accessibilityLabel = appString(Res.string.fx_reverb_mix_a11y),
                onValueChange = onMixChange,
            )
            PercentSliderBlock(
                label = appString(Res.string.fx_reverb_room),
                value = reverb.size,
                enabled = enabled,
                effectEnabled = reverb.enabled,
                controlsAlpha = controlsAlpha,
                accessibilityLabel = appString(Res.string.fx_reverb_room_a11y),
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
    val controlsAlpha = rememberEffectControlAlpha(bassBoost.enabled)

    SenadaCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val bassTitle = appString(Res.string.fx_bass_boost)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bassTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = colors.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = appString(Res.string.fx_bass_boost_desc),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.onSurfaceVariant,
                    )
                }
                SenadaSwitch(
                    checked = bassBoost.enabled,
                    onCheckedChange = onToggle,
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = bassTitle },
                )
            }
            PercentSliderBlock(
                label = appString(Res.string.fx_bass_strength),
                value = bassBoost.strength,
                enabled = enabled,
                effectEnabled = bassBoost.enabled,
                controlsAlpha = controlsAlpha,
                accessibilityLabel = appString(Res.string.fx_bass_strength_a11y),
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
            val language = LocalAppLanguage.current
            Text(
                text = appString(Res.string.audiolab_speed_pitch_title),
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
                        label = formatSpeed(choice, language),
                        selected = abs(speed - choice) < 0.01f,
                        enabled = enabled,
                        onClick = { onSpeedSelect(choice) },
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            val pitchLockTitle = appString(Res.string.fx_pitch_lock)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pitchLockTitle,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = colors.onSurface,
                    )
                    Text(
                        text = appString(Res.string.fx_pitch_lock_desc),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.onSurfaceVariant,
                    )
                }
                SenadaSwitch(
                    checked = pitchLock,
                    onCheckedChange = onPitchLockToggle,
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = pitchLockTitle },
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
    effectEnabled: Boolean,
    controlsAlpha: Float,
    accessibilityLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val percentText = formatPercent(value)
    val offStateLabel = appString(Res.string.fx_effect_off)
    val sliderStateDescription = if (effectEnabled) percentText else "$percentText, $offStateLabel"

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = controlsAlpha },
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
            stateDescription = sliderStateDescription,
            onValueChange = onValueChange,
            modifier = Modifier.graphicsLayer { alpha = if (enabled) controlsAlpha else 1f },
        )
    }
}
