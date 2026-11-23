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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import senada.app.theme.SenadaPalette

private val avatarColors = listOf(Color(0xFF8FE0C9), Color(0xFFAFA6DE), Color(0xFFC9A86A))

internal fun profileInitials(name: String): String {
    val trimmed = name.trimStart()
    val length = if (trimmed.firstOrNull()?.isHighSurrogate() == true && trimmed.getOrNull(1)?.isLowSurrogate() == true) 2 else 1
    return trimmed.take(length).uppercase()
}

@Composable
internal fun ProfileAvatar(
    name: String,
    selected: Boolean = false,
    size: Dp = 28.dp,
    borderAlpha: Float = 0.2f,
    borderColor: Color? = null,
    modifier: Modifier = Modifier,
    borderWidth: Dp = 2.dp,
    photoModel: Any? = null,
    fallbackBackground: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    val initial = profileInitials(name)
    var photoLoaded by remember(photoModel) { mutableStateOf(false) }
    val background = fallbackBackground ?: if (initial.isEmpty()) colors.surfaceContainerHigh
        else avatarColors[name.hashCode().mod(avatarColors.size)]
    val border = borderColor ?: if (selected) colors.onSurface else colors.onSurface.copy(alpha = borderAlpha)
    val textSize = when {
        size >= 84.dp -> 32.sp
        size >= 52.dp -> 20.sp
        size >= 34.dp -> 15.sp
        else -> 13.sp
    }
    Box(
        modifier = modifier.size(size).clip(CircleShape)
            .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, border, CircleShape) else Modifier)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (!photoLoaded && initial.isEmpty()) {
            Icon(
                SenadaIcons.Person, null, Modifier.size((size * 0.6f).coerceAtLeast(18.dp)),
                tint = if (selected) colors.onSurface else colors.onSurfaceVariant,
            )
        } else if (!photoLoaded) {
            Text(
                initial,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = textSize, lineHeight = textSize * 1.2f, fontWeight = FontWeight.ExtraBold,
                ),
                color = SenadaPalette.LightText,
                maxLines = 1,
            )
        }
        if (photoModel != null) {
            AsyncImage(
                photoModel, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop,
                onSuccess = { photoLoaded = true },
                onError = { photoLoaded = false },
            )
        }
    }
}
