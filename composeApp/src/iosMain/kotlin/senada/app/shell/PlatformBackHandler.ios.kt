package senada.app.shell

import androidx.compose.runtime.Composable

/** iOS tidak punya tombol kembali sistem; layar ditutup lewat tombol Tutup atau usapan. */
@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
