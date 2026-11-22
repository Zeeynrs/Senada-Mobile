package senada.app.shell

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalDragOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun rememberNowPlayingPanel(navigator: AppNavigator): NowPlayingPanelState {
    val scope = rememberCoroutineScope()
    val panel = remember(navigator, scope) { NowPlayingPanelState(navigator, scope) }
    LaunchedEffect(panel, navigator.nowPlayingOpen) { panel.settle() }
    return panel
}

@Stable
internal class NowPlayingPanelState(private val navigator: AppNavigator, private val scope: CoroutineScope) {
    private var animation: Job? = null
    private var openingSwipe: Boolean? = null
    private var height = 0f

    var progress by mutableFloatStateOf(0f)
        private set
    val visible by derivedStateOf { navigator.nowPlayingOpen || progress > 0f }

    fun updateHeight(heightPx: Int) {
        height = heightPx.toFloat()
    }

    fun beginSwipe(opening: Boolean): Boolean {
        if (height <= 0f || openingSwipe != null || navigator.nowPlayingOpen == opening) return false
        animation?.cancel()
        openingSwipe = opening
        return true
    }

    fun dragBy(opening: Boolean, deltaY: Float) {
        if (openingSwipe == opening && height > 0f) {
            progress = (progress - deltaY / height).coerceIn(0f, 1f)
        }
    }

    fun releaseSwipe(opening: Boolean, velocity: Float, flingThreshold: Float) {
        if (openingSwipe != opening) return
        val commit = if (opening) {
            shouldOpenNowPlaying(progress * height, height, velocity, flingThreshold)
        } else {
            shouldCloseNowPlaying((1f - progress) * height, height, velocity, flingThreshold)
        }
        if (commit && navigator.nowPlayingOpen != opening) {
            openingSwipe = null
            if (opening) navigator.openNowPlaying() else navigator.closeNowPlaying()
        } else {
            settle()
        }
    }

    fun cancelSwipe(opening: Boolean) {
        if (openingSwipe == opening) settle()
    }

    fun settle() {
        openingSwipe = null
        animation?.cancel()
        val target = if (navigator.nowPlayingOpen) 1f else 0f
        if (progress == target || nowPlayingReduceMotionEnabled()) {
            progress = target
            return
        }
        animation = scope.launch {
            animate(progress, target, animationSpec = tween(275, easing = FastOutSlowInEasing)) { value, _ ->
                progress = value
            }
        }
    }
}

internal expect fun nowPlayingReduceMotionEnabled(): Boolean

@Composable
internal fun Modifier.nowPlayingSwipe(
    panel: NowPlayingPanelState,
    opening: Boolean,
    canStart: () -> Boolean = { true },
): Modifier {
    val currentCanStart by rememberUpdatedState(canStart)
    return pointerInput(panel, opening) {
        val flingThreshold = 1000.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!currentCanStart()) return@awaitEachGesture
            val tracker = VelocityTracker()
            tracker.addPointerInputChange(down)
            var displacement = Offset.Zero
            var change: PointerInputChange
            do {
                change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed || change.isConsumed) return@awaitEachGesture
                tracker.addPointerInputChange(change)
                displacement += change.positionChange()
                if (displacement.getDistance() >= viewConfiguration.touchSlop) break
                awaitPointerEvent(PointerEventPass.Final)
                if (change.isConsumed) return@awaitEachGesture
            } while (true)

            // Biarkan ketukan, geser mendatar, dan gulir ke arah lain ditangani kontrol yang sudah ada.
            if (abs(displacement.x) >= abs(displacement.y) || (displacement.y < 0f) != opening ||
                !currentCanStart() || !panel.beginSwipe(opening)
            ) return@awaitEachGesture

            change.consume()
            panel.dragBy(opening, displacement.y)
            try {
                while (true) {
                    change = awaitVerticalDragOrCancellation(change.id) ?: break
                    tracker.addPointerInputChange(change)
                    if (!change.pressed) {
                        panel.releaseSwipe(opening, tracker.calculateVelocity().y, flingThreshold)
                        break
                    }
                    val deltaY = change.positionChange().y
                    change.consume()
                    panel.dragBy(opening, deltaY)
                }
            } finally {
                panel.cancelSwipe(opening)
            }
        }
    }
}
