@file:OptIn(ExperimentalForeignApi::class)

package senada.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import senada.local.LocalAudioFiles

/** Folder salinan file audio yang diimpor pengguna, di Application Support milik aplikasi. */
internal fun importedAudioDirectory(): String? = NSFileManager.defaultManager.URLForDirectory(
    NSApplicationSupportDirectory,
    NSUserDomainMask,
    appropriateForURL = null,
    create = true,
    error = null,
)?.path?.let { "$it/imported-audio" }

internal class IosLocalAudioFiles : LocalAudioFiles {
    override fun uriFor(fileName: String): String? {
        val path = importedAudioDirectory()?.let { "$it/$fileName" } ?: return null
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
        return NSURL.fileURLWithPath(path).absoluteString
    }
}
