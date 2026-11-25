package senada.app.shell

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal fun isBackSwipe(start: Offset, end: Offset, edgeWidth: Float, minimumTravel: Float): Boolean {
    val travel = end - start
    return start.x in 0f..edgeWidth && travel.x >= minimumTravel && travel.x > abs(travel.y)
}

@Composable
internal fun Modifier.backSwipe(enabled: Boolean, onBack: () -> Unit): Modifier {
    if (!usesInAppBackSwipe || !enabled) return this
    val currentOnBack by rememberUpdatedState(onBack)
    return pointerInput(Unit) {
        val edgeWidth = 24.dp.toPx()
        val minimumTravel = 64.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            val start = down.position
            if (start.x !in 0f..edgeWidth) return@awaitEachGesture

            var draggingBack = false
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.singleOrNull()
                    ?.takeIf { it.id == down.id && !it.isConsumed } ?: return@awaitEachGesture
                if (!change.pressed) {
                    if (draggingBack && isBackSwipe(start, change.position, edgeWidth, minimumTravel)) {
                        change.consume()
                        currentOnBack()
                    }
                    return@awaitEachGesture
                }

                if (!draggingBack) {
                    if ((change.position - start).getDistance() < viewConfiguration.touchSlop) continue
                    if (!isBackSwipe(start, change.position, edgeWidth, minimumTravel = 0f)) return@awaitEachGesture
                    draggingBack = true
                }
                // Setelah arah terkonfirmasi, gestur tepi diprioritaskan di atas kontrol dalam halaman.
                change.consume()
            }
        }
    }
}
