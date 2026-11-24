package senada.app

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import senada.resources.Res
import senada.resources.senada_mark

/** Simbol resmi Senada dari aset logo Senada Desktop. */
@Composable
internal fun SenadaMark(modifier: Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Icon(
        painter = painterResource(Res.drawable.senada_mark),
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}
