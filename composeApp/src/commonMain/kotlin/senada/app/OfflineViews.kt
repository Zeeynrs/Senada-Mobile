package senada.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import senada.app.nowplaying.PlaySource
import senada.app.shell.SenadaIcons
import senada.model.Track
import senada.offline.OfflineDownloads
import senada.offline.OfflineState
import senada.offline.OfflineTask
import senada.offline.OfflineTransfer
import senada.offline.canDownloadOffline

@Composable
internal fun OfflineTrackMenuItem(track: Track, downloads: OfflineDownloads?, select: (() -> Unit) -> Unit) {
    if (downloads == null || !canDownloadOffline(track)) return
    val state by downloads.state.collectAsState()
    val transfer = state.tasks[track.id]?.transfer
    val song = state.song(track.id)
    val label = when {
        song != null -> "Hapus unduhan offline"
        transfer is OfflineTransfer.Downloading -> transfer.percent?.let { "Batalkan unduhan ($it%)" } ?: "Batalkan unduhan"
        transfer is OfflineTransfer.Queued -> "Batalkan antrean unduhan"
        transfer is OfflineTransfer.Failed -> "Coba unduh lagi"
        else -> "Unduh untuk offline"
    }
    OverflowActionItem(
        label = label,
        icon = SenadaIcons.Download,
        enabled = state.ready,
        modifier = Modifier.minTouchTarget(),
        onClick = {
            when {
                song != null -> select { downloads.remove(track) }
                transfer is OfflineTransfer.Downloading || transfer is OfflineTransfer.Queued -> downloads.cancel(track)
                else -> downloads.download(track)
            }
        },
    )
}

internal fun LazyListScope.offlineSongsTab(
    state: OfflineState,
    library: LibraryState,
    actions: LibraryActions,
    onOpenSearch: () -> Unit,
) {
    val tracks = state.songs.map { it.track }
    fun play(index: Int = 0) = actions.playAll(tracks, PlaySource.Offline, index, resetShuffle = true)
    item(key = "offline-header") {
        CompactCollectionHeader(
            title = "Lagu offline",
            meta = collectionMeta(trackCount = tracks.size, totalDurationMs = sumKnownDurationMs(tracks)),
            eyebrow = formatSize(state.songs.sumOf { it.sizeBytes }),
        ) {
            CollectionPlayButtons(
                title = "Lagu offline",
                canPlay = tracks.isNotEmpty(),
                onPlay = { play() },
                onShuffle = { actions.playShuffled(tracks, PlaySource.Offline) },
            )
        }
    }
    if (!state.ready) {
        item(key = "offline-loading") { Text("Memuat unduhan…") }
    } else if (tracks.isEmpty() && state.tasks.isEmpty()) {
        item(key = "offline-empty") {
            LibraryEmptyState("Pilih Unduh untuk offline dari menu lagu.", "Cari lagu", onAction = onOpenSearch)
        }
    }
    state.problem?.let { message ->
        item(key = "offline-problem") { Text(message, color = MaterialTheme.colorScheme.error) }
    }
    items(state.tasks.values.toList(), key = { "offline-task-${it.track.id}" }) { task ->
        actions.downloads?.let { OfflineTaskRow(task, it) }
    }
    itemsIndexed(state.songs, key = { _, song -> "offline-song-${song.track.id}" }) { index, song ->
        val track = song.track
        TrackRow(
            track = track.copy(artworkUrl = song.coverUri ?: track.artworkUrl),
            favorite = track.id in library.favoriteIds,
            onClick = { play(index) },
        ) { TrackMenu(track, library, actions) }
    }
}

@Composable
private fun OfflineTaskRow(task: OfflineTask, downloads: OfflineDownloads) {
    val transfer = task.transfer
    SenadaCard {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(task.track.title, style = MaterialTheme.typography.titleSmall)
            when (transfer) {
                OfflineTransfer.Queued -> Text("Dalam antrean")
                is OfflineTransfer.Failed -> Text(transfer.message, color = MaterialTheme.colorScheme.error)
                is OfflineTransfer.Downloading -> {
                    val percent = transfer.percent
                    Text(when (percent) {
                        null -> "Mengunduh…"
                        100 -> "Menyimpan sampul dan lirik…"
                        else -> "Mengunduh $percent%"
                    })
                    if (percent == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
            if (transfer is OfflineTransfer.Failed) {
                TextButton(onClick = { downloads.download(task.track) }) { Text("Coba lagi") }
            }
            TextButton(onClick = { downloads.cancel(task.track) }) {
                Text(if (transfer is OfflineTransfer.Failed) "Tutup" else "Batalkan")
            }
        }
    }
}
