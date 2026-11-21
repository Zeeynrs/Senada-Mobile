package senada.app.nowplaying

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.interop.LocalUIViewController
import platform.UIKit.UIUserInterfaceStyle

@Composable
internal actual fun NowPlayingSystemBars() {
    val window = LocalUIViewController.current.view.window ?: return
    DisposableEffect(window) {
        val previous = window.overrideUserInterfaceStyle
        window.overrideUserInterfaceStyle = UIUserInterfaceStyle.UIUserInterfaceStyleDark
        onDispose { window.overrideUserInterfaceStyle = previous }
    }
}
