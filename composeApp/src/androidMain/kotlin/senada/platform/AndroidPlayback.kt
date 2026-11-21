package senada.platform

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.util.AtomicFile
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import senada.app.createPlaybackController
import senada.playback.PlaybackController
import senada.playback.PlaybackState
import senada.playback.QueueStore
import senada.playback.SampleAudioOutput
import senada.playback.SampleAudioStatus

/** Pemutar dan controller hidup selama proses aplikasi, terpisah dari Activity. */
internal object AndroidPlayback {
    class Components(val controller: PlaybackController, val sessionPlayer: SessionPlayer)

    private var components: Components? = null

    fun get(context: Context): Components = components ?: create(context.applicationContext).also { components = it }

    private fun create(context: Context): Components {
        val output = ExoAudioOutput(context)
        val controller = createPlaybackController(output, AndroidQueueStore(context))
        val sessionPlayer = SessionPlayer(controller) { output.positionMs }
        controller.addListener { sessionPlayer.refresh() }
        return Components(controller, sessionPlayer)
    }
}

private class AndroidQueueStore(context: Context) : QueueStore {
    private val file = AtomicFile(File(context.filesDir, "playback-queue.json"))

    override fun load(): String = file.readFully().decodeToString(throwOnInvalidSequence = true)

    override fun save(text: String) {
        val stream = file.startWrite()
        try {
            stream.write(text.encodeToByteArray())
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
}

private class ExoAudioOutput(context: Context) : SampleAudioOutput {
    private val player = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    override var onEvent: (() -> Unit)? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                onEvent?.invoke()
            }
        })
    }

    override val status: SampleAudioStatus
        get() = when {
            player.playerError != null -> SampleAudioStatus.Failed
            player.playbackState == Player.STATE_ENDED -> SampleAudioStatus.Ended
            player.isPlaying -> SampleAudioStatus.Playing
            !player.playWhenReady && player.playbackState == Player.STATE_READY -> SampleAudioStatus.Paused
            else -> SampleAudioStatus.Loading
        }

    override val positionMs get() = player.currentPosition

    override val durationMs get() = player.duration.takeIf { it > 0 }

    override fun play(uri: String, restart: Boolean, startAtMs: Long) {
        if (restart) {
            player.setMediaItem(MediaItem.fromUri(uri), startAtMs)
            player.prepare()
        }
        player.play()
    }

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun release() = player.release()
}

/**
 * Wajah antrean Senada bagi MediaSession: notifikasi, layar kunci dan tombol Bluetooth.
 * Semua perintah diteruskan ke [PlaybackController]; ExoPlayer tetap menjadi pemutar internal.
 */
@OptIn(UnstableApi::class)
internal class SessionPlayer(
    private val playback: PlaybackController,
    private val positionMs: () -> Long,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    fun refresh() = invalidateState()

    override fun getState(): SimpleBasePlayer.State {
        val state = playback.state.value
        val items = sessionItems(state)
        val builder = SimpleBasePlayer.State.Builder()
            .setAvailableCommands(SESSION_COMMANDS)
            .setPlaylist(items.map { it.toMediaItemData() })
        if (items.isNotEmpty()) {
            builder.setCurrentMediaItemIndex(currentIndex(state, items))
            when {
                // Media3 perlu membandingkan nilai tetap sebelum dan sesudah seek saat dijeda.
                state.audio.status == SampleAudioStatus.Paused || state.audio.selection?.isPending == true ->
                    builder.setContentPositionMs(state.audio.positionMs)
                state.audio.selection == null -> builder.setContentPositionMs(0)
                else -> builder.setContentPositionMs(SimpleBasePlayer.PositionSupplier { positionMs() })
            }
        }

        val (playbackState, playWhenReady) = when (state.audio.status) {
            SampleAudioStatus.Idle -> Player.STATE_IDLE to false
            SampleAudioStatus.Loading -> Player.STATE_BUFFERING to true
            SampleAudioStatus.Playing -> Player.STATE_READY to true
            SampleAudioStatus.Paused -> Player.STATE_READY to false
            SampleAudioStatus.Ended -> Player.STATE_ENDED to false
            SampleAudioStatus.Failed -> Player.STATE_IDLE to false
        }
        builder.setPlaybackState(if (items.isEmpty()) Player.STATE_IDLE else playbackState)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        if (state.audio.status == SampleAudioStatus.Failed) {
            builder.setPlayerError(
                PlaybackException("Audio gagal diputar", null, PlaybackException.ERROR_CODE_UNSPECIFIED),
            )
        }
        return builder.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) playback.resume() else playback.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val state = playback.state.value
        val items = sessionItems(state)
        val target = items.getOrNull(mediaItemIndex)
        when {
            target == null -> Unit
            state.audio.selection == null || mediaItemIndex != currentIndex(state, items) ->
                target.entryId?.let(playback::playEntry)
            positionMs != C.TIME_UNSET -> playback.seekTo(positionMs)
        }
        return Futures.immediateVoidFuture()
    }

    private fun SessionItem.toMediaItemData(): SimpleBasePlayer.MediaItemData {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artists.joinToString().ifEmpty { null })
            // MediaSession mengunduh gambarnya sendiri untuk notifikasi dan layar kunci.
            .setArtworkUri(artworkUrl?.let(Uri::parse))
            .build()
        return SimpleBasePlayer.MediaItemData.Builder(uid)
            .setMediaItem(MediaItem.Builder().setMediaId(uid).setMediaMetadata(metadata).build())
            .setDurationUs(durationMs?.let { it * 1_000 } ?: C.TIME_UNSET)
            .setIsSeekable(durationMs != null)
            .build()
    }
}

private class SessionItem(
    val uid: String,
    val entryId: Long?,
    val title: String,
    val artists: List<String>,
    val durationMs: Long?,
    val artworkUrl: String? = null,
)

private val SESSION_COMMANDS: Player.Commands = Player.Commands.Builder().addAll(
    Player.COMMAND_PLAY_PAUSE,
    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
    Player.COMMAND_SEEK_TO_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_PREVIOUS,
    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_NEXT,
    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
    Player.COMMAND_GET_TIMELINE,
    Player.COMMAND_GET_METADATA,
).build()

/** Antrean menjadi playlist sesi; sampel uji di luar antrean tampil sebagai satu item. */
private fun sessionItems(state: PlaybackState): List<SessionItem> {
    val selection = state.audio.selection
    if (selection != null && selection.queueEntryId == null) {
        return listOf(SessionItem("sample:${selection.source}", null, selection.title, emptyList(), state.audio.durationMs))
    }
    return state.queue.entries.map { entry ->
        val isCurrent = entry.id == selection?.queueEntryId
        SessionItem(
            uid = "entry:${entry.id}",
            entryId = entry.id,
            title = entry.track.title,
            artists = entry.track.artists,
            durationMs = if (isCurrent) state.audio.durationMs ?: entry.track.durationMs else entry.track.durationMs,
            artworkUrl = entry.track.artworkUrl,
        )
    }
}

private fun currentIndex(state: PlaybackState, items: List<SessionItem>): Int {
    val entryId = state.audio.selection?.queueEntryId ?: return 0
    return items.indexOfFirst { it.entryId == entryId }.coerceAtLeast(0)
}
