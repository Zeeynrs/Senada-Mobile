package senada.app.shell

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@OptIn(ExperimentalForeignApi::class)
internal actual fun nowPlayingReduceMotionEnabled(): Boolean = UIAccessibilityIsReduceMotionEnabled()
