package senada.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import senada.app.theme.LocalSenadaLook

/** Sumber blur untuk bilah kaca; diisi oleh [AppShell] dari daftar isi tab di belakang bilah. */
internal val LocalGlassState = staticCompositionLocalOf<HazeState?> { null }

/** Kekuatan blur dan transparansi kaca. Lapisan warna cukup pekat agar teks tetap terbaca di atas sampul. */
private val GlassBlur = 18.dp
private const val GlassTintAlpha = 0.72f
private const val GlassFallbackAlpha = 0.94f
private const val GlassBorderAlpha = 0.08f
internal expect val glassBlurInputScale: Float

/**
 * Permukaan kaca buram untuk mini player dan navigasi: isi di belakangnya diburamkan lalu diberi lapisan
 * warna permukaan yang tembus pandang. Bila efek kaca dimatikan, memakai permukaan padat `#1D1F23`
 * (atau padanan tema terang) tanpa blur Haze.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun GlassSurface(shape: Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlassState.current
    val glassEnabled = LocalSenadaLook.current.glass
    val tint = colors.surfaceContainerHigh
    val fill = if (glassEnabled && glass != null) {
        Modifier.hazeEffect(
            state = glass,
            style = HazeStyle(
                backgroundColor = colors.background,
                tint = HazeTint(tint.copy(alpha = GlassTintAlpha)),
                blurRadius = GlassBlur,
                noiseFactor = 0f,
                fallbackTint = HazeTint(tint.copy(alpha = GlassFallbackAlpha)),
            ),
        ) {
            inputScale = HazeInputScale.Fixed(glassBlurInputScale)
        }
    } else {
        Modifier.background(tint)
    }
    val borderColor = if (glassEnabled) Color.White.copy(alpha = GlassBorderAlpha) else colors.outline
    Box(
        modifier
            .clip(shape)
            .then(fill)
            .border(1.dp, borderColor, shape),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) { content() }
    }
}
