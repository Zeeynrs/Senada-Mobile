package senada.app

import androidx.compose.runtime.Composable

/** Pemilih file sistem untuk backup: Storage Access Framework di Android, document picker di iOS. */
internal interface BackupFiles {
    /** Meminta pengguna memilih tempat menyimpan [text]; false bila dibatalkan. */
    suspend fun save(suggestedName: String, text: String): Boolean

    /** Isi file JSON yang dipilih pengguna, atau null bila dibatalkan. */
    suspend fun open(): String?
}

@Composable
internal expect fun rememberBackupFiles(): BackupFiles
