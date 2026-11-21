package senada.app

import android.content.Context
import coil3.BitmapImage
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.allowHardware

internal actual fun appearanceStore(context: PlatformContext): AppearanceStore {
    val prefs by lazy { context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    return AppearanceStore({ prefs.getString("settings", null) }, { prefs.edit().putString("settings", it).apply() })
}

internal actual fun artworkRequest(context: PlatformContext) = ImageRequest.Builder(context).allowHardware(false)

internal actual fun BitmapImage.readPixels(): IntArray = IntArray(width * height).also {
    bitmap.getPixels(it, 0, width, 0, 0, width, height)
}
