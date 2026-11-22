package senada.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
internal fun AppearanceSection(preferences: AppearancePreferences) {
    val settings = preferences.settings ?: Appearance()
    val ready = preferences.settings != null
    SettingsCard("Tampilan") {
        Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                val selected = mode == settings.mode
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected, enabled = ready, role = Role.RadioButton) {
                            preferences.selectMode(mode)
                        }
                        .semantics { stateDescription = if (selected) "Dipilih" else "Tidak dipilih" }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected, onClick = null, enabled = ready)
                    Text(
                        mode.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AccentPicker(settings.accent, ready, preferences::selectAccent)
    }
}
