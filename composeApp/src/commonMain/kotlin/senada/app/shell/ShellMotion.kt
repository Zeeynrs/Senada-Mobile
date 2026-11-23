package senada.app.shell

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import senada.app.theme.SenadaLook

internal const val SHELL_TAB_FADE_MS = 180
internal const val SHELL_ROUTE_SLIDE_FORWARD_MS = 220
internal const val SHELL_ROUTE_SLIDE_BACKWARD_MS = 200
internal const val SHELL_OVERLAY_ANIMATION_MS = 200
internal const val SHELL_NAV_PILL_MS = 180

internal data class ShellPageKey(
    val destination: AppDestination,
    val profileDepth: Int,
    val profileRoute: ProfileRoute?,
    val catalogDepth: Int,
    val catalogId: String?,
    val openedPlaylistId: String?,
)

internal enum class ShellTransitionKind {
    None,
    Fade,
    SlideForward,
    SlideBackward,
}

internal fun shellTransitionKind(previous: ShellPageKey, next: ShellPageKey): ShellTransitionKind {
    if (previous == next) return ShellTransitionKind.None
    if (previous.destination != next.destination) return ShellTransitionKind.Fade
    val previousDepth = previous.profileDepth + previous.catalogDepth + if (previous.openedPlaylistId != null) 1 else 0
    val nextDepth = next.profileDepth + next.catalogDepth + if (next.openedPlaylistId != null) 1 else 0
    return when {
        nextDepth > previousDepth -> ShellTransitionKind.SlideForward
        nextDepth < previousDepth -> ShellTransitionKind.SlideBackward
        else -> ShellTransitionKind.Fade
    }
}

internal fun shellTransitionBaseDurationMs(kind: ShellTransitionKind): Int = when (kind) {
    ShellTransitionKind.None -> 0
    ShellTransitionKind.Fade -> SHELL_TAB_FADE_MS
    ShellTransitionKind.SlideForward -> SHELL_ROUTE_SLIDE_FORWARD_MS
    ShellTransitionKind.SlideBackward -> SHELL_ROUTE_SLIDE_BACKWARD_MS
}

internal fun shellOverlayEnter(look: SenadaLook): EnterTransition {
    val duration = look.durationMillis(SHELL_OVERLAY_ANIMATION_MS)
    if (duration == 0) return EnterTransition.None
    return fadeIn(tween(durationMillis = duration)) +
        expandVertically(
            animationSpec = tween<IntSize>(durationMillis = duration, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Bottom,
        ) +
        slideInVertically(
            animationSpec = tween<IntOffset>(durationMillis = duration, easing = FastOutSlowInEasing),
        ) { it / 3 }
}

internal fun shellOverlayExit(look: SenadaLook): ExitTransition {
    val duration = look.durationMillis(SHELL_OVERLAY_ANIMATION_MS)
    if (duration == 0) return ExitTransition.None
    return fadeOut(tween(durationMillis = duration)) +
        shrinkVertically(
            animationSpec = tween<IntSize>(durationMillis = duration, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Bottom,
        ) +
        slideOutVertically(
            animationSpec = tween<IntOffset>(durationMillis = duration, easing = FastOutSlowInEasing),
        ) { it / 3 }
}

/**
 * Menganimasikan perpindahan tab dan slide halaman turunan Profil/katalog dengan durasi
 * yang dikalikan [SenadaLook.animationScale] (0 = langsung tanpa animasi).
 */
@Composable
internal fun Modifier.shellPageMotion(pageKey: ShellPageKey, look: SenadaLook): Modifier {
    val lastKey = remember { arrayOf(pageKey) }
    val kind = remember(pageKey) {
        shellTransitionKind(lastKey[0], pageKey).also { lastKey[0] = pageKey }
    }
    val duration = remember(pageKey) { look.durationMillis(shellTransitionBaseDurationMs(kind)) }
    val progress = remember(pageKey) { Animatable(if (duration == 0) 1f else 0f) }
    val slidePx = with(LocalDensity.current) { 28.dp.toPx() }

    LaunchedEffect(pageKey, duration) {
        if (duration > 0 && progress.value < 1f) {
            progress.animateTo(1f, tween(durationMillis = duration, easing = FastOutSlowInEasing))
        } else if (progress.value != 1f) {
            progress.snapTo(1f)
        }
    }

    return graphicsLayer {
        val p = progress.value
        if (p < 1f) {
            when (kind) {
                ShellTransitionKind.None -> Unit
                ShellTransitionKind.Fade -> {
                    alpha = 0.25f + 0.75f * p
                }
                ShellTransitionKind.SlideForward -> {
                    alpha = 0.25f + 0.75f * p
                    translationX = (1f - p) * slidePx
                }
                ShellTransitionKind.SlideBackward -> {
                    alpha = 0.25f + 0.75f * p
                    translationX = -(1f - p) * slidePx
                }
            }
        }
    }
}
