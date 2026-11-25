package senada.app.nowplaying

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import senada.app.FullViewTheme
import senada.app.LocalAppLanguage
import senada.app.SenadaCard
import senada.app.appString
import senada.resources.Res
import senada.resources.appearance_full_view_theme_title

@Composable
internal fun FullViewThemeCard(selected: FullViewTheme, enabled: Boolean, onSelect: (FullViewTheme) -> Unit) {
    SenadaCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                appString(Res.string.appearance_full_view_theme_title), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            FullViewThemeOptions(selected, enabled, onSelect)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullViewThemeSheet(selected: FullViewTheme, onSelect: (FullViewTheme) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                appString(Res.string.appearance_full_view_theme_title), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            FullViewThemeOptions(selected, enabled = true) { theme ->
                onSelect(theme)
                onDismiss()
            }
        }
    }
}

@Composable
private fun FullViewThemeOptions(selected: FullViewTheme, enabled: Boolean, onSelect: (FullViewTheme) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val language = LocalAppLanguage.current
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (theme in FullViewTheme.entries) {
            val active = theme == selected
            Surface(
                modifier = Modifier.weight(1f).clip(shape).selectable(active, enabled, Role.RadioButton) { onSelect(theme) },
                shape = shape,
                color = if (active) colors.primaryContainer else colors.surfaceContainerHigh,
                contentColor = if (active) colors.onPrimaryContainer else colors.onSurface,
                border = BorderStroke(1.dp, if (active) colors.primary else colors.outlineVariant),
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    FullViewThemePreview(theme)
                    Text(
                        theme.label(language),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FullViewThemePreview(theme: FullViewTheme) {
    Canvas(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(8.dp))) {
        val w = size.width
        val h = size.height
        val art = Color(0xFFD78F65)
        drawRect(Brush.linearGradient(listOf(Color(0xFF6B4239), Color(0xFF1C181C))))
        if (theme == FullViewTheme.Vinyl) {
            val center = Offset(w * .37f, h * .28f)
            drawCircle(Color(0xFF161419), h * .59f, center)
            drawCircle(Color.White.copy(alpha = .16f), h * .51f, center, style = Stroke(1.dp.toPx()))
            drawCircle(art, h * .20f, center)
            drawLine(PlayerWhite, Offset(w * .77f, h * .10f), Offset(w * .54f, h * .47f), 2.dp.toPx(), StrokeCap.Round)
            drawLine(PlayerWhite.copy(alpha = .8f), Offset(w * .25f, h * .78f), Offset(w * .64f, h * .78f), 2.dp.toPx(), StrokeCap.Round)
        } else {
            val cover = h * .52f
            drawRoundRect(art, Offset((w - cover) / 2f, h * .10f), Size(cover, cover), CornerRadius(4.dp.toPx()))
            drawLine(PlayerWhite.copy(alpha = .8f), Offset(w * .29f, h * .75f), Offset(w * .71f, h * .75f), 2.dp.toPx(), StrokeCap.Round)
        }
        drawCircle(PlayerWhite.copy(alpha = .70f), h * .045f, Offset(w * .5f, h * .89f))
    }
}
