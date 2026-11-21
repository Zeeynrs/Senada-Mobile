package senada.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import senada.catalog.DemoCatalog
import senada.playback.PlaybackController
import senada.playback.SampleAudioSource

/** Uji audio untuk pengembang: memutar sampel yang ikut di aplikasi atau dari HTTPS, di luar antrean. */
@Composable
internal fun DeveloperSection(playback: PlaybackController) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pengembang", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
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
            Button(
                onClick = { playback.play(sampleAudioSelection(SampleAudioSource.Local)) },
                modifier = Modifier.minTouchTarget(),
            ) { Text("Putar lokal") }
            Button(
                onClick = { playback.play(sampleAudioSelection(SampleAudioSource.Https)) },
                modifier = Modifier.minTouchTarget(),
            ) { Text("Putar HTTPS") }
        }
    }
}
