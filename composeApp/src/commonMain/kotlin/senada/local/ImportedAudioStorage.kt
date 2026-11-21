package senada.local

import kotlinx.coroutines.sync.Mutex
import senada.model.Track

internal interface ImportedAudioStorage {
    fun listFiles(): List<ImportedAudioFile>
    fun deleteFile(fileName: String): Boolean
}

internal data class ImportedAudioFile(val fileName: String, val sizeBytes: Long)

internal data class ImportedAudioSummary(val count: Int, val sizeBytes: Long)

/** Dipakai sampai impor selesai menambahkan lagu ke antrean, termasuk setelah file selesai disalin. */
internal val importedAudioLock = Mutex()

internal fun unusedImportedAudioFileNames(
    files: List<ImportedAudioFile>,
    queueTracks: List<Track>,
    libraryTrackIds: List<String>,
): List<String> {
    val used = buildSet {
        queueTracks.mapNotNullTo(this, ::localFileNameOf)
        libraryTrackIds.mapNotNullTo(this, ::localFileNameFromId)
    }
    return files.map { it.fileName }.filter { isLocalAudioFileName(it) && it !in used }
}

internal fun summarizeImportedAudio(files: List<ImportedAudioFile>): ImportedAudioSummary {
    val listed = files.filter { isLocalAudioFileName(it.fileName) }
    return ImportedAudioSummary(listed.size, listed.sumOf { it.sizeBytes })
}
