package senada.app

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toOkioPath

internal actual fun artworkCacheDirectory(context: PlatformContext): Path =
    context.cacheDir.toOkioPath() / ARTWORK_CACHE_DIRECTORY
