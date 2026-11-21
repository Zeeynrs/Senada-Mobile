package senada.app.shell

import androidx.compose.runtime.Composable

/** Tombol kembali sistem: Android memakainya; iOS menutup layar lewat tombol atau usapan. */
@Composable
internal expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
