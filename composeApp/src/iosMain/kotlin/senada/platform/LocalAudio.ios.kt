@file:OptIn(ExperimentalForeignApi::class)

package senada.platform

import coil3.PlatformContext
import kotlinx.cinterop.ExperimentalForeignApi
import okio.IOException
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeRegular
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import senada.local.ImportedAudioFile
import senada.local.ImportedAudioStorage
import senada.local.LocalAudioFiles
import senada.local.isLocalAudioFileName

/** Folder salinan file audio yang diimpor pengguna, di Application Support milik aplikasi. */
internal fun importedAudioDirectory(): String? = NSFileManager.defaultManager.URLForDirectory(
    NSApplicationSupportDirectory,
    NSUserDomainMask,
    appropriateForURL = null,
    create = true,
    error = null,
)?.path?.let { "$it/imported-audio" }

internal actual fun importedAudioStorage(context: PlatformContext): ImportedAudioStorage = IosLocalAudioFiles()

internal class IosLocalAudioFiles : LocalAudioFiles, ImportedAudioStorage {
    private val files = NSFileManager.defaultManager

    override fun uriFor(fileName: String): String? {
        val path = importedAudioDirectory()?.let { "$it/$fileName" } ?: return null
        if (!files.fileExistsAtPath(path)) return null
        return NSURL.fileURLWithPath(path).absoluteString
    }

    override fun listFiles(): List<ImportedAudioFile> {
        val dir = importedAudioDirectory() ?: throw IOException("Folder audio impor tidak tersedia.")
        if (!files.fileExistsAtPath(dir)) return emptyList()
        val names = files.contentsOfDirectoryAtPath(dir, error = null)
            ?: throw IOException("Folder audio impor tidak dapat dibaca.")
        return names.filterIsInstance<String>().mapNotNull { name ->
            val attributes = files.attributesOfItemAtPath("$dir/$name", error = null)
            val size = (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue
            if (attributes?.get(NSFileType) == NSFileTypeRegular && size != null) {
                ImportedAudioFile(name, size)
            } else {
                null
            }
        }
    }

    override fun deleteFile(fileName: String): Boolean {
        if (!isLocalAudioFileName(fileName)) return false
        val dir = importedAudioDirectory() ?: return false
        val path = "$dir/$fileName"
        return files.attributesOfItemAtPath(path, error = null)?.get(NSFileType) == NSFileTypeRegular &&
            files.removeItemAtPath(path, error = null)
    }
}
