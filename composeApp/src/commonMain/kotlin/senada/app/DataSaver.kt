package senada.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
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
    Column {
        Text("Data", style = MaterialTheme.typography.h6)
        Row(
            Modifier.fillMaxWidth().toggleable(enabled, enabled = settings != null, role = Role.Switch,
                onValueChange = preferences::saveMobileData).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Hemat data seluler", Modifier.weight(1f))
            Switch(enabled, onCheckedChange = null, enabled = settings != null)
        }
        if (dataSaverActive(enabled, metered)) {
            Text("Hemat data aktif di jaringan ini.", style = MaterialTheme.typography.body2)
        }
    }
}
