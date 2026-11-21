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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import senada.catalog.DemoCatalog
import senada.playback.PlaybackController
import senada.playback.SampleAudioSource

/** Uji audio untuk pengembang: memutar sampel yang ikut di aplikasi atau dari HTTPS, di luar antrean. */
@Composable
internal fun DeveloperSection(playback: PlaybackController) {
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
        }
    }
}
