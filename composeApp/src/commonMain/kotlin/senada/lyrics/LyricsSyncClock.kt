package senada.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import senada.playback.PlaybackController
import senada.playback.SampleAudioState
import senada.playback.SampleAudioStatus

internal class LyricsPositionFilter {
    private var lastDiscontinuity = Long.MIN_VALUE
    private var lastSelectionKey: Any? = null
    private var lastStatus: SampleAudioStatus? = null
    private var lastDisplayedMs: Long = 0L

    fun resolve(audio: SampleAudioState, monotonicMs: Long): Long {
        val raw = audio.positionAt(monotonicMs)
        val selectionKey = audio.selection?.queueEntryId ?: audio.selection?.uri
        val reset = audio.discontinuityVersion != lastDiscontinuity ||
            selectionKey != lastSelectionKey ||
            audio.status != SampleAudioStatus.Playing ||
            lastStatus != SampleAudioStatus.Playing
        lastDiscontinuity = audio.discontinuityVersion
        lastSelectionKey = selectionKey
        lastStatus = audio.status
        if (reset) {
            lastDisplayedMs = raw
            return raw
        }
        // Menahan jitter kecil dari timestamp audio hardware (< 40 ms) agar sorotan tidak berkedip mundur.
        val filtered = if (raw < lastDisplayedMs && lastDisplayedMs - raw <= BACKWARD_JITTER_GUARD_MS) {
            lastDisplayedMs
        } else {
            raw
        }
        lastDisplayedMs = filtered
        return filtered
    }

    private companion object {
        const val BACKWARD_JITTER_GUARD_MS = 40L
    }
}

internal fun nextCueWaitWallMs(
    lines: List<LyricLine>,
    currentIndex: Int,
    positionMs: Long,
    playbackSpeed: Float,
): Long? {
    val nextStart = lines.getOrNull(currentIndex + 1)?.startMs ?: return null
    val remainingAudioMs = (nextStart - positionMs).coerceAtLeast(1L)
    val rate = playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
    return ceil(remainingAudioMs.toDouble() / rate.toDouble()).toLong().coerceAtLeast(1L)
}

@Composable
internal fun rememberSyncedPlaybackPositionMs(playback: PlaybackController): State<Long> {
    val audio by remember(playback) {
        playback.state.map { it.audio }.distinctUntilChanged()
    }.collectAsState(playback.state.value.audio)
    val filter = remember(playback) { LyricsPositionFilter() }
    val positionMs = remember(playback) {
        mutableLongStateOf(filter.resolve(audio, playback.monotonicTimeMs()))
    }
    LaunchedEffect(
        playback,
        audio.selection?.queueEntryId,
        audio.selection?.uri,
        audio.status,
        audio.positionMs,
        audio.sampledAtMs,
        audio.playbackSpeed,
        audio.discontinuityVersion,
    ) {
        positionMs.longValue = filter.resolve(audio, playback.monotonicTimeMs())
        if (audio.status == SampleAudioStatus.Playing && audio.selection?.isPending != true) {
            while (isActive) {
                withFrameNanos {
                    positionMs.longValue = filter.resolve(audio, playback.monotonicTimeMs())
                }
            }
        }
    }
    return positionMs
}

@Composable
internal fun rememberActiveLyricIndex(lines: List<LyricLine>, playback: PlaybackController): Int {
    val audio by remember(playback) {
        playback.state.map { it.audio }.distinctUntilChanged()
    }.collectAsState(playback.state.value.audio)
    val filter = remember(playback) { LyricsPositionFilter() }
    val activeIndex = remember(lines, playback) {
        mutableIntStateOf(activeLineIndex(lines, filter.resolve(audio, playback.monotonicTimeMs())))
    }
    LaunchedEffect(
        lines,
        playback,
        audio.selection?.queueEntryId,
        audio.selection?.uri,
        audio.status,
        audio.positionMs,
        audio.sampledAtMs,
        audio.playbackSpeed,
        audio.discontinuityVersion,
    ) {
        var pos = filter.resolve(audio, playback.monotonicTimeMs())
        var idx = activeLineIndex(lines, pos)
        activeIndex.intValue = idx
        if (audio.status == SampleAudioStatus.Playing && audio.selection?.isPending != true) {
            while (isActive) {
                val waitWallMs = nextCueWaitWallMs(lines, idx, pos, audio.playbackSpeed) ?: break
                delay(waitWallMs)
                pos = filter.resolve(audio, playback.monotonicTimeMs())
                idx = activeLineIndex(lines, pos)
                activeIndex.intValue = idx
            }
        }
    }
    return activeIndex.intValue
}
