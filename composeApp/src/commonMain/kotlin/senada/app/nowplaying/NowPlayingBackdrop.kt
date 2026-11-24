package senada.app.nowplaying

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import senada.app.contrastRatio
import senada.app.theme.LocalSenadaLook
import senada.app.theme.scaledAnimationDurationMillis

internal const val PLAYER_BACKDROP_TRANSITION_MS = 400
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

internal data class PlayerBackdropColors(
    val glow: Color,
    val lower: Color,
    val base: Color,
    val glowStrength: Float,
    val shadeAlpha: Float,
)

internal fun playerBackdropColors(accent: Color?, fromCover: Boolean): PlayerBackdropColors {
    val glow = playerGlow(accent)
    val lower = if (accent == null) WarmShadowGlow else glow.dim(.60f)
    val base = when {
        !fromCover -> PlainBase
        accent == null -> WarmBase
        else -> coverBase(glow)
    }
    return PlayerBackdropColors(
        glow = glow,
        lower = lower,
        base = base,
        glowStrength = if (fromCover) 1f else 0f,
        shadeAlpha = if (fromCover) .55f else .15f,
    )
}

internal fun playerBackdropTransitionDurationMs(animationScale: Float): Int =
    scaledAnimationDurationMillis(PLAYER_BACKDROP_TRANSITION_MS, animationScale)

internal data class BackdropAppearance(
    val colors: PlayerBackdropColors,
    val fromCover: Boolean,
)

internal fun backdropAppearance(colors: PlayerBackdropColors, fromCover: Boolean): BackdropAppearance =
    if (fromCover && colors.glowStrength > 0f) {
        BackdropAppearance(colors = colors, fromCover = true)
    } else {
        // Latar polos tidak menggambar cahaya sampul, sehingga perubahan aksen tidak memicu raster ulang.
        BackdropAppearance(
            colors = colors.copy(glow = Color.Unspecified, lower = Color.Unspecified, glowStrength = 0f),
            fromCover = false,
        )
    }

internal data class BackdropCacheKey(
    val appearance: BackdropAppearance,
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val scale: Float,
    val glass: Boolean = false,
)

internal fun backdropCacheKey(
    appearance: BackdropAppearance,
    frame: Size,
    density: Float,
    scale: Float,
    glass: Boolean = false,
): BackdropCacheKey? {
    val widthPx = frame.width.roundToInt()
    val heightPx = frame.height.roundToInt()
    if (widthPx <= 0 || heightPx <= 0) return null
    return BackdropCacheKey(
        appearance = appearance,
        widthPx = widthPx,
        heightPx = heightPx,
        density = density,
        scale = scale,
        glass = glass,
    )
}

internal data class BackdropLayerBlend(
    val drawPrevious: Boolean,
    val currentAlpha: Float,
)

internal fun backdropLayerBlend(hasPrevious: Boolean, progress: Float): BackdropLayerBlend {
    val clamped = progress.coerceIn(0f, 1f)
    if (!hasPrevious || clamped >= 1f) {
        return BackdropLayerBlend(drawPrevious = false, currentAlpha = 1f)
    }
    return BackdropLayerBlend(drawPrevious = true, currentAlpha = clamped)
}

internal data class BackdropCrossfadeSlot<T>(
    val currentKey: BackdropCacheKey? = null,
    val current: T? = null,
    val previous: T? = null,
) {
    fun resolve(key: BackdropCacheKey, render: () -> T): Pair<BackdropCrossfadeSlot<T>, T> {
        if (current != null && currentKey == key) return this to current
        val rendered = render()
        return copy(currentKey = key, current = rendered) to rendered
    }

    fun beginTransition(progress: Float): BackdropCrossfadeSlot<T> {
        val nextPrevious = when {
            current == null -> previous
            previous == null || progress >= .5f -> current
            else -> previous
        }
        return BackdropCrossfadeSlot(currentKey = null, current = null, previous = nextPrevious)
    }

    fun snap(): BackdropCrossfadeSlot<T> = BackdropCrossfadeSlot()

    fun finishTransition(): BackdropCrossfadeSlot<T> =
        if (previous == null) this else copy(previous = null)
}

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
internal class PlayerBackdrop(
    initialAppearance: BackdropAppearance,
    initialGlow: Color,
    initialScale: Float,
) {
    var appearance by mutableStateOf(initialAppearance)
        private set
    private var targetGlow by mutableStateOf(initialGlow)
    private var previousGlow: Color? = null
    private var scale by mutableFloatStateOf(initialScale)
    private var crossfadeAlpha by mutableFloatStateOf(1f)
    private var viewport by mutableStateOf(Size.Zero)
    private var coordinates: LayoutCoordinates? = null
    private var backdropSlot = BackdropCrossfadeSlot<ImageBitmap>()
    private var glassSlot = BackdropCrossfadeSlot<ImageBitmap>()

    val glow: Color
        get() {
            val prev = previousGlow
            val alpha = crossfadeAlpha
            return if (prev != null && alpha < 1f) lerp(prev, targetGlow, alpha.coerceIn(0f, 1f)) else targetGlow
        }

    fun updateScale(nextScale: Float) {
        if (scale != nextScale) scale = nextScale
    }

    fun updateGlow(nextGlow: Color) {
        if (targetGlow != nextGlow) targetGlow = nextGlow
    }

    fun snapTo(nextAppearance: BackdropAppearance, nextGlow: Color) {
        backdropSlot = backdropSlot.snap()
        glassSlot = glassSlot.snap()
        previousGlow = null
        targetGlow = nextGlow
        appearance = nextAppearance
        crossfadeAlpha = 1f
    }

    fun beginCrossfade(nextAppearance: BackdropAppearance, nextGlow: Color) {
        val progress = crossfadeAlpha
        backdropSlot = backdropSlot.beginTransition(progress)
        glassSlot = glassSlot.beginTransition(progress)
        previousGlow = glow
        targetGlow = nextGlow
        appearance = nextAppearance
        crossfadeAlpha = 0f
    }

    fun updateCrossfadeAlpha(alpha: Float) {
        crossfadeAlpha = alpha.coerceIn(0f, 1f)
    }

    fun finishCrossfade() {
        backdropSlot = backdropSlot.finishTransition()
        glassSlot = glassSlot.finishTransition()
        previousGlow = null
        crossfadeAlpha = 1f
    }

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
        val currentAppearance = appearance
        val currentScale = scale
        val bitmap = bitmapFor(
            glass = false,
            appearance = currentAppearance,
            frame = size,
            density = density,
            fontScale = fontScale,
            layoutDirection = layoutDirection,
            scale = currentScale,
        )
        val dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
        onDrawBehind {
            if (bitmap != null && dstSize.width > 0 && dstSize.height > 0) {
                drawCrossfadedBitmap(
                    current = bitmap,
                    previous = backdropSlot.previous,
                    alpha = crossfadeAlpha,
                    dstOffset = IntOffset.Zero,
                    dstSize = dstSize,
                )
            } else {
                drawRect(currentAppearance.colors.base)
            }
        }
    }

    fun glassModifier(origin: () -> Offset): Modifier = Modifier.drawWithCache {
        val currentAppearance = appearance
        val currentScale = scale
        val frame = viewport
        val bitmap = bitmapFor(
            glass = true,
            appearance = currentAppearance,
            frame = frame,
            density = density,
            fontScale = fontScale,
            layoutDirection = layoutDirection,
            scale = currentScale,
        )
        val dstSize = IntSize(frame.width.roundToInt(), frame.height.roundToInt())
        onDrawBehind {
            if (bitmap == null || dstSize.width <= 0 || dstSize.height <= 0) {
                drawRect(currentAppearance.colors.base)
                return@onDrawBehind
            }
            val offset = origin()
            val dstOffset = IntOffset((-offset.x).roundToInt(), (-offset.y).roundToInt())
            clipRect {
                drawCrossfadedBitmap(
                    current = bitmap,
                    previous = glassSlot.previous,
                    alpha = crossfadeAlpha,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                )
            }
        }
    }

    // Latar gradasi halus di-raster sekali per target ke bitmap kecil agar tidak menjalankan ulang
    // dua shader radial 13-stop sepenuh layar pada setiap frame posisi, geser panel, atau transisi warna.
    private fun bitmapFor(
        glass: Boolean,
        appearance: BackdropAppearance,
        frame: Size,
        density: Float,
        fontScale: Float,
        layoutDirection: LayoutDirection,
        scale: Float,
    ): ImageBitmap? {
        val key = backdropCacheKey(appearance, frame, density, scale, glass = glass) ?: return null
        val target = backdropCacheSize(frame)
        if (target.width <= 0 || target.height <= 0) return null
        val slot = if (glass) glassSlot else backdropSlot
        val (nextSlot, bitmap) = slot.resolve(key) {
            rasterizeBackdropBitmap(key, frame, target, fontScale, layoutDirection)
        }
        if (glass) glassSlot = nextSlot else backdropSlot = nextSlot
        return bitmap
    }

    private fun rasterizeBackdropBitmap(
        key: BackdropCacheKey,
        frame: Size,
        target: IntSize,
        fontScale: Float,
        layoutDirection: LayoutDirection,
    ): ImageBitmap {
        val bitmap = ImageBitmap(target.width, target.height)
        val downsample = frame.width / target.width.toFloat()
        val scaledDensity = Density(key.density / downsample, fontScale)
        val drawSize = Size(target.width.toFloat(), target.height.toFloat())
        val unit = (scaledDensity.density * key.scale).coerceAtLeast(1f / 390f)
        val colors = key.appearance.colors
        val showGlow = key.appearance.fromCover && colors.glowStrength > 0f
        val upperBrush = if (showGlow) {
            Brush.radialGradient(
                colors = (if (key.glass) UpperGlassGlow else UpperGlow).map {
                    colors.glow.copy(alpha = it * .75f * colors.glowStrength)
                },
                center = Offset(140f * unit, 180f * unit),
                radius = (if (key.glass) 534.23f else 530f) * unit,
            )
        } else {
            null
        }
        val lowerBrush = if (showGlow) {
            Brush.radialGradient(
                colors = (if (key.glass) LowerGlassGlow else LowerGlow).map {
                    colors.lower.copy(alpha = it * .60f * colors.glowStrength)
                },
                center = Offset(drawSize.width - 30f * unit, drawSize.height - 244f * unit),
                radius = (if (key.glass) 483.82f else 480f) * unit,
            )
        } else {
            null
        }
        val shade = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to Color.Black.copy(alpha = colors.shadeAlpha),
            startY = drawSize.height * .40f,
            endY = drawSize.height,
        )
        CanvasDrawScope().draw(scaledDensity, layoutDirection, Canvas(bitmap), drawSize) {
            drawRect(colors.base)
            if (upperBrush != null && lowerBrush != null) {
                drawRect(upperBrush)
                drawRect(lowerBrush)
            }
            drawRect(shade)
        }
        return bitmap
    }
}

private fun DrawScope.drawCrossfadedBitmap(
    current: ImageBitmap,
    previous: ImageBitmap?,
    alpha: Float,
    dstOffset: IntOffset,
    dstSize: IntSize,
) {
    val blend = backdropLayerBlend(hasPrevious = previous != null, progress = alpha)
    if (blend.drawPrevious && previous != null) {
        drawImage(
            image = previous,
            dstOffset = dstOffset,
            dstSize = dstSize,
            filterQuality = FilterQuality.Low,
        )
        if (blend.currentAlpha > 0f) {
            drawImage(
                image = current,
                dstOffset = dstOffset,
                dstSize = dstSize,
                alpha = blend.currentAlpha,
                filterQuality = FilterQuality.Low,
            )
        }
    } else {
        drawImage(
            image = current,
            dstOffset = dstOffset,
            dstSize = dstSize,
            filterQuality = FilterQuality.Low,
        )
    }
}

@Composable
internal fun rememberPlayerBackdrop(accent: Color?, fromCover: Boolean, scale: Float): PlayerBackdrop {
    val look = LocalSenadaLook.current
    val targetColors = remember(accent, fromCover) { playerBackdropColors(accent, fromCover) }
    val targetAppearance = remember(targetColors, fromCover) { backdropAppearance(targetColors, fromCover) }
    val transitionDurationMs = playerBackdropTransitionDurationMs(look.animationScale)
    val currentDurationMs by rememberUpdatedState(transitionDurationMs)
    val currentTargetGlow by rememberUpdatedState(targetColors.glow)
    val backdrop = remember {
        PlayerBackdrop(
            initialAppearance = targetAppearance,
            initialGlow = targetColors.glow,
            initialScale = scale,
        )
    }
    SideEffect {
        backdrop.updateScale(scale)
        if (backdrop.appearance == targetAppearance) {
            backdrop.updateGlow(targetColors.glow)
        } else if (transitionDurationMs <= 0) {
            backdrop.snapTo(targetAppearance, targetColors.glow)
        }
    }
    LaunchedEffect(backdrop, targetAppearance) {
        if (backdrop.appearance == targetAppearance) return@LaunchedEffect
        val durationMs = currentDurationMs
        if (durationMs <= 0) {
            backdrop.snapTo(targetAppearance, currentTargetGlow)
            return@LaunchedEffect
        }
        backdrop.beginCrossfade(targetAppearance, currentTargetGlow)
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
        ) { value, _ ->
            backdrop.updateCrossfadeAlpha(value)
        }
        backdrop.finishCrossfade()
    }
    return backdrop
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
