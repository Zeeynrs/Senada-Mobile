@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package senada.app

import coil3.PlatformContext
import okio.Path.Companion.toPath
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSURL
import senada.offline.OfflineDownloads

private val downloads by lazy {
    createOfflineDownloads((NSHomeDirectory() + "/Library/Application Support/offline-audio").toPath()) { path ->
        checkNotNull(NSURL.fileURLWithPath(path.toString()).absoluteString)
    }
}

internal actual fun offlineDownloads(context: PlatformContext): OfflineDownloads = downloads
