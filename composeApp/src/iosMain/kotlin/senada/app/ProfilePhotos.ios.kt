@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package senada.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.PlatformContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSItemProvider
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIRectFill
import platform.darwin.NSObject

@Composable
internal actual fun rememberProfilePhotoPicker(): ProfilePhotoPicker = remember { IosProfilePhotoPicker() }

private class IosProfilePhotoPicker : ProfilePhotoPicker {
    // PHPicker memegang delegate secara lemah.
    private var delegate: ProfilePhotoDelegate? = null

    override suspend fun pick(): ByteArray? {
        val provider = selectPhoto() ?: return null
        val source = suspendCancellableCoroutine { continuation ->
            val progress = provider.loadDataRepresentationForTypeIdentifier("public.image") { data, error ->
                if (data == null) {
                    continuation.resumeWithException(IllegalStateException(error?.localizedDescription))
                } else {
                    continuation.resume(data)
                }
            }
            continuation.invokeOnCancellation { progress.cancel() }
        }
        return withContext(Dispatchers.Default) { checkNotNull(UIImage.imageWithData(source)).toProfileJpeg() }
    }

    private suspend fun selectPhoto(): NSItemProvider? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) controller = controller.presentedViewController
        val presenter = checkNotNull(controller)
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter()
            selectionLimit = 1
        }
        val picker = PHPickerViewController(configuration)
        return try {
            suspendCancellableCoroutine { continuation ->
                val callback = ProfilePhotoDelegate { provider -> continuation.resume(provider) }
                delegate = callback
                picker.delegate = callback
                presenter.presentViewController(picker, animated = true, completion = null)
            }
        } finally {
            if (picker.presentingViewController != null) picker.dismissViewControllerAnimated(true, null)
            delegate = null
        }
    }
}

private class ProfilePhotoDelegate(private val finish: (NSItemProvider?) -> Unit) :
    NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true) {
            finish((didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider)
        }
    }
}

private fun UIImage.toProfileJpeg(): ByteArray {
    val (width, height) = size.useContents { width to height }
    val side = minOf(width, height)
    check(side > 0)
    val pixels = minOf(side * scale, ProfilePhotoSize.toDouble()).toInt().coerceAtLeast(1).toDouble()
    val multiplier = pixels / side
    val scaledWidth = width * multiplier
    val scaledHeight = height * multiplier
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(pixels, pixels), true, 1.0)
    try {
        UIColor.whiteColor.setFill()
        UIRectFill(CGRectMake(0.0, 0.0, pixels, pixels))
        drawInRect(CGRectMake((pixels - scaledWidth) / 2, (pixels - scaledHeight) / 2, scaledWidth, scaledHeight))
        val jpeg = checkNotNull(UIImageJPEGRepresentation(checkNotNull(UIGraphicsGetImageFromCurrentImageContext()), 0.9))
        return checkNotNull(jpeg.bytes).readBytes(jpeg.length.toInt())
    } finally {
        UIGraphicsEndImageContext()
    }
}

private fun profileDirectory(): String = checkNotNull(
    NSFileManager.defaultManager.URLForDirectory(
        NSApplicationSupportDirectory, NSUserDomainMask, appropriateForURL = null, create = true, error = null,
    )?.path,
) + "/profile"

internal actual fun saveProfilePhoto(context: PlatformContext, jpeg: ByteArray): String {
    val directory = profileDirectory().toPath()
    val files = FileSystem.SYSTEM
    files.createDirectories(directory)
    val name = "${NSUUID().UUIDString}.jpg"
    val path = directory / name
    try {
        files.write(path, mustCreate = true) { write(jpeg) }
    } catch (error: Exception) {
        files.delete(path)
        throw error
    }
    return name
}

internal actual fun deleteProfilePhoto(context: PlatformContext, path: String) {
    val file = profileDirectory().toPath() / path.toPath().name
    FileSystem.SYSTEM.delete(file)
}

internal actual fun profilePhotoModel(context: PlatformContext, path: String): String =
    NSURL.fileURLWithPath(profileDirectory() + "/" + path.toPath().name).absoluteString.orEmpty()
