package senada.app.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import senada.app.LocalAppLanguage
import senada.app.theme.LocalSenadaLook

/** Pil navigasi kaca yang mengambang di bawah layar; tujuan aktif ditandai pil monokrom. */
@Composable
internal fun FloatingNavBar(
    current: AppDestination,
    onSelect: (AppDestination) -> Unit,
    profileName: String = "",
    profilePhoto: Any? = null,
) {
    val look = LocalSenadaLook.current
    val language = LocalAppLanguage.current
    GlassSurface(shape = CircleShape, modifier = Modifier.fillMaxWidth().height(64.dp)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (destination in AppDestination.entries) {
                val selected = destination == current
                val label = destination.label(language)
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(destination) },
                        )
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    val colors = MaterialTheme.colorScheme
                    val tint by animateColorAsState(
                        targetValue = if (selected) colors.onSurface else colors.onSurfaceVariant,
                        animationSpec = look.tweenOrSnap(SHELL_NAV_PILL_MS),
                    )
                    val pillBackground by animateColorAsState(
                        targetValue = if (selected) colors.onSurface.copy(alpha = 0.12f) else Color.Transparent,
                        animationSpec = look.tweenOrSnap(SHELL_NAV_PILL_MS),
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 44.dp)
                            .clip(CircleShape)
                            .background(pillBackground)
                            .indication(interactionSource, LocalIndication.current),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (destination == AppDestination.Profile) {
                            ProfileAvatar(profileName, selected, size = 32.dp, photoModel = profilePhoto)
                        } else {
                            val icon = when (destination) {
                                AppDestination.Home -> if (selected) SenadaIcons.HomeFilled else SenadaIcons.Home
                                AppDestination.Search -> SenadaIcons.Search
                                else -> SenadaIcons.NavLibrary
                            }
                            Icon(icon, null, Modifier.size(26.dp), tint = tint)
                        }
                    }
                }
            }
        }
    }
}
