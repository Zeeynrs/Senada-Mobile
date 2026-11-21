package senada.platform

import android.content.Context
import android.net.Uri
import java.io.File
import senada.local.LocalAudioFiles

/** Folder salinan file audio yang diimpor pengguna, di penyimpanan privat aplikasi. */
internal fun importedAudioDir(context: Context): File = File(context.filesDir, "imported-audio")

internal class AndroidLocalAudioFiles(context: Context) : LocalAudioFiles {
    private val dir = importedAudioDir(context.applicationContext)

    override fun uriFor(fileName: String): String? =
        File(dir, fileName).takeIf { it.isFile }?.let { Uri.fromFile(it).toString() }
}
