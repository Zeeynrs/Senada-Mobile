package senada.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import senada.audio.AudioEffects
import senada.audio.AudioEffectsController
import senada.audio.BassBoostSettings
import senada.audio.EqPreset
import senada.audio.EqualizerSettings
import senada.audio.ReverbSettings
import senada.catalog.DemoCatalog
import senada.playback.PlaybackController
import senada.playback.SampleAudioSource
import senada.resources.Res
import senada.resources.developer_audio_sample_label
import senada.resources.developer_play_https
import senada.resources.developer_play_local
import senada.resources.developer_test_effects
import senada.resources.developer_title
import senada.resources.state_active
import senada.resources.state_off

/** Uji audio untuk pengembang: di build debug memutar sampel lokal atau dari HTTPS, di luar antrean. */
@Composable
internal fun DeveloperSection(playback: PlaybackController, audioEffects: AudioEffectsController?) {
    // Sampel audio hanya ada di build debug; build rilis hanya menyisakan uji efek.
    val showSamples = isDebugBuild()
    SettingsCard(appString(Res.string.developer_title)) {
        if (showSamples) {
            Text(
                appString(Res.string.developer_audio_sample_label, DemoCatalog.debugTracks.first().title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val buttonColors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (showSamples) {
                FilledTonalButton(
                    onClick = { playback.play(sampleAudioSelection(SampleAudioSource.Local)) },
                    shape = CircleShape,
                    colors = buttonColors,
                    modifier = Modifier.minTouchTarget(),
                ) { Text(appString(Res.string.developer_play_local)) }
                FilledTonalButton(
                    onClick = { playback.play(sampleAudioSelection(SampleAudioSource.Https)) },
                    shape = CircleShape,
                    colors = buttonColors,
                    modifier = Modifier.minTouchTarget(),
                ) { Text(appString(Res.string.developer_play_https)) }
            }
            if (audioEffects != null) {
                val current by audioEffects.effects.collectAsState()
                val active = current.copy(customPresets = emptyList()) == DeveloperTestEffects
                val stateLabel = appString(if (active) Res.string.state_active else Res.string.state_off)
                FilledTonalButton(
                    onClick = {
                        audioEffects.update {
                            val next = if (active) AudioEffects() else DeveloperTestEffects
                            next.copy(customPresets = it.customPresets)
                        }
                    },
                    shape = CircleShape,
                    colors = buttonColors,
                    modifier = Modifier.minTouchTarget().semantics {
                        stateDescription = stateLabel
                    },
                ) { Text(appString(Res.string.developer_test_effects)) }
            }
        }
    }
}

private val DeveloperTestEffects = AudioEffects(
    equalizer = EqPreset.BuiltIns.first { it.id == "bass" }.let { bass ->
        EqualizerSettings(enabled = true, presetId = bass.id, gains = bass.gains)
    },
    bassBoost = BassBoostSettings(enabled = true),
    reverb = ReverbSettings(enabled = true),
    speed = 1.25f,
)
