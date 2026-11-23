package senada.app.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import senada.app.contrastRatio

internal const val BACKDROP_CACHE_SHORT_SIDE_PX = 270f

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

internal fun backdropCacheSize(frame: Size, maxShortSidePx: Float = BACKDROP_CACHE_SHORT_SIDE_PX): IntSize {
    if (frame.width <= 0f || frame.height <= 0f) return IntSize.Zero
    val shortSide = minOf(frame.width, frame.height)
    val downsample = (shortSide / maxShortSidePx).coerceAtLeast(1f)
    return IntSize(
        (frame.width / downsample).roundToInt().coerceAtLeast(1),
        (frame.height / downsample).roundToInt().coerceAtLeast(1),
    )
}

@Stable
internal class PlayerBackdrop(accent: Color?, private val fromCover: Boolean, private val scale: Float) {
    val glow = playerGlow(accent)
    private val lower = if (accent == null) WarmShadowGlow else glow.dim(.60f)
    private val base = when {
        !fromCover -> PlainBase
        accent == null -> WarmBase
        else -> coverBase(glow)
    }
    private var viewport by mutableStateOf(Size.Zero)
    private var coordinates: LayoutCoordinates? = null
    private var cachedBackdrop: CachedBitmap? = null
    private var cachedGlass: CachedBitmap? = null

    fun offsetOf(child: LayoutCoordinates): Offset? {
        val host = coordinates?.takeIf { it.isAttached } ?: return null
        if (!child.isAttached) return null
        return host.localPositionOf(child, Offset.Zero)
    }

    fun backgroundModifier(): Modifier = Modifier.onGloballyPositioned { coords ->
        coordinates = coords
        val nextSize = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
        if (viewport != nextSize) viewport = nextSize
    }.drawWithCache {
        val bitmap = bitmapFor(glass = false, size, density, fontScale, layoutDirection)
        val dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
        onDrawBehind {
            if (bitmap != null && dstSize.width > 0 && dstSize.height > 0) {
                drawImage(image = bitmap, dstSize = dstSize, filterQuality = FilterQuality.Low)
            } else {
                drawRect(base)
            }
        }
    }

    fun glassModifier(origin: () -> Offset): Modifier = Modifier.drawWithCache {
        val frame = viewport
        val bitmap = bitmapFor(glass = true, frame, density, fontScale, layoutDirection)
        val dstSize = IntSize(frame.width.roundToInt(), frame.height.roundToInt())
        onDrawBehind {
            if (bitmap == null || dstSize.width <= 0 || dstSize.height <= 0) {
                drawRect(base)
                return@onDrawBehind
            }
            val offset = origin()
            clipRect {
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset((-offset.x).roundToInt(), (-offset.y).roundToInt()),
                    dstSize = dstSize,
                    filterQuality = FilterQuality.Low,
                )
            }
        }
    }

    // Latar gradasi halus di-raster sekali ke bitmap kecil agar tidak menjalankan ulang
    // dua shader radial 13-stop sepenuh layar pada setiap frame posisi atau geser panel.
    private fun bitmapFor(
        glass: Boolean,
        frame: Size,
        density: Float,
        fontScale: Float,
        layoutDirection: LayoutDirection,
    ): ImageBitmap? {
        val widthPx = frame.width.roundToInt()
        val heightPx = frame.height.roundToInt()
        if (widthPx <= 0 || heightPx <= 0) return null
        val cached = if (glass) cachedGlass else cachedBackdrop
        if (cached != null && cached.widthPx == widthPx && cached.heightPx == heightPx && cached.density == density) {
            return cached.bitmap
        }
        val target = backdropCacheSize(frame)
        if (target.width <= 0 || target.height <= 0) return null
        val bitmap = ImageBitmap(target.width, target.height)
        val downsample = frame.width / target.width.toFloat()
        val scaledDensity = Density(density / downsample, fontScale)
        val drawSize = Size(target.width.toFloat(), target.height.toFloat())
        val unit = (scaledDensity.density * scale).coerceAtLeast(1f / 390f)
        val upperBrush = if (fromCover) {
            Brush.radialGradient(
                colors = (if (glass) UpperGlassGlow else UpperGlow).map { glow.copy(alpha = it * .75f) },
                center = Offset(140f * unit, 180f * unit),
                radius = (if (glass) 534.23f else 530f) * unit,
            )
        } else {
            null
        }
        val lowerBrush = if (fromCover) {
            Brush.radialGradient(
                colors = (if (glass) LowerGlassGlow else LowerGlow).map { lower.copy(alpha = it * .60f) },
                center = Offset(drawSize.width - 30f * unit, drawSize.height - 244f * unit),
                radius = (if (glass) 483.82f else 480f) * unit,
            )
        } else {
            null
        }
        val shade = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to Color.Black.copy(alpha = if (fromCover) .55f else .15f),
            startY = drawSize.height * .40f,
            endY = drawSize.height,
        )
        CanvasDrawScope().draw(scaledDensity, layoutDirection, Canvas(bitmap), drawSize) {
            drawRect(base)
            if (upperBrush != null && lowerBrush != null) {
                drawRect(upperBrush)
                drawRect(lowerBrush)
            }
            drawRect(shade)
        }
        val entry = CachedBitmap(widthPx, heightPx, density, bitmap)
        if (glass) cachedGlass = entry else cachedBackdrop = entry
        return bitmap
    }

    private class CachedBitmap(val widthPx: Int, val heightPx: Int, val density: Float, val bitmap: ImageBitmap)
}

@Composable
internal fun NowPlayingBackdrop(backdrop: PlayerBackdrop) {
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer()
            .then(remember(backdrop) { backdrop.backgroundModifier() }),
    )
}

@Composable
internal fun Modifier.playerGlassBackdrop(): Modifier {
    val backdrop = LocalPlayerBackdrop.current
    var origin by remember(backdrop) { mutableStateOf<Offset?>(null) }
    val coordsRef = remember(backdrop) { arrayOfNulls<LayoutCoordinates>(1) }
    return onGloballyPositioned { coords ->
        coordsRef[0] = coords
        backdrop.offsetOf(coords)?.let { origin = it }
    }.then(
        remember(backdrop) {
            backdrop.glassModifier { origin ?: coordsRef[0]?.let(backdrop::offsetOf) ?: Offset.Zero }
        },
    )
}
