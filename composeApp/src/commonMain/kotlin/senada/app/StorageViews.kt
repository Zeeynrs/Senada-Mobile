package senada.app

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import senada.local.deleteUnusedImportedAudio
import senada.local.importedAudioLock
import senada.local.summarizeImportedAudio
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

    SettingsCard("Penyimpanan") {
        Text(
            cacheSize?.let { "Cache sampul: ${formatSize(it)}" }
                ?: if (readingSize) "Menghitung ukuran cache sampul…" else "Ukuran cache sampul tidak tersedia.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            enabled = !readingSize && !clearing,
            shape = CircleShape,
            modifier = Modifier.minTouchTarget(),
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    var confirmingDelete by remember(storage) { mutableStateOf(false) }
    var deleting by remember(storage) { mutableStateOf(false) }
    var deleteMessage by remember(storage) { mutableStateOf<String?>(null) }

    LaunchedEffect(storage) {
        summary = storage.readImportedAudioSummary()
        reading = false
    }

    fun deleteUnusedFiles() {
        deleting = true
        deleteMessage = null
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withContext(NonCancellable) {
                    val result = deleteUnusedImportedAudio(
                        storage = storage,
                        library = library,
                        queueTracks = { playback.state.value.queue.entries.map { it.track } },
                    )
                    summary = result.summary
                    deleteMessage = when {
                        result.failedCount > 0 -> "Sebagian file audio impor tidak dapat dihapus."
                        result.unusedCount == 0 -> "Tidak ada file yang tidak dipakai."
                        else -> "${result.unusedCount} file yang tidak dipakai dihapus."
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
    }

    Text(
        summary?.let { "File audio impor: ${it.count} file, ${formatSize(it.sizeBytes)}" }
            ?: if (reading) "Menghitung ukuran file audio impor…" else "Ukuran file audio impor tidak tersedia.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        enabled = !reading && !deleting,
        shape = CircleShape,
        modifier = Modifier.minTouchTarget(),
        onClick = { confirmingDelete = true },
    ) { Text(if (deleting) "Menghapus…" else "Hapus file yang tidak dipakai") }
    deleteMessage?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Hapus file yang tidak dipakai?", modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    "File audio impor yang tidak ada di antrean, favorit, playlist, riwayat, atau hitungan putar " +
                        "akan dihapus dari ponsel, termasuk file yang belum pernah diputar. File ini juga hilang " +
                        "dari Library › File impor dan harus diimpor ulang untuk diputar lagi.",
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        deleteUnusedFiles()
                    },
                    modifier = Modifier.minTouchTarget(),
                ) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmingDelete = false },
                    modifier = Modifier.minTouchTarget(),
                ) { Text("Batal") }
            },
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
