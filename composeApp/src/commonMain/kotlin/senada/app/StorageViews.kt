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
import senada.resources.Res
import senada.resources.action_cancel
import senada.resources.action_delete
import senada.resources.storage_clear_cache
import senada.resources.storage_clear_cache_done
import senada.resources.storage_clear_cache_failed
import senada.resources.storage_clearing
import senada.resources.storage_cover_cache_calculating
import senada.resources.storage_cover_cache_size
import senada.resources.storage_cover_cache_unavailable
import senada.resources.storage_delete_unused
import senada.resources.storage_delete_unused_failed
import senada.resources.storage_delete_unused_message
import senada.resources.storage_delete_unused_none
import senada.resources.storage_delete_unused_title
import senada.resources.storage_file_count
import senada.resources.storage_imported_calculating
import senada.resources.storage_imported_summary
import senada.resources.storage_imported_unavailable
import senada.resources.storage_title
import senada.resources.storage_unused_deleted

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
    var clearMessage by remember(imageLoader) { mutableStateOf<UiText?>(null) }
    val language = LocalAppLanguage.current

    LaunchedEffect(imageLoader) {
        cacheSize = imageLoader.readArtworkCacheSize()
        readingSize = false
    }

    SettingsCard(appString(Res.string.storage_title)) {
        Text(
            cacheSize?.let { appString(Res.string.storage_cover_cache_size, formatSize(it)) }
                ?: if (readingSize) {
                    appString(Res.string.storage_cover_cache_calculating)
                } else {
                    appString(Res.string.storage_cover_cache_unavailable)
                },
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
                            UiText.Resource(Res.string.storage_clear_cache_failed)
                        } else {
                            UiText.Resource(Res.string.storage_clear_cache_done)
                        }
                        clearing = false
                    }
                }
            },
        ) { Text(if (clearing) appString(Res.string.storage_clearing) else appString(Res.string.storage_clear_cache)) }
        clearMessage?.let {
            Text(
                it.resolve(language),
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
    var deletedUnusedCount by remember(storage) { mutableStateOf<Int?>(null) }
    val language = LocalAppLanguage.current

    LaunchedEffect(storage) {
        summary = storage.readImportedAudioSummary()
        reading = false
    }

    fun deleteUnusedFiles() {
        deleting = true
        deleteMessage = null
        deletedUnusedCount = null
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withContext(NonCancellable) {
                    val result = deleteUnusedImportedAudio(
                        storage = storage,
                        library = library,
                        queueTracks = { playback.state.value.queue.entries.map { it.track } },
                    )
                    summary = result.summary
                    when {
                        result.failedCount > 0 ->
                            deleteMessage = UiText.Resource(Res.string.storage_delete_unused_failed).resolve(language)
                        result.unusedCount == 0 ->
                            deleteMessage = UiText.Resource(Res.string.storage_delete_unused_none).resolve(language)
                        else -> deletedUnusedCount = result.unusedCount
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                deleteMessage = UiText.Resource(Res.string.storage_delete_unused_failed).resolve(language)
                summary = storage.readImportedAudioSummary()
            } finally {
                deleting = false
            }
        }
    }

    Text(
        summary?.let {
            appString(
                Res.string.storage_imported_summary,
                appPlural(Res.plurals.storage_file_count, it.count, it.count),
                formatSize(it.sizeBytes),
            )
        } ?: if (reading) {
            appString(Res.string.storage_imported_calculating)
        } else {
            appString(Res.string.storage_imported_unavailable)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        enabled = !reading && !deleting,
        shape = CircleShape,
        modifier = Modifier.minTouchTarget(),
        onClick = { confirmingDelete = true },
    ) { Text(if (deleting) appString(Res.string.storage_clearing) else appString(Res.string.storage_delete_unused)) }
    val displayedDeleteMessage = deletedUnusedCount?.let { appPlural(Res.plurals.storage_unused_deleted, it, it) }
        ?: deleteMessage
    displayedDeleteMessage?.let {
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
            title = { Text(appString(Res.string.storage_delete_unused_title), modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    appString(Res.string.storage_delete_unused_message),
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
                ) { Text(appString(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmingDelete = false },
                    modifier = Modifier.minTouchTarget(),
                ) { Text(appString(Res.string.action_cancel)) }
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
