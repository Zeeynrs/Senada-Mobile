package senada.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import coil3.PlatformContext
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path
import senada.offline.OfflineDownloads
import senada.offline.OfflineStore

internal expect fun offlineDownloads(context: PlatformContext): OfflineDownloads

internal fun createOfflineDownloads(root: Path, fileUri: (Path) -> String): OfflineDownloads = OfflineDownloads(
    OfflineStore(root, FileSystem.SYSTEM, fileUri), SenadaSources.streams, SenadaSources.http,
    { track ->
        val lyrics = SenadaSources.lyrics
        withTimeoutOrNull(15_000) { lyrics.get(track) }?.let { lyrics.withRomanization(track, it) }
    },
    MainScope(),
)

@Composable
internal fun rememberOfflineArtwork(trackId: String?, downloads: OfflineDownloads?): String? {
    if (trackId == null || downloads == null) return null
    return remember(downloads, trackId) {
        downloads.state.map { it.song(trackId)?.coverUri }.distinctUntilChanged()
    }.collectAsState(downloads.state.value.song(trackId)?.coverUri).value
}
