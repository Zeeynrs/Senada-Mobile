package senada.app.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Pil navigasi yang mengambang di bawah layar; tujuan aktif ditandai pil berisi aksen. */
@Composable
internal fun FloatingNavBar(current: AppDestination, onSelect: (AppDestination) -> Unit) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().height(64.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (destination in AppDestination.entries) {
                val selected = destination == current
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(destination) })
                        .semantics { contentDescription = destination.label },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = if (selected) 64.dp else 48.dp, height = 44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private val AppDestination.icon: ImageVector
    get() = when (this) {
        AppDestination.Home -> SenadaIcons.Home
        AppDestination.Search -> SenadaIcons.Search
        AppDestination.Library -> SenadaIcons.Library
        AppDestination.Settings -> SenadaIcons.Settings
    }
