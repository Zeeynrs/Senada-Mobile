package senada.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.flow.StateFlow

/** Satu pemantau untuk layar dan pemutar latar belakang. Status awal dianggap tidak berbayar. */
internal expect fun meteredNetwork(context: PlatformContext): StateFlow<Boolean>

internal fun dataSaverActive(enabled: Boolean, metered: Boolean) = enabled && metered

@Composable
internal fun DataSection(preferences: AppearancePreferences) {
    val context = LocalPlatformContext.current
    val metered by remember(context) { meteredNetwork(context) }.collectAsState()
    val settings = preferences.settings
    val enabled = settings?.saveMobileData == true
    SettingsCard("Data") {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(
                    value = enabled,
                    enabled = settings != null,
                    role = Role.Switch,
                    onValueChange = preferences::saveMobileData,
                )
                .semantics { stateDescription = if (enabled) "Aktif" else "Nonaktif" }
                .heightIn(min = 48.dp)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Hemat data seluler",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(enabled, onCheckedChange = null, enabled = settings != null)
        }
        if (dataSaverActive(enabled, metered)) {
            Text(
                "Hemat data aktif di jaringan ini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
