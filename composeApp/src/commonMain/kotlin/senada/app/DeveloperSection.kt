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

/** Uji audio untuk pengembang: memutar sampel yang ikut di aplikasi atau dari HTTPS, di luar antrean. */
@Composable
internal fun DeveloperSection(playback: PlaybackController, audioEffects: AudioEffectsController?) {
    SettingsCard("Pengembang") {
        Text(
            "Uji audio: ${DemoCatalog.tracks.first().title}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val buttonColors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            FilledTonalButton(
                onClick = { playback.play(sampleAudioSelection(SampleAudioSource.Local)) },
                shape = CircleShape,
                colors = buttonColors,
                modifier = Modifier.minTouchTarget(),
            ) { Text("Putar lokal") }
            FilledTonalButton(
                onClick = { playback.play(sampleAudioSelection(SampleAudioSource.Https)) },
                shape = CircleShape,
                colors = buttonColors,
                modifier = Modifier.minTouchTarget(),
            ) { Text("Putar HTTPS") }
            if (audioEffects != null) {
                val current by audioEffects.effects.collectAsState()
                val active = current.copy(customPresets = emptyList()) == DeveloperTestEffects
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
                        stateDescription = if (active) "Aktif" else "Mati"
                    },
                ) { Text("Uji efek") }
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
