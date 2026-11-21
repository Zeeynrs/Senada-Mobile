@file:OptIn(ExperimentalForeignApi::class)

package senada.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVMetadataCommonKeyArtist
import platform.AVFoundation.AVMetadataCommonKeyTitle
import platform.AVFoundation.AVMetadataItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.commonKey
import platform.AVFoundation.commonMetadata
import platform.AVFoundation.stringValue
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
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
                val target = "$dir/$fileName"
                if (!files.moveItemAtPath(source, toPath = target, error = null)) {
                    throw LocalAudioException("File audio gagal disalin.")
                }
                ImportedAudio(fileName, displayName, size.coerceAtLeast(0)).withMetadataFrom(target)
            } finally {
                // Salinan sementara yang tidak jadi dipindahkan dibuang.
                if (files.fileExistsAtPath(source)) files.removeItemAtPath(source, null)
            }
        }
    }
}

private fun ImportedAudio.withMetadataFrom(path: String): ImportedAudio = try {
    val asset = AVURLAsset.URLAssetWithURL(NSURL.fileURLWithPath(path), options = null)
    val metadata = asset.commonMetadata.filterIsInstance<AVMetadataItem>()
    fun textFor(key: String?): String? = metadata.firstNotNullOfOrNull { item ->
        if (key != null && item.commonKey == key) item.stringValue?.takeIf { it.isNotBlank() } else null
    }

    val seconds = CMTimeGetSeconds(asset.duration)
    copy(
        title = textFor(AVMetadataCommonKeyTitle),
        artist = textFor(AVMetadataCommonKeyArtist),
        durationMs = seconds.takeIf { it.isFinite() }?.let { (it * 1_000).toLong() },
    )
} catch (_: Exception) {
    this
}
