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
import senada.resources.Res
import senada.resources.action_cancel
import senada.resources.action_close
import senada.resources.action_download_offline
import senada.resources.action_remove_offline
import senada.resources.action_retry
import senada.resources.offline_cancel_download
import senada.resources.offline_cancel_download_pct
import senada.resources.offline_cancel_queued
import senada.resources.offline_downloading
import senada.resources.offline_downloading_pct
import senada.resources.offline_empty_message
import senada.resources.offline_find_songs
import senada.resources.offline_loading
import senada.resources.offline_queued
import senada.resources.offline_retry_download
import senada.resources.offline_saving_meta
import senada.resources.offline_songs_title

@Composable
internal fun OfflineTrackMenuItem(track: Track, downloads: OfflineDownloads?, select: (() -> Unit) -> Unit) {
    if (downloads == null || !canDownloadOffline(track)) return
    val state by downloads.state.collectAsState()
    val transfer = state.tasks[track.id]?.transfer
    val song = state.song(track.id)
    val label = when {
        song != null -> appString(Res.string.action_remove_offline)
        transfer is OfflineTransfer.Downloading -> transfer.percent?.let {
            appString(Res.string.offline_cancel_download_pct, it)
        } ?: appString(Res.string.offline_cancel_download)
        transfer is OfflineTransfer.Queued -> appString(Res.string.offline_cancel_queued)
        transfer is OfflineTransfer.Failed -> appString(Res.string.offline_retry_download)
        else -> appString(Res.string.action_download_offline)
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
        val language = LocalAppLanguage.current
        val title = appString(Res.string.offline_songs_title)
        CompactCollectionHeader(
            title = title,
            meta = collectionMeta(trackCount = tracks.size, totalDurationMs = sumKnownDurationMs(tracks), language = language),
            eyebrow = formatSize(state.songs.sumOf { it.sizeBytes }, language),
        ) {
            CollectionPlayButtons(
                title = title,
                canPlay = tracks.isNotEmpty(),
                onPlay = { play() },
                onShuffle = { actions.playShuffled(tracks, PlaySource.Offline) },
            )
        }
    }
    if (!state.ready) {
        item(key = "offline-loading") { Text(appString(Res.string.offline_loading)) }
    } else if (tracks.isEmpty() && state.tasks.isEmpty()) {
        item(key = "offline-empty") {
            LibraryEmptyState(
                appString(Res.string.offline_empty_message),
                appString(Res.string.offline_find_songs),
                onAction = onOpenSearch,
            )
        }
    }
    state.problemText?.let { problemText ->
        item(key = "offline-problem") {
            Text(problemText.resolve(LocalAppLanguage.current), color = MaterialTheme.colorScheme.error)
        }
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
    val language = LocalAppLanguage.current
    SenadaCard {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(task.track.title, style = MaterialTheme.typography.titleSmall)
            when (transfer) {
                OfflineTransfer.Queued -> Text(appString(Res.string.offline_queued))
                is OfflineTransfer.Failed -> Text(transfer.message(language), color = MaterialTheme.colorScheme.error)
                is OfflineTransfer.Downloading -> {
                    val percent = transfer.percent
                    Text(when (percent) {
                        null -> appString(Res.string.offline_downloading)
                        100 -> appString(Res.string.offline_saving_meta)
                        else -> appString(Res.string.offline_downloading_pct, percent)
                    })
                    if (percent == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
            if (transfer is OfflineTransfer.Failed) {
                TextButton(onClick = { downloads.download(task.track) }) { Text(appString(Res.string.action_retry)) }
            }
            TextButton(onClick = { downloads.cancel(task.track) }) {
                Text(appString(if (transfer is OfflineTransfer.Failed) Res.string.action_close else Res.string.action_cancel))
            }
        }
    }
}
