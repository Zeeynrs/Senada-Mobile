package senada.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.IOException
import okio.Path
import senada.library.LibraryRepository
import senada.local.ImportedAudioStorage
import senada.local.ImportedAudioSummary
import senada.local.importedAudioLock
import senada.local.summarizeImportedAudio
import senada.local.unusedImportedAudioFileNames
import senada.platform.importedAudioStorage
import senada.playback.PlaybackController

internal const val ARTWORK_CACHE_DIRECTORY = "coil3_disk_cache"

internal expect fun artworkCacheDirectory(context: PlatformContext): Path

private val storageCacheLock = Mutex()

@Composable
internal fun StorageSection(playback: PlaybackController, library: LibraryRepository) {
    val context = LocalPlatformContext.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    val scope = rememberCoroutineScope()
    var cacheSize by remember(imageLoader) { mutableStateOf<Long?>(null) }
    var readingSize by remember(imageLoader) { mutableStateOf(true) }
    var clearing by remember(imageLoader) { mutableStateOf(false) }
    var clearFailed by remember(imageLoader) { mutableStateOf(false) }

    LaunchedEffect(imageLoader) {
        cacheSize = imageLoader.readArtworkCacheSize()
        readingSize = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Penyimpanan", style = MaterialTheme.typography.h6)
        Text(
            cacheSize?.let { "Cache sampul: ${formatSize(it)}" }
                ?: if (readingSize) "Menghitung ukuran cache sampul…" else "Ukuran cache sampul tidak tersedia.",
            style = MaterialTheme.typography.body2,
        )
        OutlinedButton(
            enabled = !readingSize && !clearing,
            onClick = {
                clearing = true
                clearFailed = false
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        // Penghapusan yang sudah dimulai tetap selesai saat pengguna pindah tab.
                        withContext(NonCancellable) {
                            storageCacheLock.withLock {
                                SenadaSources.lyrics.clearCache()
                                withContext(Dispatchers.IO) {
                                    imageLoader.memoryCache?.clear()
                                    imageLoader.diskCache?.clear()
                                }
                            }
                        }
                    } catch (_: IOException) {
                        clearFailed = true
                    } finally {
                        cacheSize = imageLoader.readArtworkCacheSize()
                        clearing = false
                    }
                }
            },
        ) { Text(if (clearing) "Menghapus…" else "Hapus cache") }
        if (clearFailed) Text("Cache sampul tidak dapat dihapus sepenuhnya.", style = MaterialTheme.typography.body2)
        val importedAudio = remember(context) { importedAudioStorage(context) }
        ImportedAudioStorageControls(importedAudio, playback, library)
    }
}

@Composable
private fun ImportedAudioStorageControls(
    storage: ImportedAudioStorage,
    playback: PlaybackController,
    library: LibraryRepository,
) {
    val scope = rememberCoroutineScope()
    var summary by remember(storage) { mutableStateOf<ImportedAudioSummary?>(null) }
    var reading by remember(storage) { mutableStateOf(true) }
    var deleting by remember(storage) { mutableStateOf(false) }
    var deleteFailed by remember(storage) { mutableStateOf(false) }

    LaunchedEffect(storage) {
        summary = storage.readImportedAudioSummary()
        reading = false
    }

    Text(
        summary?.let { "File audio impor: ${it.count} file, ${formatSize(it.sizeBytes)}" }
            ?: if (reading) "Menghitung ukuran file audio impor…" else "Ukuran file audio impor tidak tersedia.",
        style = MaterialTheme.typography.body2,
    )
    OutlinedButton(
        enabled = !reading && !deleting,
        onClick = {
            deleting = true
            deleteFailed = false
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    withContext(NonCancellable) {
                        importedAudioLock.withLock {
                            val files = withContext(Dispatchers.IO) { storage.listFiles() }
                            deleteFailed = library.withLocalTrackIds { libraryIds ->
                                // Baca antrean dan hapus tanpa jeda di thread UI, tempat pemutar mengubah antrean.
                                val queueTracks = playback.state.value.queue.entries.map { it.track }
                                val unused = unusedImportedAudioFileNames(files, queueTracks, libraryIds)
                                unused.count { !storage.deleteFile(it) } > 0
                            }
                            summary = withContext(Dispatchers.IO) { summarizeImportedAudio(storage.listFiles()) }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    deleteFailed = true
                    summary = storage.readImportedAudioSummary()
                } finally {
                    deleting = false
                }
            }
        },
    ) { Text(if (deleting) "Menghapus…" else "Hapus file yang tidak dipakai") }
    if (deleteFailed) Text("Sebagian file audio impor tidak dapat dihapus.", style = MaterialTheme.typography.body2)
}

private suspend fun ImportedAudioStorage.readImportedAudioSummary(): ImportedAudioSummary? =
    importedAudioLock.withLock {
        withContext(Dispatchers.IO) {
            try {
                summarizeImportedAudio(listFiles())
            } catch (_: IOException) {
                null
            }
        }
    }

private suspend fun ImageLoader.readArtworkCacheSize(): Long? = storageCacheLock.withLock {
    withContext(Dispatchers.IO) {
        try {
            diskCache?.size ?: 0L
        } catch (_: IOException) {
            null
        }
    }
}
