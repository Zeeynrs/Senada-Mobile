@file:OptIn(ExperimentalForeignApi::class)

package senada.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

/** Menyimpan dan membuka backup lewat document picker, jadi bisa ke Files, iCloud Drive, dan lainnya. */
@Composable
internal actual fun rememberBackupFiles(): BackupFiles = remember { IosBackupFiles() }

private class IosBackupFiles : BackupFiles {
    /** Picker memegang delegate secara lemah; simpan referensinya selama picker terbuka. */
    private var delegate: PickerDelegate? = null

    override suspend fun save(suggestedName: String, text: String): Boolean {
        val path = NSTemporaryDirectory() + suggestedName
        @Suppress("CAST_NEVER_SUCCEEDS")
        check((text as NSString).writeToFile(path, true, NSUTF8StringEncoding, null)) { "Gagal menulis file sementara" }
        val file = NSURL.fileURLWithPath(path)
        try {
            val picked = present(UIDocumentPickerViewController(forExportingURLs = listOf(file), asCopy = true))
            return picked != null
        } finally {
            NSFileManager.defaultManager.removeItemAtURL(file, null)
        }
    }

    override suspend fun open(): String? {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOfNotNull(UTTypeJSON, UTTypePlainText), asCopy = true)
        val url = present(picker) ?: return null
        val path = checkNotNull(url.path)
        return try {
            NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
        } finally {
            // asCopy menaruh salinan di folder sementara aplikasi; tidak perlu disimpan.
            NSFileManager.defaultManager.removeItemAtURL(url, null)
        }
    }

    /** Menampilkan picker di atas layar Senada; URL yang dipilih, atau null bila dibatalkan. */
    private suspend fun present(picker: UIDocumentPickerViewController): NSURL? =
        suspendCancellableCoroutine { continuation ->
            val pickerDelegate = PickerDelegate { url ->
                delegate = null
                if (continuation.isActive) continuation.resume(url)
            }
            delegate = pickerDelegate
            picker.delegate = pickerDelegate
            picker.allowsMultipleSelection = false
            continuation.invokeOnCancellation { picker.dismissViewControllerAnimated(true, null) }
            topViewController()?.presentViewController(picker, animated = true, completion = null)
                ?: pickerDelegate.finish(null)
        }

    private fun topViewController(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) {
            controller = controller.presentedViewController
        }
        return controller
    }
}

private class PickerDelegate(private val onFinish: (NSURL?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    private var finished = false

    fun finish(url: NSURL?) {
        if (finished) return
        finished = true
        onFinish(url)
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        finish(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        finish(null)
    }
}
