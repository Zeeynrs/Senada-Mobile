package senada.app.nowplaying

import androidx.compose.ui.geometry.Offset
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

internal class VinylGeometry(width: Float, height: Float) {
    val radius = minOf(width * .57f, height * .60f)
    val center = Offset(width * .37f, height * .39f)
    val pivot = center + Offset(radius * .96f, -radius * .61f)
    val labelRadius = radius * .39f
    private val outerGroove = radius * .94f
    private val innerGroove = radius * .48f
    private val armLength = radius * 1.08f

    fun needleAt(progress: Float): Offset {
        if (radius <= 0f) return pivot
        val groove = outerGroove - progress.coerceIn(0f, 1f) * (outerGroove - innerGroove)
        val direction = center - pivot
        val distance = direction.getDistance()
        // Triangle between the spindle, the arm pivot, and the needle tip.
        val cosine = (armLength * armLength + distance * distance - groove * groove) /
            (2f * armLength * distance)
        val angle = atan2(direction.y, direction.x) - acos(cosine.coerceIn(-1f, 1f))
        return pivot + Offset(cos(angle), sin(angle)) * armLength
    }

    fun progressAt(point: Offset): Float {
        if (radius <= 0f) return 0f
        val groove = (point - center).getDistance()
        return ((outerGroove - groove) / (outerGroove - innerGroove)).coerceIn(0f, 1f)
    }
}

internal fun vinylSeekPosition(progress: Float, durationMs: Long): Long =
    (progress.coerceIn(0f, 1f) * durationMs.toDouble()).roundToLong()
