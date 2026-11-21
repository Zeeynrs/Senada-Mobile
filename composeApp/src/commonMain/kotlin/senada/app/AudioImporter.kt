package senada.app

import androidx.compose.runtime.Composable
import senada.local.ImportedAudio

/** Memilih satu file audio lewat pemilih dokumen sistem lalu menyalinnya ke penyimpanan aplikasi. */
internal interface AudioImporter {
    /** File yang sudah disalin, atau null bila pengguna membatalkan. Gagal menyalin → `LocalAudioException`. */
    suspend fun import(): ImportedAudio?
}

@Composable
internal expect fun rememberAudioImporter(): AudioImporter
