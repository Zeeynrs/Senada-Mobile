package senada.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import senada.playback.PlaybackController
import senada.playback.SampleAudioState
import senada.playback.SampleAudioStatus

/**
 * The playback position the lyrics draw with.
 *
 * Each player report re-anchors [SampleAudioState.positionAt], so the raw estimate steps by the report's jitter.
 * This clock instead moves on by the time since the last read and eases the gap to the raw estimate away,
 * so the karaoke sweep glides. It never runs backward while playing: backward seeks bump the discontinuity
 * version and restart it.
 */
internal class LyricsPositionFilter {
    private var lastDiscontinuity = Long.MIN_VALUE
    private var lastSelectionKey: Any? = null
    private var lastStatus: SampleAudioStatus? = null
    private var lastReadMs = Long.MIN_VALUE
    private var shownMs = 0.0

    fun resolve(audio: SampleAudioState, monotonicMs: Long): Long {
        val raw = audio.positionAt(monotonicMs)
        val selectionKey = audio.selection?.queueEntryId ?: audio.selection?.uri
        val elapsedMs = monotonicMs - lastReadMs
        val reset = audio.discontinuityVersion != lastDiscontinuity ||
            selectionKey != lastSelectionKey ||
            audio.status != SampleAudioStatus.Playing ||
            lastStatus != SampleAudioStatus.Playing ||
            lastReadMs == Long.MIN_VALUE ||
            elapsedMs !in 0..STALE_READ_MS
        lastDiscontinuity = audio.discontinuityVersion
        lastSelectionKey = selectionKey
        lastStatus = audio.status
        lastReadMs = monotonicMs
        if (reset) {
            shownMs = raw.toDouble()
            return raw
        }
        val rate = audio.playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
        val predicted = shownMs + elapsedMs * rate.toDouble()
        val gap = raw - predicted
        val next = if (gap > SNAP_FORWARD_MS) {
            raw.toDouble()
        } else {
            predicted + gap * (1.0 - exp(-elapsedMs / PULL_TIME_MS))
        }
        shownMs = maxOf(shownMs, next)
        return shownMs.roundToLong()
    }

    private companion object {
        /** A gap larger than this is a skip the player made on its own, so the clock jumps with it. */
        const val SNAP_FORWARD_MS = 250.0

        /** Time constant for easing the gap to the player's position away. */
        const val PULL_TIME_MS = 60.0

        /** Reads further apart than this mean nobody was drawing, so start again from the player. */
        const val STALE_READ_MS = 200L
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
internal fun rememberLyricsFramePosition(playback: PlaybackController): LongState {
    val filter = remember(playback) { LyricsPositionFilter() }
    val positionMs = remember(playback) {
        mutableLongStateOf(filter.resolve(playback.state.value.audio, playback.monotonicTimeMs()))
    }
    LaunchedEffect(playback) {
        var frameLoop: Job? = null
        playback.state.map { it.audio }.distinctUntilChanged().collect {
            val audio = playback.state.value.audio
            positionMs.longValue = filter.resolve(audio, playback.monotonicTimeMs())
            if (audio.status == SampleAudioStatus.Playing && audio.selection?.isPending != true) {
                if (frameLoop == null) frameLoop = launch {
                    while (isActive) {
                        withFrameNanos {
                            positionMs.longValue = filter.resolve(playback.state.value.audio, playback.monotonicTimeMs())
                        }
                    }
                }
            } else {
                frameLoop?.cancel()
                frameLoop = null
            }
        }
    }
    return positionMs
}

@Composable
internal fun rememberActiveLyricIndex(lines: List<LyricLine>, positionMs: LongState): Int {
    val index by remember(lines, positionMs) {
        derivedStateOf { activeLineIndex(lines, positionMs.longValue) }
    }
    return index
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
