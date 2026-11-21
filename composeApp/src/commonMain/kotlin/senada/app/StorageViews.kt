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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    var clearMessage by remember(imageLoader) { mutableStateOf<String?>(null) }

    LaunchedEffect(imageLoader) {
        cacheSize = imageLoader.readArtworkCacheSize()
        readingSize = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Penyimpanan", style = MaterialTheme.typography.h6, modifier = Modifier.semantics { heading() })
        Text(
            cacheSize?.let { "Cache sampul: ${formatSize(it)}" }
                ?: if (readingSize) "Menghitung ukuran cache sampul…" else "Ukuran cache sampul tidak tersedia.",
            style = MaterialTheme.typography.body2,
        )
        OutlinedButton(
            enabled = !readingSize && !clearing,
            onClick = {
                clearing = true
                clearMessage = null
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    var failed = false
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
                        failed = true
                    } finally {
                        cacheSize = imageLoader.readArtworkCacheSize()
                        clearMessage = if (failed) {
                            "Cache sampul tidak dapat dihapus sepenuhnya."
                        } else {
                            "Cache sampul dan lirik dihapus."
                        }
                        clearing = false
                    }
                }
            },
        ) { Text(if (clearing) "Menghapus…" else "Hapus cache") }
        clearMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
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
    var deleteMessage by remember(storage) { mutableStateOf<String?>(null) }

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
            deleteMessage = null
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    withContext(NonCancellable) {
                        importedAudioLock.withLock {
                            val files = withContext(Dispatchers.IO) { storage.listFiles() }
                            val result = library.withLocalTrackIds { libraryIds ->
                                // Baca antrean dan hapus tanpa jeda di thread UI, tempat pemutar mengubah antrean.
                                val queueTracks = playback.state.value.queue.entries.map { it.track }
                                val unused = unusedImportedAudioFileNames(files, queueTracks, libraryIds)
                                val failed = unused.count { !storage.deleteFile(it) }
                                unused.size to failed
                            }
                            summary = withContext(Dispatchers.IO) { summarizeImportedAudio(storage.listFiles()) }
                            val (unusedCount, failedCount) = result
                            deleteMessage = when {
                                failedCount > 0 -> "Sebagian file audio impor tidak dapat dihapus."
                                unusedCount == 0 -> "Tidak ada file yang tidak dipakai."
                                else -> "$unusedCount file yang tidak dipakai dihapus."
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    deleteMessage = "Sebagian file audio impor tidak dapat dihapus."
                    summary = storage.readImportedAudioSummary()
                } finally {
                    deleting = false
                }
            }
        },
    ) { Text(if (deleting) "Menghapus…" else "Hapus file yang tidak dipakai") }
    deleteMessage?.let {
        Text(
            it,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
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
