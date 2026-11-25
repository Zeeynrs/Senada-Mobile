package senada.app.shell

import androidx.compose.runtime.Composable

/** Tombol atau gestur kembali yang disediakan sistem. */
@Composable
internal expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

internal expect val usesInAppBackSwipe: Boolean
