@file:OptIn(ExperimentalForeignApi::class)

package senada.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText

/** Menyimpan dan membuka backup lewat document picker, jadi bisa ke Files, iCloud Drive, dan lainnya. */
@Composable
internal actual fun rememberBackupFiles(): BackupFiles = remember { IosBackupFiles() }

private class IosBackupFiles : BackupFiles {
    private val picker = DocumentPicker()

    override suspend fun save(suggestedName: String, text: String): Boolean {
        val path = NSTemporaryDirectory() + suggestedName
        @Suppress("CAST_NEVER_SUCCEEDS")
        check((text as NSString).writeToFile(path, true, NSUTF8StringEncoding, null)) { "Gagal menulis file sementara" }
        val file = NSURL.fileURLWithPath(path)
        try {
            return picker.present(UIDocumentPickerViewController(forExportingURLs = listOf(file), asCopy = true)) != null
        } finally {
            NSFileManager.defaultManager.removeItemAtURL(file, null)
        }
    }

    override suspend fun open(): String? {
        val types = listOfNotNull(UTTypeJSON, UTTypePlainText)
        val url = picker.present(UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)) ?: return null
        val path = checkNotNull(url.path)
        return try {
            NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
        } finally {
            // asCopy menaruh salinan di folder sementara aplikasi; tidak perlu disimpan.
            NSFileManager.defaultManager.removeItemAtURL(url, null)
        }
    }
}
