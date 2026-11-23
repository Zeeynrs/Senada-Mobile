package senada.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberProfilePhotoPicker(): ProfilePhotoPicker {
    val context = LocalContext.current.applicationContext
    val pending = remember { PendingProfilePhoto() }
    val launcher = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> pending.result?.complete(uri) }
    return remember(context, launcher) {
        ProfilePhotoPicker {
            val result = CompletableDeferred<Uri?>()
            pending.result = result
            try {
                launcher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                result.await()?.let { profileJpeg(context, it) }
            } finally {
                pending.result = null
            }
        }
    }
}

private class PendingProfilePhoto {
    var result: CompletableDeferred<Uri?>? = null
}

private suspend fun profileJpeg(context: PlatformContext, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val request = ImageRequest.Builder(context).data(uri)
        .size(ProfilePhotoSize).scale(Scale.FILL).precision(Precision.INEXACT)
        .allowHardware(false).memoryCachePolicy(CachePolicy.DISABLED).build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
    val original = checkNotNull((result?.image as? BitmapImage)?.bitmap)
    val side = minOf(original.width, original.height)
    val outputSide = minOf(side, ProfilePhotoSize)
    val left = (original.width - side) / 2
    val top = (original.height - side) / 2
    val cropped = Bitmap.createBitmap(outputSide, outputSide, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(cropped)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(
            original, Rect(left, top, left + side, top + side), Rect(0, 0, outputSide, outputSide),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        ByteArrayOutputStream().use { output ->
            check(cropped.compress(Bitmap.CompressFormat.JPEG, 90, output))
            output.toByteArray()
        }
    } finally {
        cropped.recycle()
    }
}

internal actual fun saveProfilePhoto(context: PlatformContext, jpeg: ByteArray): String {
    val directory = File(context.filesDir, "profile")
    check(directory.isDirectory || directory.mkdirs())
    val file = File(directory, "${UUID.randomUUID()}.jpg")
    try {
        file.writeBytes(jpeg)
    } catch (error: Exception) {
        file.delete()
        throw error
    }
    return file.name
}

internal actual fun deleteProfilePhoto(context: PlatformContext, path: String) {
    File(File(context.filesDir, "profile"), File(path).name).delete()
}

internal actual fun profilePhotoModel(context: PlatformContext, path: String): String =
    Uri.fromFile(File(File(context.filesDir, "profile"), File(path).name)).toString()
