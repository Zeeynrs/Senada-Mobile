package senada.app.shell

import androidx.compose.runtime.Composable

@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

internal actual val usesInAppBackSwipe: Boolean = true
