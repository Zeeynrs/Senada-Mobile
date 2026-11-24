package senada.app.theme

import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import senada.app.AnimationSpeed
import senada.app.Appearance
import senada.app.DEFAULT_COVER_RADIUS_DP
import senada.app.clampCoverRadius

private const val LIST_COVER_RADIUS_NUMERATOR = 12
private const val LIST_COVER_RADIUS_DENOMINATOR = 20f

/**
 * Pengaturan tampilan visual yang dipakai bersama di seluruh UI:
 * sudut sampul besar dan daftar, efek kaca, skala animasi efektif, dan latar pemutar dari sampul.
 */
internal data class SenadaLook(
    val coverRadius: Dp = DEFAULT_COVER_RADIUS_DP.dp,
    val listCoverRadius: Dp = listCoverRadiusDp(DEFAULT_COVER_RADIUS_DP).dp,
    val glass: Boolean = true,
    val animationScale: Float = AnimationSpeed.Normal.scale,
    val playerBackdropFromCover: Boolean = true,
    val systemReduceMotion: Boolean = false,
) {
    fun durationMillis(baseMillis: Int): Int = scaledAnimationDurationMillis(baseMillis, animationScale)

    fun <T> tweenOrSnap(
        baseMillis: Int,
        easing: Easing = FastOutSlowInEasing,
    ): DurationBasedAnimationSpec<T> {
        val duration = durationMillis(baseMillis)
        return if (duration == 0) snap() else tween(durationMillis = duration, easing = easing)
    }
}

internal val LocalSenadaLook = compositionLocalOf { SenadaLook() }

/** Menghitung sudut sampul daftar (`coverRadius × 12/20`, minimal 0 dp). */
internal fun listCoverRadiusDp(coverRadiusDp: Int): Float =
    ((clampCoverRadius(coverRadiusDp) * LIST_COVER_RADIUS_NUMERATOR) / LIST_COVER_RADIUS_DENOMINATOR).coerceAtLeast(0f)

/** Menghitung skala animasi efektif; permintaan pengurangan gerak dari sistem selalu memaksa 0. */
internal fun effectiveAnimationScale(speed: AnimationSpeed, systemReduceMotion: Boolean): Float =
    if (systemReduceMotion) 0f else speed.scale.coerceAtLeast(0f)

/** Mengalikan durasi dasar (ms) dengan [animationScale]; 0 berarti tanpa animasi. */
internal fun scaledAnimationDurationMillis(baseMillis: Int, animationScale: Float): Int {
    if (baseMillis <= 0 || animationScale <= 0f) return 0
    return (baseMillis * animationScale).roundToInt().coerceAtLeast(1)
}

internal fun senadaLook(settings: Appearance, systemReduceMotion: Boolean): SenadaLook {
    val clampedRadius = clampCoverRadius(settings.coverRadius)
    return SenadaLook(
        coverRadius = clampedRadius.dp,
        listCoverRadius = listCoverRadiusDp(clampedRadius).dp,
        glass = settings.glass,
        animationScale = effectiveAnimationScale(settings.animation, systemReduceMotion),
        playerBackdropFromCover = settings.playerBackdropFromCover,
        systemReduceMotion = systemReduceMotion,
    )
}

/** Benar bila sistem meminta pengurangan gerak (Android ANIMATOR_DURATION_SCALE == 0, iOS Reduce Motion). */
@Composable
internal expect fun rememberSystemReduceMotion(): Boolean
