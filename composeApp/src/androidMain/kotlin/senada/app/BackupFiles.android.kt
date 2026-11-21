package senada.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import senada.backup.BackupCodec

/** Menyimpan dan membuka backup lewat Storage Access Framework, jadi bisa ke Files, Drive, dan lainnya. */
@Composable
internal actual fun rememberBackupFiles(): BackupFiles {
    val context = LocalContext.current.applicationContext
    val pending = remember { PendingResults() }
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON)) { uri ->
        pending.save?.complete(uri)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pending.open?.complete(uri)
    }
    return remember(createLauncher, openLauncher) {
        object : BackupFiles {
            override suspend fun save(suggestedName: String, text: String): Boolean {
                val result = CompletableDeferred<Uri?>().also { pending.save = it }
                createLauncher.launch(suggestedName)
                val uri = result.await() ?: return false
                withContext(Dispatchers.IO) {
                    // "wt" mengosongkan file lama bila pengguna memilih file yang sudah ada.
                    val stream = checkNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                    stream.use { it.write(text.encodeToByteArray()) }
                }
                return true
            }

            override suspend fun open(): String? {
                val result = CompletableDeferred<Uri?>().also { pending.open = it }
                // Banyak pengelola file melabeli .json sebagai teks biasa atau data umum.
                openLauncher.launch(arrayOf(JSON, "text/plain", "application/octet-stream"))
                val uri = result.await() ?: return null
                return withContext(Dispatchers.IO) {
                    val stream = checkNotNull(context.contentResolver.openInputStream(uri))
                    // Baca paling banyak satu byte di atas batas; BackupCodec menolak yang terlalu besar.
                    val bytes = stream.use { it.readNBytesCompat(BackupCodec.MAX_BYTES + 1) }
                    bytes.decodeToString()
                }
            }
        }
    }
}

/** Hasil pemilih file yang sedang ditunggu; launcher Compose hanya memberi callback. */
private class PendingResults {
    var save: CompletableDeferred<Uri?>? = null
    var open: CompletableDeferred<Uri?>? = null
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private const val JSON = "application/json"
