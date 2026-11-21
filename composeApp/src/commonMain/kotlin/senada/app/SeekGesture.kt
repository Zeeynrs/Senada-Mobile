package senada.app

import androidx.compose.ui.Modifier

/**
 * Area geser posisi lagu tidak boleh direbut gestur sistem: di Android, usapan yang dimulai di tepi layar
 * dibaca sebagai "kembali" dan menutup Sedang diputar alih-alih menggeser lagu.
 */
internal expect fun Modifier.excludeSystemGestures(): Modifier
