package senada.local

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.IOException
import senada.library.LibraryRepository
import senada.model.Track

internal interface ImportedAudioStorage {
    fun listFiles(): List<ImportedAudioFile>
    fun deleteFile(fileName: String): Boolean
}

internal data class ImportedAudioFile(val fileName: String, val sizeBytes: Long)

internal data class ImportedAudioSummary(val count: Int, val sizeBytes: Long)

internal data class ImportedAudioCleanupResult(
    val unusedCount: Int,
    val failedCount: Int,
    val summary: ImportedAudioSummary?,
)

/** Dipakai sampai impor selesai mencatat lagu ke library dan antrean, termasuk setelah file disalin. */
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

/**
 * Menyelaraskan catatan file impor di [library] dengan isi [storage]. Gagal membaca folder
 * diabaikan agar daftar yang ada tidak terhapus karena kesalahan I/O sementara.
 */
internal suspend fun syncImportedAudio(
    storage: ImportedAudioStorage,
    library: LibraryRepository,
    queueTracks: () -> List<Track> = ::emptyList,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    importedAudioLock.withLock {
        val files = withContext(ioDispatcher) {
            try {
                storage.listFiles()
            } catch (_: IOException) {
                null
            }
        } ?: return
        val existing = files.mapNotNullTo(mutableSetOf()) { it.fileName.takeIf(::isLocalAudioFileName) }
        val known = queueTracks()
        library.syncImportedFiles(existing, known)
    }
}

/**
 * Menghapus file audio impor yang tidak lagi dipakai antrean maupun library (favorit, playlist,
 * riwayat, hitungan putar), lalu menyelaraskan daftar "File impor".
 */
internal suspend fun deleteUnusedImportedAudio(
    storage: ImportedAudioStorage,
    library: LibraryRepository,
    queueTracks: () -> List<Track>,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): ImportedAudioCleanupResult = importedAudioLock.withLock {
    val files = withContext(ioDispatcher) { storage.listFiles() }
    val deleted = mutableListOf<String>()
    val counts = try {
        library.withLocalTrackIds { libraryIds ->
            // Baca antrean dan hapus tanpa jeda di thread UI, tempat pemutar mengubah antrean.
            val unused = unusedImportedAudioFileNames(files, queueTracks(), libraryIds)
            var failed = 0
            for (fileName in unused) {
                if (storage.deleteFile(fileName)) deleted += fileName else failed++
            }
            unused.size to failed
        }
    } catch (e: Throwable) {
        if (deleted.isNotEmpty()) withContext(NonCancellable) { library.removeImportedFiles(deleted) }
        throw e
    }
    val remaining = withContext(ioDispatcher) {
        try {
            storage.listFiles()
        } catch (_: IOException) {
            null
        }
    }
    if (remaining != null) {
        val existing = remaining.mapNotNullTo(mutableSetOf()) { it.fileName.takeIf(::isLocalAudioFileName) }
        library.syncImportedFiles(existing)
    } else if (deleted.isNotEmpty()) {
        library.removeImportedFiles(deleted)
    }
    ImportedAudioCleanupResult(
        unusedCount = counts.first,
        failedCount = counts.second,
        summary = remaining?.let(::summarizeImportedAudio),
    )
}
