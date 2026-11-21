package senada.app

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/** Menampilkan document picker di atas layar Senada, untuk backup dan file audio. */
internal class DocumentPicker {
    /** Picker memegang delegate secara lemah; simpan referensinya selama picker terbuka. */
    private var delegate: PickerDelegate? = null

    /** URL yang dipilih, atau null bila dibatalkan. */
    suspend fun present(picker: UIDocumentPickerViewController): NSURL? =
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
