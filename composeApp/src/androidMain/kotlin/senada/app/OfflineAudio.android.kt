package senada.app

import android.net.Uri
import coil3.PlatformContext
import java.io.File
import okio.Path.Companion.toOkioPath
import senada.offline.OfflineDownloads

private var downloads: OfflineDownloads? = null

@Synchronized
internal actual fun offlineDownloads(context: PlatformContext): OfflineDownloads = downloads ?: createOfflineDownloads(
    File(context.applicationContext.filesDir, "offline-audio").toOkioPath(),
) { path -> Uri.fromFile(path.toFile()).toString() }.also { downloads = it }
