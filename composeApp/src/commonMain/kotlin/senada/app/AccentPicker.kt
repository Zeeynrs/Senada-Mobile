package senada.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import senada.app.shell.SenadaIcons
import senada.app.theme.AccentChoice
import senada.app.theme.SenadaPalette

@Composable
internal fun AccentPicker(selected: AccentChoice, enabled: Boolean, onSelect: (AccentChoice) -> Unit) {
    val cover = LocalArtworkAccent.current
    Text(
        "Warna aksen",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { heading() },
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (choice in AccentChoice.entries) {
            AccentSwatch(choice, choice.source(cover) ?: SenadaPalette.AccentDark, selected == choice, enabled) {
                onSelect(choice)
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    choice: AccentChoice,
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var ring = Modifier.size(34.dp)
    if (selected) ring = ring.border(2.dp, colors.onSurface, CircleShape)

    Column(
        modifier = Modifier.widthIn(min = 68.dp)
            .minTouchTarget()
            .clip(MaterialTheme.shapes.small)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = "Warna aksen ${choice.label}"
                stateDescription = if (selected) "Dipilih" else "Tidak dipilih"
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Box(ring, contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(24.dp).background(color, CircleShape)
                    .border(1.dp, colors.onSurfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (choice == AccentChoice.FollowCover) {
                    Icon(SenadaIcons.MusicNote, null, Modifier.size(16.dp), tint = textOnAccent(color))
                }
            }
        }
        Text(
            choice.label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
