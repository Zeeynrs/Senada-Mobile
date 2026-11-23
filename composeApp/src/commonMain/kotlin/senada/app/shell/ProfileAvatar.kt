package senada.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import senada.app.theme.SenadaPalette

private val avatarColors = listOf(Color(0xFFC9A86A), Color(0xFF8FE0C9), Color(0xFFAFA6DE))

internal fun profileInitials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }

/**
 * Avatar bulat profil bersama untuk navbar bawah (28 dp) dan bar atas Beranda (36 dp):
 * menampilkan inisial di atas warna konsisten dari nama, atau ikon orang bila nama kosong.
 */
@Composable
internal fun ProfileAvatar(
    name: String,
    selected: Boolean = false,
    size: Dp = 28.dp,
    borderAlpha: Float = 0.2f,
    borderColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val initials = profileInitials(name)
    val background = if (initials.isEmpty()) {
        colors.surfaceContainerHigh
    } else {
        avatarColors[name.hashCode().mod(avatarColors.size)]
    }
    val resolvedBorder = borderColor ?: when {
        selected -> colors.onSurface
        colors.onSurface == SenadaPalette.DarkText -> Color.White.copy(alpha = borderAlpha)
        else -> colors.onSurface.copy(alpha = borderAlpha)
    }
    val iconSize = (size.value * 0.6f).dp.coerceAtLeast(18.dp)
    val textSize = if (size >= 34.dp) 15.sp else 13.sp
    val lineHeight = if (size >= 34.dp) 18.sp else 16.sp

    Box(
        modifier = modifier
            .size(size)
            .background(background, CircleShape)
            .border(2.dp, resolvedBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (initials.isEmpty()) {
            Icon(
                imageVector = SenadaIcons.Person,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = if (selected) colors.onSurface else colors.onSurfaceVariant,
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = textSize,
                    lineHeight = lineHeight,
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = SenadaPalette.LightText,
            )
        }
    }
}
