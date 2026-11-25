package senada.lyrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import senada.app.AppearancePreferences
import senada.app.SenadaCard
import senada.app.SenadaFilterPill
import senada.app.theme.MonoLabel

@Composable
internal fun LyricsSettingsSection(preferences: AppearancePreferences) {
    val settings = preferences.settings
    val appearance = settings?.lyricsAppearance ?: LyricsAppearance()
    val enabled = settings != null
    SenadaCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MonoLabel("Pratinjau", Modifier.semantics { heading() })
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF16161A),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LyricsLineText("Malam bergerak pelan", appearance, LyricLineState.Past, baseSize = 18f)
                    LyricsLineText(
                        "Kita ikuti iramanya", appearance, LyricLineState.Active,
                        baseSize = 18f, progress = .55f,
                    )
                    LyricsLineText("Sampai cahaya datang", appearance, LyricLineState.Upcoming, baseSize = 18f)
                }
            }
            LyricsOptions("Ukuran teks", LyricsSize.entries, appearance.size, LyricsSize::label, enabled) { size ->
                preferences.updateLyricsAppearance { it.copy(size = size) }
            }
            LyricsOptions("Font", LyricsFont.entries, appearance.font, LyricsFont::label, enabled) { font ->
                preferences.updateLyricsAppearance { it.copy(font = font) }
            }
            LyricsOptions("Perataan", LyricsAlignment.entries, appearance.alignment, LyricsAlignment::label, enabled) { alignment ->
                preferences.updateLyricsAppearance { it.copy(alignment = alignment) }
            }
            LyricsOptions("Sorotan baris aktif", LyricsHighlight.entries, appearance.highlight, LyricsHighlight::label, enabled) { highlight ->
                preferences.updateLyricsAppearance { it.copy(highlight = highlight) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsSettingsSheet(preferences: AppearancePreferences, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Tampilan lirik",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            LyricsSettingsSection(preferences)
        }
    }
}

@Composable
private fun <T> LyricsOptions(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { value ->
                SenadaFilterPill(
                    label = label(value),
                    selected = value == selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}
