package senada.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import senada.model.Track

internal data class PlaybackState(
    val queue: Queue = Queue(),
    val audio: SampleAudioState = SampleAudioState(),
) {
    val currentEntry: QueueItem?
        get() = audio.selection?.queueEntryId?.let { id -> queue.entries.firstOrNull { it.id == id } }

    val canGoNext: Boolean
        get() = queue.adjacentTo(audio.selection?.queueEntryId, 1) != null
}

/**
 * Pemilik antrean dan pemutaran untuk seluruh proses aplikasi. UI, notifikasi Android dan
 * kontrol layar kunci iOS memanggil objek yang sama, sehingga audio tidak bergantung pada layar.
 */
internal class PlaybackController(
    private val output: SampleAudioOutput,
    private val scope: CoroutineScope,
    private val tickMs: Long = 250,
    private val selectionFor: (QueueItem) -> SampleAudioSelection,
) {
    private val player = SampleAudioPlayer(output)
    private var queue = Queue()
    private val mutableState = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    private val listeners = mutableListOf<(PlaybackState) -> Unit>()
    private var lastAnnounced: PlaybackState? = null
    private var announceNextPublish = false
    private var uiVisible = false
    private var ticker: Job? = null
    private var resumeAfterInterruption = false

    init {
        // Event bisa datang di tengah panggilan ke pemutar native; tangani setelah panggilan selesai.
        output.onEvent = { scope.launch { sync() } }
    }

    /** Listener menerima perubahan lagu, status, durasi dan hasil seek, bukan setiap tick posisi. */
    fun addListener(listener: (PlaybackState) -> Unit) {
        listeners += listener
        listener(mutableState.value)
    }

    fun add(track: Track) {
        queue = queue.append(track)
        publish()
    }

    fun remove(entryId: Long) {
        queue = queue.remove(entryId)
        player.onQueueEntryRemoved(entryId)
        publish()
    }

    fun play(selection: SampleAudioSelection) {
        player.play(selection)
        publish()
    }

    fun playEntry(entryId: Long) {
        val entry = queue.entries.firstOrNull { it.id == entryId } ?: return
        play(selectionFor(entry))
    }

    /** Melanjutkan lagu terpilih, atau memulai entri pertama bila belum ada yang dipilih. */
    fun resume() {
        val selection = player.state.selection
        if (selection != null) {
            play(selection)
        } else {
            queue.entries.firstOrNull()?.let { play(selectionFor(it)) }
        }
    }

    fun pause() {
        resumeAfterInterruption = false
        player.pause()
        publish()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        announceNextPublish = true
        publish()
    }

    fun next() {
        val nextEntry = queue.adjacentTo(player.state.selection?.queueEntryId, 1) ?: return
        play(selectionFor(nextEntry))
    }

    /** Seperti pemutar musik umumnya: setelah 3 detik, kembali ke awal lagu yang sama. */
    fun previous() {
        player.refresh()
        val audio = player.state
        val previousEntry = queue.adjacentTo(audio.selection?.queueEntryId, -1)
        when {
            audio.positionMs > RESTART_THRESHOLD_MS || previousEntry == null -> restartCurrent()
            else -> play(selectionFor(previousEntry))
        }
    }

    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        sync()
    }

    /** iOS: sistem sudah menjeda pemutar karena panggilan masuk atau aplikasi lain. */
    fun onInterruptionBegan() {
        resumeAfterInterruption = resumeAfterInterruption || player.state.isActive
        sync()
    }

    /** Lanjut hanya bila sistem mengizinkan dan audio sedang diputar sebelum interupsi. */
    fun onInterruptionEnded(shouldResume: Boolean) {
        val resumeNow = shouldResume && resumeAfterInterruption
        resumeAfterInterruption = false
        if (resumeNow) resume() else sync()
    }

    private fun restartCurrent() {
        val audio = player.state
        if (audio.canSeek) {
            seekTo(0)
        } else {
            audio.selection?.let(this::play)
        }
    }

    private fun sync() {
        player.refresh()
        player.advanceAfterEnd(queue, selectionFor)
        publish()
    }

    private fun publish() {
        val current = PlaybackState(queue, player.state)
        mutableState.value = current

        val announced = current.copy(audio = current.audio.copy(positionMs = 0))
        if (announceNextPublish || announced != lastAnnounced) {
            announceNextPublish = false
            lastAnnounced = announced
            listeners.forEach { it(current) }
        }
        updateTicker(current.audio)
    }

    /** Posisi hanya dibaca berkala saat layar terlihat atau audio sedang dimuat. */
    private fun updateTicker(audio: SampleAudioState) {
        val needed = audio.isActive && (uiVisible || audio.status == SampleAudioStatus.Loading)
        if (!needed) {
            ticker?.cancel()
            ticker = null
        } else if (ticker == null) {
            ticker = scope.launch {
                while (isActive) {
                    delay(tickMs)
                    sync()
                }
            }
        }
    }

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
