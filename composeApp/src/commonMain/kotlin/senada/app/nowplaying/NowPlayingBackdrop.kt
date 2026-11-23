package senada.app.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import senada.app.contrastRatio

private val WarmGlow = Color(0xFFB4521E)
private val WarmShadowGlow = Color(0xFF6E1E12)
private val WarmBase = Color(0xFF1C0F08)
private val PlainBase = Color(0xFF0B0C0E)
internal val PlayerWhite = Color(0xFFF2F2F3)

// Radial samples of a circular disk convolved with a Gaussian. The last stop is
// three standard deviations beyond the disk, where it becomes transparent.
private val UpperGlow = listOf(.985f, .976f, .946f, .879f, .761f, .596f, .408f, .239f, .117f, .047f, .016f, .004f, 0f)
private val LowerGlow = listOf(.802f, .777f, .703f, .590f, .453f, .316f, .197f, .109f, .053f, .023f, .008f, .003f, 0f)

internal fun playerGlow(accent: Color?): Color {
    if (accent == null) return WarmGlow
    val peak = maxOf(accent.red, accent.green, accent.blue)
    var high = if (peak > WarmGlow.red) WarmGlow.red / peak else 1f
    var low = 0f
    if (sourceLabelContrast(accent.dim(high)) >= 4.7f) return accent.dim(high)
    repeat(8) {
        val middle = (low + high) / 2f
        if (sourceLabelContrast(accent.dim(middle)) >= 4.7f) low = middle else high = middle
    }
    return accent.dim(low)
}

internal fun sourceLabelContrast(glow: Color): Float {
    val brightest = glow.copy(alpha = .75f).compositeOver(coverBase(glow))
    return contrastRatio(PlayerWhite.copy(alpha = .85f).compositeOver(brightest), brightest)
}

private fun coverBase(glow: Color) = glow.dim(.13f, blackLevel = 4f / 255f)

private fun Color.dim(factor: Float, blackLevel: Float = 0f): Color =
    Color(red * factor + blackLevel, green * factor + blackLevel, blue * factor + blackLevel)

@Composable
internal fun NowPlayingBackdrop(accent: Color?, fromCover: Boolean, scale: Float, modifier: Modifier = Modifier) {
    val glow = remember(accent) { playerGlow(accent) }
    val base = when {
        !fromCover -> PlainBase
        accent == null -> WarmBase
        else -> coverBase(glow)
    }
    val lower = if (accent == null) WarmShadowGlow else glow.dim(.60f)
    Box(
        modifier.fillMaxSize().drawWithCache {
            val unit = density * scale
            val upperBrush = Brush.radialGradient(
                colors = UpperGlow.map { glow.copy(alpha = it * .75f) },
                center = Offset(140f * unit, 180f * unit),
                radius = (260f + 3 * 90f) * unit,
            )
            val lowerBrush = Brush.radialGradient(
                colors = LowerGlow.map { lower.copy(alpha = it * .60f) },
                center = Offset(size.width - 30f * unit, size.height - 244f * unit),
                radius = (180f + 3 * 100f) * unit,
            )
            val shade = Brush.verticalGradient(
                .40f to Color.Transparent,
                1f to Color.Black.copy(alpha = if (fromCover) .55f else .15f),
            )
            onDrawBehind {
                drawRect(base)
                if (fromCover) {
                    drawRect(upperBrush)
                    drawRect(lowerBrush)
                }
                drawRect(shade)
            }
        },
    )
}
