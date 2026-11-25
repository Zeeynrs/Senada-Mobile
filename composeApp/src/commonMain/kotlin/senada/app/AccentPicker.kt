package senada.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import senada.app.theme.AccentChoice
import senada.app.theme.SenadaPalette
import senada.resources.Res
import senada.resources.a11y_accent_color
import senada.resources.state_not_selected
import senada.resources.state_selected

/** Pilihan warna aksen tetap yang ditampilkan di deret bulatan; FollowCover memakai sakelar tersendiri. */
internal val FixedAccentChoices: List<AccentChoice> =
    AccentChoice.entries.filter { it != AccentChoice.FollowCover }

/**
 * Deret bulatan 44 dp untuk memilih warna aksen tetap. Bulatan terpilih bergaris 3 dp #F2F2F3
 * (atau onSurface pada tema terang), dengan target sentuh 48 dp dan jarak visual 12 dp.
 */
@Composable
internal fun AccentSwatchRow(
    selected: AccentChoice,
    enabled: Boolean,
    onSelect: (AccentChoice) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val darkTheme = colors.background.luminance() < 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        // Tiap bulatan 44 dp dibungkus target sentuh 48 dp (+2 dp tiap sisi): 2 + 8 + 2 = 12 dp jarak visual.
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (choice in FixedAccentChoices) {
            val isSelected = selected == choice
            val choiceDescription = appString(Res.string.a11y_accent_color, choice.label(LocalAppLanguage.current))
            val selectedLabel = appString(Res.string.state_selected)
            val notSelectedLabel = appString(Res.string.state_not_selected)
            val swatchColor = if (choice == AccentChoice.Senada) {
                if (darkTheme) SenadaPalette.AccentDark else SenadaPalette.AccentLight
            } else {
                choice.source(null) ?: SenadaPalette.AccentDark
            }
            val borderModifier = if (isSelected) {
                Modifier.border(3.dp, colors.onSurface, CircleShape)
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(choice) },
                    )
                    .semantics {
                        contentDescription = choiceDescription
                        stateDescription = if (isSelected) selectedLabel else notSelectedLabel
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                        .then(borderModifier),
                )
            }
        }
    }
}
