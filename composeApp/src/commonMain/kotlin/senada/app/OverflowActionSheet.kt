package senada.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import senada.app.tabs.CatalogArtwork
import senada.app.theme.LocalSenadaLook
import senada.resources.Res
import senada.resources.a11y_menu_for

/** Panel aksi untuk menu titik tiga. Tutup panel sebelum membuka dialog atau berpindah halaman. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverflowActionSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    artwork: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.(select: (() -> Unit) -> Unit) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selecting by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = { if (!selecting) onDismiss() },
        sheetState = state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = colors.surfaceContainerHigh.copy(alpha = if (LocalSenadaLook.current.glass) 0.96f else 1f),
        contentColor = colors.onSurface,
        scrimColor = colors.scrim.copy(alpha = 0.52f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            artwork?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val menuForTitle = appString(Res.string.a11y_menu_for, title)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics {
                        heading()
                        contentDescription = menuForTitle
                    },
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            content { action ->
                if (!selecting) {
                    selecting = true
                    scope.launch {
                        try {
                            state.hide()
                            onDismiss()
                            action()
                        } finally {
                            selecting = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OverflowActionArtwork(cover: CollectionCover) {
    CatalogArtwork(
        artworkUrl = cover.artworkUrl,
        shape = RoundedCornerShape(LocalSenadaLook.current.listCoverRadius),
        fallbackIcon = cover.fallbackIcon,
        imageSize = 144,
        modifier = Modifier.size(48.dp),
        background = cover.containerColor ?: MaterialTheme.colorScheme.surfaceContainer,
    )
}

@Composable
internal fun OverflowActionItem(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    )
}
