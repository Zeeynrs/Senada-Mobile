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

internal const val ARTWORK_CACHE_DIRECTORY = "coil3_disk_cache"

internal expect fun artworkCacheDirectory(context: PlatformContext): Path

private val storageCacheLock = Mutex()

@Composable
internal fun StorageSection() {
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
