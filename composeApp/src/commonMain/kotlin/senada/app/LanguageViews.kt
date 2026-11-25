package senada.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import senada.app.theme.MonoLabel
import senada.resources.Res
import senada.resources.language_active_label
import senada.resources.language_english_hint
import senada.resources.language_indonesian_hint
import senada.resources.language_section_description
import senada.resources.language_section_title
import senada.resources.state_active

@Composable
internal fun LanguageSection(preferences: AppearancePreferences) {
    val settings = preferences.settings ?: Appearance()
    val current = settings.language
    val enabled = preferences.settings != null
    val colors = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                appString(Res.string.language_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                appString(Res.string.language_section_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            MonoLabel(appString(Res.string.language_active_label, current.nativeName))
        }

        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (option in AppLanguage.entries) {
                val isSelected = option == current
                val activeLabel = appString(Res.string.state_active)
                val hint = when (option) {
                    AppLanguage.English -> appString(Res.string.language_english_hint)
                    AppLanguage.Indonesian -> appString(Res.string.language_indonesian_hint)
                }
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (isSelected) colors.surfaceContainerHighest else colors.surfaceContainer,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) colors.primary else colors.outlineVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(enabled = enabled, role = Role.RadioButton) {
                            preferences.selectLanguage(option)
                        }
                        .semantics(mergeDescendants = true) {
                            selected = isSelected
                            if (isSelected) stateDescription = activeLabel
                        },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = isSelected, onClick = null, enabled = enabled)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                option.nativeName,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onSurface,
                            )
                            Text(
                                hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
