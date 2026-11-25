package senada.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import senada.local.FREE_SPACE_MARGIN_BYTES
import senada.local.ImportedAudio
import senada.local.LocalAudioException
import senada.local.newLocalFileName
import senada.local.notEnoughSpace
import senada.platform.importedAudioDir
import senada.resources.Res
import senada.resources.import_audio_default_name
import senada.resources.import_audio_failed_copy
import senada.resources.import_audio_failed_open

@Composable
internal actual fun rememberAudioImporter(): AudioImporter {
    val context = LocalContext.current.applicationContext
    val pending = remember { PendingUri() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pending.result?.complete(uri)
    }
    return remember(launcher) {
        object : AudioImporter {
            override suspend fun import(): ImportedAudio? {
                val result = CompletableDeferred<Uri?>().also { pending.result = it }
                launcher.launch(arrayOf("audio/*"))
                val uri = result.await() ?: return null
                return withContext(Dispatchers.IO) { copyIntoApp(context, uri) }
            }
        }
    }
}

private class PendingUri {
    var result: CompletableDeferred<Uri?>? = null
}

/** Menyalin file pilihan pengguna; izin baca URI tidak dijamin bertahan, jadi salinan yang dipakai. */
private fun copyIntoApp(context: Context, uri: Uri): ImportedAudio {
    val resolver = context.contentResolver
    var displayName = AppStrings.get(Res.string.import_audio_default_name)
    var size = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameColumn >= 0 && !cursor.isNull(nameColumn)) displayName = cursor.getString(nameColumn)
            val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn)
        }
    }

    val dir = importedAudioDir(context).apply { mkdirs() }
    if (size >= 0 && dir.usableSpace < size + FREE_SPACE_MARGIN_BYTES) throw notEnoughSpace()

    val fileName = newLocalFileName(displayName)
    val target = File(dir, fileName)
    val partial = File(dir, "$fileName.part")
    try {
        val input = resolver.openInputStream(uri)
            ?: throw LocalAudioException(UiText.Resource(Res.string.import_audio_failed_open))
        input.use { source -> partial.outputStream().use { source.copyTo(it) } }
        if (!partial.renameTo(target)) throw IOException("Failed to move copied file")
    } catch (e: IOException) {
        partial.delete()
        throw if (dir.usableSpace < FREE_SPACE_MARGIN_BYTES) {
            notEnoughSpace()
        } else {
            LocalAudioException(UiText.Resource(Res.string.import_audio_failed_copy), e)
        }
    }
    return ImportedAudio(fileName, displayName, target.length()).withMetadataFrom(target)
}

private fun ImportedAudio.withMetadataFrom(file: File): ImportedAudio = try {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        copy(
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
        )
    } finally {
        retriever.release()
    }
} catch (_: Exception) {
    this
}
