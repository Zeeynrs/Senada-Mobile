package senada.platform

import android.content.Context
import android.net.Uri
import coil3.PlatformContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import senada.local.ImportedAudioFile
import senada.local.ImportedAudioStorage
import senada.local.LocalAudioFiles
import senada.local.isLocalAudioFileName

/** Folder salinan file audio yang diimpor pengguna, di penyimpanan privat aplikasi. */
internal fun importedAudioDir(context: Context): File = File(context.filesDir, "imported-audio")

internal actual fun importedAudioStorage(context: PlatformContext): ImportedAudioStorage =
    AndroidLocalAudioFiles(context)

internal class AndroidLocalAudioFiles(context: Context) : LocalAudioFiles, ImportedAudioStorage {
    private val dir = importedAudioDir(context.applicationContext)

    override fun uriFor(fileName: String): String? =
        File(dir, fileName).takeIf { it.isFile }?.let { Uri.fromFile(it).toString() }

    override fun listFiles(): List<ImportedAudioFile> {
        if (!dir.exists()) return emptyList()
        val files = dir.listFiles() ?: throw IOException("Folder audio impor tidak dapat dibaca.")
        return files.filter { it.isRegularFile() }.map { ImportedAudioFile(it.name, it.length()) }
    }

    override fun deleteFile(fileName: String): Boolean {
        if (!isLocalAudioFileName(fileName)) return false
        val file = File(dir, fileName)
        return file.isRegularFile() && file.delete()
    }

    private fun File.isRegularFile(): Boolean = Files.isRegularFile(toPath(), LinkOption.NOFOLLOW_LINKS)
}
