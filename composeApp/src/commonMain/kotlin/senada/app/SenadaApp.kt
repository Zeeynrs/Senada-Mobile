package senada.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import senada.app.shell.AppShell
import senada.audio.AudioEffectsController
import senada.library.LibraryRepository
import senada.playback.PlaybackController

/** Titik masuk layar: tema Senada, pemuat gambar bersama, lalu kerangka aplikasi. */
@Composable
internal fun SenadaApp(
    playback: PlaybackController,
    library: LibraryRepository,
    audioEffects: AudioEffectsController? = null,
) {
    // Sampul diunduh dengan klien HTTP yang sama dengan katalog.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(SenadaSources.http)) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.10).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(artworkCacheDirectory(context))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .build()
    }
    val artworkUrl by remember(playback) {
        playback.state.map { it.currentEntry?.track?.artworkUrl }.distinctUntilChanged()
    }.collectAsState(playback.state.value.currentEntry?.track?.artworkUrl)
    var screenVisible by remember { mutableStateOf(false) }

    // Audio tetap berjalan di latar belakang; layar hanya mengatur kapan posisi perlu dibaca berkala.
    LifecycleStartEffect(playback) {
        screenVisible = true
        playback.setUiVisible(true)
        onStopOrDispose {
            screenVisible = false
            playback.setUiVisible(false)
        }
    }

    SenadaTheme(artworkUrl, visible = screenVisible) { appearance ->
        AppShell(playback, library, appearance, screenVisible, audioEffects)
    }
}
