package senada.app

import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier

internal actual fun Modifier.excludeSystemGestures(): Modifier = systemGestureExclusion()
