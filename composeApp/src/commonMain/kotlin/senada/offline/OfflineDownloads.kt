package senada.offline

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path
import senada.catalog.CatalogException
import senada.catalog.StreamResolver
import senada.catalog.youtube.resizeGoogleArtwork
import senada.lyrics.LyricsDocument
import senada.model.Track

internal sealed interface OfflineTransfer {
    data object Queued : OfflineTransfer
    data class Downloading(val percent: Int?) : OfflineTransfer
    data class Failed(val message: String) : OfflineTransfer
}

internal data class OfflineTask(val track: Track, val transfer: OfflineTransfer)

internal data class OfflineState(
    val ready: Boolean = false,
    val songs: List<OfflineSong> = emptyList(),
    val tasks: Map<String, OfflineTask> = emptyMap(),
    val problem: String? = null,
) {
    fun song(id: String): OfflineSong? = songs.firstOrNull { it.track.id == id }
}

/** One process-wide queue, independent of the open screen. Only the active transfer writes files. */
internal class OfflineDownloads(
    private val store: OfflineStore,
    private val resolver: StreamResolver,
    private val http: HttpClient,
    private val readLyrics: suspend (Track) -> LyricsDocument,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(OfflineState())
    val state = mutableState.asStateFlow()
    private val pending = ArrayDeque<Track>()
    private var worker: Job? = null
    private var active: Pair<String, Job>? = null
    private val removing = mutableSetOf<String>()
    private val loading = scope.launch {
        val songs = withContext(Dispatchers.IO) { store.list() }
        mutableState.update { it.copy(ready = true, songs = songs) }
    }

    suspend fun song(track: Track): OfflineSong? = withContext(Dispatchers.IO) { store.find(track) }

    suspend fun lyrics(track: Track): LyricsDocument? = withContext(Dispatchers.IO) { store.lyrics(track) }

    fun download(track: Track) {
        val snapshot = state.value
        if (!canDownloadOffline(track) || snapshot.song(track.id) != null) return
        val task = snapshot.tasks[track.id]
        if (task != null && task.transfer !is OfflineTransfer.Failed) return
        pending.addLast(track)
        updateTask(track, OfflineTransfer.Queued)
        if (worker?.isActive != true) worker = scope.launch { processQueue() }
    }

    fun cancel(track: Track) {
        val job = active?.takeIf { it.first == track.id }?.second
        if (job != null) job.cancel()
        else {
            pending.removeAll { it.id == track.id }
            updateTask(track, null)
        }
    }

    fun remove(track: Track) {
        if (state.value.song(track.id) == null || !removing.add(track.id)) return
        scope.launch {
            try {
                withContext(Dispatchers.IO) { store.remove(track) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(problem = "Salinan lagu tidak dapat dihapus sepenuhnya.") }
            } finally {
                refreshSong(track)
                removing.remove(track.id)
            }
        }
    }

    private suspend fun processQueue() = coroutineScope {
        loading.join()
        while (pending.isNotEmpty()) {
            val track = pending.removeFirst()
            val job = launch { transfer(track) }
            active = track.id to job
            job.join()
            refreshSong(track)
            if (state.value.tasks[track.id]?.transfer !is OfflineTransfer.Failed) updateTask(track, null)
            active = null
        }
    }

    private suspend fun transfer(track: Track) {
        if (state.value.song(track.id) != null) return
        try {
            updateTask(track, OfflineTransfer.Downloading(null))
            val audio = resolver.resolve(track)
            withContext(Dispatchers.IO) {
                store.save(
                    track, audio,
                    writeAudio = { target ->
                        fetchOfflineFile(http, audio.location, target, audio.headers) { percent ->
                            updateTask(track, OfflineTransfer.Downloading(percent))
                        }
                    },
                    writeCover = { target ->
                        track.artworkUrl?.takeIf(String::isNotBlank)?.let { url ->
                            optional {
                                try {
                                    fetchOfflineFile(http, resizeGoogleArtwork(url, 700), target, cover = true)
                                } catch (e: Exception) {
                                    runCatching { FileSystem.SYSTEM.delete(target) }
                                    throw e
                                }
                            }
                        }
                    },
                    readLyrics = { withTimeoutOrNull(15_000) { optional { readLyrics(track) } } },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateTask(track, OfflineTransfer.Failed((e as? CatalogException)?.userMessage ?: "Unduhan gagal. Coba lagi."))
        }
    }

    private suspend fun refreshSong(track: Track) {
        val song = song(track)
        mutableState.update { snapshot ->
            val others = snapshot.songs.filterNot { it.track.id == track.id }
            snapshot.copy(songs = if (song == null) others else (others + song).sortedByDescending { it.downloadedAtMs })
        }
    }

    private fun updateTask(track: Track, transfer: OfflineTransfer?) {
        mutableState.update { snapshot ->
            val tasks = if (transfer == null) snapshot.tasks - track.id else snapshot.tasks + (track.id to OfflineTask(track, transfer))
            snapshot.copy(tasks = tasks)
        }
    }

    private suspend fun <T> optional(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

internal suspend fun fetchOfflineFile(
    http: HttpClient,
    url: String,
    target: Path,
    headers: Map<String, String> = emptyMap(),
    cover: Boolean = false,
    progress: (Int?) -> Unit = {},
) {
    check(Url(url).protocol == URLProtocol.HTTPS)
    val limit = if (cover) 8L * 1024 * 1024 else Long.MAX_VALUE
    http.prepareGet(url) {
        headers.forEach { (key, value) -> header(key, value) }
        timeout { requestTimeoutMillis = if (cover) 15_000 else 900_000 }
    }.execute { response ->
        check(response.status == HttpStatusCode.OK)
        val mime = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()?.lowercase()
        check(if (cover) mime?.startsWith("image/") == true else mime == null || mime == "audio/mp4" || mime == "application/octet-stream")
        val length = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.takeIf { it > 0L }
        check(length == null || length <= limit)
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(64 * 1024)
        var received = 0L
        var lastPercent: Int? = null
        FileSystem.SYSTEM.write(target) {
            while (true) {
                val count = channel.readAvailable(buffer, 0, buffer.size)
                if (count == -1) break
                received += count
                check(received <= limit && (length == null || received <= length))
                write(buffer, 0, count)
                val percent = length?.let { ((received * 100) / it).toInt() }
                if (percent != lastPercent) {
                    progress(percent)
                    lastPercent = percent
                }
            }
        }
        check(received > 0L && (length == null || received == length))
    }
}
