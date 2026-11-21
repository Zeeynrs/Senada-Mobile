package senada.app

import coil3.BitmapImage
import coil3.PlatformContext
import coil3.request.ImageRequest
import platform.Foundation.NSUserDefaults

internal actual fun appearanceStore(context: PlatformContext): AppearanceStore {
    val prefs = NSUserDefaults.standardUserDefaults
    val key = "senada.appearance"
    return AppearanceStore({ prefs.stringForKey(key) }, { prefs.setObject(it, forKey = key) })
}

internal actual fun artworkRequest(context: PlatformContext) = ImageRequest.Builder(context)

internal actual fun BitmapImage.readPixels(): IntArray =
    IntArray(width * height) { index -> bitmap.getColor(index % width, index / width) }
