package senada.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import senada.app.theme.LocalSenadaLook
import senada.app.theme.SenadaPalette

private val SwitchTrackWidth = 56.dp
private val SwitchTrackHeight = 32.dp
private val SwitchTrackRadius = 16.dp
private val SwitchThumbSize = 26.dp
private val SwitchThumbPadding = 3.dp
private val SwitchThumbTravel = SwitchTrackWidth - SwitchThumbSize - SwitchThumbPadding * 2
private const val SwitchAnimationDurationMs = 150

internal data class SenadaSwitchColors(
    val track: Color,
    val thumb: Color,
)

/**
 * Warna trek dan bulatan sakelar Senada: mengikuti [ColorScheme.primary] saat menyala dengan bulatan
 * berkontras tinggi (#0B0C0E di tema gelap, #FFFFFF di aksen gelap tema terang), dan pasangan warna
 * netral saat mati agar tetap kontras di tema gelap maupun terang.
 */
internal fun senadaSwitchColors(colors: ColorScheme, checked: Boolean): SenadaSwitchColors {
    if (checked) {
        val track = colors.primary
        val thumb = if (
            contrastRatio(track, SenadaPalette.DarkBackground) >=
            contrastRatio(track, SenadaPalette.LightSurface)
        ) {
            SenadaPalette.DarkBackground
        } else {
            SenadaPalette.LightSurface
        }
        return SenadaSwitchColors(track = track, thumb = thumb)
    }
    return SenadaSwitchColors(
        track = colors.outline,
        thumb = colors.onSurfaceVariant,
    )
}

/**
 * Sakelar bersama untuk layar Pengaturan dan Profil. Bila [onCheckedChange] tidak null, komponen
 * menangani area sentuh minimal 48 dp, semantik `Role.Switch`, serta status nyala/mati sendiri.
 */
@Composable
internal fun SenadaSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val look = LocalSenadaLook.current
    val targetColors = senadaSwitchColors(MaterialTheme.colorScheme, checked)
    val trackColor by animateColorAsState(
        targetValue = targetColors.track,
        animationSpec = look.tweenOrSnap(SwitchAnimationDurationMs),
    )
    val thumbColor by animateColorAsState(
        targetValue = targetColors.thumb,
        animationSpec = look.tweenOrSnap(SwitchAnimationDurationMs),
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) SwitchThumbTravel else 0.dp,
        animationSpec = look.tweenOrSnap(SwitchAnimationDurationMs),
    )
    val interactionSource = remember { MutableInteractionSource() }

    val interactiveModifier = if (onCheckedChange != null) {
        Modifier
            .minTouchTarget()
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics { stateDescription = if (checked) "Aktif" else "Nonaktif" }
    } else {
        Modifier
    }

    Box(
        modifier = modifier.then(interactiveModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(SwitchTrackWidth, SwitchTrackHeight)
                .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
                .clip(RoundedCornerShape(SwitchTrackRadius))
                .background(trackColor)
                .padding(SwitchThumbPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                    .size(SwitchThumbSize)
                    .background(thumbColor, CircleShape),
            )
        }
    }
}
