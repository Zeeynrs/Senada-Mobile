package senada.app.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import senada.app.contrastRatio

private val WarmGlow = Color(0xFFB4521E)
private val WarmShadowGlow = Color(0xFF6E1E12)
private val WarmBase = Color(0xFF1C0F08)
private val PlainBase = Color(0xFF0B0C0E)
internal val PlayerWhite = Color(0xFFF2F2F3)

// Sampel radial dari lingkaran yang dikonvolusi dengan Gaussian. Radius sampling
// mencapai tiga deviasi standar setelah tepi lingkaran, lalu menjadi transparan.
private val UpperGlow = listOf(.985f, .976f, .946f, .879f, .761f, .596f, .408f, .239f, .117f, .047f, .016f, .004f, 0f)
private val LowerGlow = listOf(.802f, .777f, .703f, .590f, .453f, .316f, .197f, .109f, .053f, .023f, .008f, .003f, 0f)

// Blur kaca 16 dp menambah varians pada Gaussian latar. Hasilnya dapat digambar
// langsung di dalam tombol tanpa menangkap ulang seluruh layar sebagai tekstur.
private val UpperGlassGlow = listOf(.982f, .974f, .941f, .871f, .751f, .585f, .399f, .233f, .114f, .046f, .015f, .004f, 0f)
private val LowerGlassGlow = listOf(.794f, .768f, .694f, .581f, .446f, .310f, .193f, .107f, .052f, .022f, .008f, .003f, 0f)

internal val LocalPlayerBackdrop = staticCompositionLocalOf<PlayerBackdrop> { error("Latar pemutar belum dipasang") }

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

internal class PlayerBackdrop(accent: Color?, private val fromCover: Boolean, private val scale: Float) {
    val glow = playerGlow(accent)
    private val lower = if (accent == null) WarmShadowGlow else glow.dim(.60f)
    private val base = when {
        !fromCover -> PlainBase
        accent == null -> WarmBase
        else -> coverBase(glow)
    }
    private var viewport by mutableStateOf(Size.Zero)
    private var position by mutableStateOf(Offset.Zero)

    fun backgroundModifier(): Modifier = Modifier.onGloballyPositioned {
        viewport = Size(it.size.width.toFloat(), it.size.height.toFloat())
        position = it.positionInRoot()
    }.paint(glass = false)

    fun glassModifier(origin: () -> Offset): Modifier = Modifier.paint(glass = true, origin = origin)

    private fun Modifier.paint(glass: Boolean, origin: () -> Offset = { Offset.Zero }): Modifier = drawWithCache {
        val unit = (density * scale).coerceAtLeast(1f / 390f)
        val frame = if (glass) viewport else size
        val offset = if (glass) origin() - position else Offset.Zero
        val upperBrush = Brush.radialGradient(
            colors = (if (glass) UpperGlassGlow else UpperGlow).map { glow.copy(alpha = it * .75f) },
            center = Offset(140f * unit, 180f * unit) - offset,
            radius = (if (glass) 534.23f else 530f) * unit,
        )
        val lowerBrush = Brush.radialGradient(
            colors = (if (glass) LowerGlassGlow else LowerGlow).map { lower.copy(alpha = it * .60f) },
            center = Offset(frame.width - 30f * unit, frame.height - 244f * unit) - offset,
            radius = (if (glass) 483.82f else 480f) * unit,
        )
        val shade = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to Color.Black.copy(alpha = if (fromCover) .55f else .15f),
            startY = frame.height * .40f - offset.y,
            endY = frame.height - offset.y,
        )
        onDrawBehind {
            drawRect(base)
            if (fromCover) {
                drawRect(upperBrush)
                drawRect(lowerBrush)
            }
            if (frame.height > 0f) drawRect(shade)
        }
    }
}

@Composable
internal fun NowPlayingBackdrop(backdrop: PlayerBackdrop) {
    Box(Modifier.fillMaxSize().then(backdrop.backgroundModifier()))
}

@Composable
internal fun Modifier.playerGlassBackdrop(): Modifier {
    val backdrop = LocalPlayerBackdrop.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { origin = it.positionInRoot() }
        .then(backdrop.glassModifier { origin })
}
