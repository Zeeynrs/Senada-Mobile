@file:OptIn(ExperimentalForeignApi::class)

package senada.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeAudio
import senada.local.FREE_SPACE_MARGIN_BYTES
import senada.local.ImportedAudio
import senada.local.LocalAudioException
import senada.local.newLocalFileName
import senada.local.notEnoughSpace
import senada.platform.importedAudioDirectory

@Composable
internal actual fun rememberAudioImporter(): AudioImporter = remember { IosAudioImporter() }

private class IosAudioImporter : AudioImporter {
    private val picker = DocumentPicker()

    override suspend fun import(): ImportedAudio? {
        val types = listOfNotNull(UTTypeAudio)
        // asCopy membuat salinan di folder sementara aplikasi, jadi izin file tidak perlu dipertahankan.
        val picked = picker.present(UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)) ?: return null
        return withContext(Dispatchers.IO) {
            val files = NSFileManager.defaultManager
            val source = checkNotNull(picked.path)
            try {
                val displayName = picked.lastPathComponent ?: "File audio"
                val size = (files.attributesOfItemAtPath(source, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: -1L
                val dir = importedAudioDirectory() ?: throw LocalAudioException("Folder aplikasi tidak dapat dibuka.")
                files.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
                val free = (files.attributesOfFileSystemForPath(dir, null)?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue
                if (free != null && size >= 0 && free < size + FREE_SPACE_MARGIN_BYTES) throw notEnoughSpace()

                val fileName = newLocalFileName(displayName)
                if (!files.moveItemAtPath(source, toPath = "$dir/$fileName", error = null)) {
                    throw LocalAudioException("File audio gagal disalin.")
                }
                ImportedAudio(fileName, displayName, size.coerceAtLeast(0))
            } finally {
                // Salinan sementara yang tidak jadi dipindahkan dibuang.
                if (files.fileExistsAtPath(source)) files.removeItemAtPath(source, null)
            }
        }
    }
}
