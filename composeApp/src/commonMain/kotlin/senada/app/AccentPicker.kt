package senada.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalDensity
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
    val gap = 4.dp
    val labelSize = with(LocalDensity.current) { MaterialTheme.typography.labelMedium.fontSize.toDp() }
    val minCellWidth = maxOf(68.dp, labelSize * 4 + 8.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        val columns = ((maxWidth + gap) / (minCellWidth + gap)).toInt().coerceIn(1, 5)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (choices in AccentChoice.entries.chunked(columns)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (choice in choices) {
                        AccentSwatch(
                            choice = choice,
                            color = choice.source(cover) ?: SenadaPalette.AccentDark,
                            selected = selected == choice,
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(choice) },
                        )
                    }
                    repeat(columns - choices.size) { Spacer(Modifier.weight(1f)) }
                }
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
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var ring = Modifier.size(34.dp)
    if (selected) ring = ring.border(2.dp, colors.onSurface, CircleShape)

    Column(
        modifier = modifier.minTouchTarget()
            .clip(MaterialTheme.shapes.small)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = "Warna aksen ${choice.label}"
                stateDescription = if (selected) "Dipilih" else "Tidak dipilih"
            }
            .padding(4.dp),
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
