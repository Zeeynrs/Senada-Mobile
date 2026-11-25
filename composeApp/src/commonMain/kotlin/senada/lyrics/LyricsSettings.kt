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
import senada.app.LocalAppLanguage
import senada.app.SenadaCard
import senada.app.SenadaFilterPill
import senada.app.appString
import senada.app.theme.MonoLabel
import senada.resources.Res
import senada.resources.lyrics_alignment_title
import senada.resources.lyrics_font_title
import senada.resources.lyrics_highlight_title
import senada.resources.lyrics_preview_heading
import senada.resources.lyrics_sample_1
import senada.resources.lyrics_sample_2
import senada.resources.lyrics_sample_3
import senada.resources.lyrics_settings_sheet_title
import senada.resources.lyrics_size_title

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
            val language = LocalAppLanguage.current
            MonoLabel(appString(Res.string.lyrics_preview_heading), Modifier.semantics { heading() })
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF16161A),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LyricsLineText(appString(Res.string.lyrics_sample_1), appearance, LyricLineState.Past, baseSize = 18f)
                    LyricsLineText(
                        appString(Res.string.lyrics_sample_2), appearance, LyricLineState.Active,
                        baseSize = 18f, progress = .55f,
                    )
                    LyricsLineText(appString(Res.string.lyrics_sample_3), appearance, LyricLineState.Upcoming, baseSize = 18f)
                }
            }
            LyricsOptions(appString(Res.string.lyrics_size_title), LyricsSize.entries, appearance.size, { it.label(language) }, enabled) { size ->
                preferences.updateLyricsAppearance { it.copy(size = size) }
            }
            LyricsOptions(appString(Res.string.lyrics_font_title), LyricsFont.entries, appearance.font, { it.label(language) }, enabled) { font ->
                preferences.updateLyricsAppearance { it.copy(font = font) }
            }
            LyricsOptions(appString(Res.string.lyrics_alignment_title), LyricsAlignment.entries, appearance.alignment, { it.label(language) }, enabled) { alignment ->
                preferences.updateLyricsAppearance { it.copy(alignment = alignment) }
            }
            LyricsOptions(appString(Res.string.lyrics_highlight_title), LyricsHighlight.entries, appearance.highlight, { it.label(language) }, enabled) { highlight ->
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
                appString(Res.string.lyrics_settings_sheet_title),
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
