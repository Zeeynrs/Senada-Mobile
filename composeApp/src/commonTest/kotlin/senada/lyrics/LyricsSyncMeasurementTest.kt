package senada.lyrics

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import senada.model.Track
import senada.playback.PlaybackController
import senada.playback.SampleAudioOutput
import senada.playback.SampleAudioSelection
import senada.playback.SampleAudioSource
import senada.playback.SampleAudioStatus

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsSyncMeasurementTest {
    private class ClockDrivenAudioOutput(private val wallNowMs: () -> Long) : SampleAudioOutput {
        private var anchorWallMs: Long = 0L
        private var anchorPosMs: Long = 0L
        private var discontinuityPending = false

        override var playbackRate: Float = 1.0f
            set(value) {
                commitPosition()
                field = value
            }

        override var status: SampleAudioStatus = SampleAudioStatus.Idle
            private set
        override var durationMs: Long? = 12_000L
        override var volume: Float = 1f
        override var onEvent: (() -> Unit)? = null

        override val positionMs: Long
            get() = if (status == SampleAudioStatus.Playing) {
                val elapsedWall = (wallNowMs() - anchorWallMs).coerceAtLeast(0L)
                val advanced = (elapsedWall * playbackRate.toDouble()).roundToLong()
                (anchorPosMs + advanced).coerceIn(0L, durationMs ?: Long.MAX_VALUE)
            } else {
                anchorPosMs
            }

        private fun commitPosition() {
            anchorPosMs = positionMs
            anchorWallMs = wallNowMs()
        }

        override fun consumeDiscontinuity(): Boolean {
            val had = discontinuityPending
            discontinuityPending = false
            return had
        }

        fun setBuffering(buffering: Boolean) {
            commitPosition()
            status = if (buffering) SampleAudioStatus.Loading else SampleAudioStatus.Playing
            onEvent?.invoke()
        }

        fun emitNativeSeek(targetMs: Long) {
            anchorPosMs = targetMs.coerceIn(0L, durationMs ?: Long.MAX_VALUE)
            anchorWallMs = wallNowMs()
            discontinuityPending = true
            onEvent?.invoke()
        }

        fun emitEnded() {
            commitPosition()
            status = SampleAudioStatus.Ended
            onEvent?.invoke()
        }

        fun injectRawPosition(posMs: Long) {
            anchorPosMs = posMs.coerceIn(0L, durationMs ?: Long.MAX_VALUE)
            anchorWallMs = wallNowMs()
        }

        override fun play(uri: String, restart: Boolean, startAtMs: Long, endAtMs: Long?) {
            if (restart) {
                anchorPosMs = startAtMs
                discontinuityPending = true
            } else {
                commitPosition()
            }
            anchorWallMs = wallNowMs()
            status = SampleAudioStatus.Playing
            onEvent?.invoke()
        }

        override fun pause() {
            commitPosition()
            status = SampleAudioStatus.Paused
            onEvent?.invoke()
        }

        override fun seekTo(positionMs: Long) {
            anchorPosMs = positionMs.coerceIn(0L, durationMs ?: Long.MAX_VALUE)
            anchorWallMs = wallNowMs()
            discontinuityPending = true
            onEvent?.invoke()
        }

        override fun release() = Unit
    }

    private data class ErrorStats(
        val samples: Int,
        val meanSignedErrorMs: Double,
        val minSignedErrorMs: Long,
        val maxSignedErrorMs: Long,
        val maxAbsErrorMs: Long,
    )

    private fun summarize(errors: List<Long>): ErrorStats {
        if (errors.isEmpty()) return ErrorStats(0, 0.0, 0L, 0L, 0L)
        return ErrorStats(
            samples = errors.size,
            meanSignedErrorMs = errors.average(),
            minSignedErrorMs = errors.min(),
            maxSignedErrorMs = errors.max(),
            maxAbsErrorMs = errors.maxOf { abs(it) },
        )
    }

    private fun fixtureLines(): List<LyricLine> = LrcParser.parse(
        """
            [00:00.80]Langit mulai reda
            [00:02.15]Langkah di halaman
            [00:03.90]Sisa hujan turun
            [00:06.10]Pagi pun datang
            [00:08.50]Cahaya hangat
            [00:10.40]Tenang kembali
        """.trimIndent(),
    )

    private fun wordTimedFixtureLines(): List<LyricLine> = LrcParser.parse(
        """
            [00:00.80]<00:00.80>Langit <00:01.20>mulai <00:01.60>reda<00:02.00>
            [00:02.15]<00:02.15>Langkah <00:02.70>di <00:03.00>halaman<00:03.75>
        """.trimIndent(),
    )

    @Test
    fun afterFixSyncErrorAcrossSpeedsIsWithinOneMillisecond() = runTest {
        val lines = fixtureLines()
        val timings = lines.mapIndexed { index, line -> karaokeTiming(line, lines.getOrNull(index + 1)?.startMs) }
        val track = Track("demo:track:hujan", "Selepas Hujan", listOf("Senada"), 12_000L, source = "demo")

        for (speed in listOf(0.5f, 1.0f, 2.0f)) {
            val output = ClockDrivenAudioOutput { testScheduler.currentTime }
            output.playbackRate = speed
            val controller = PlaybackController(
                output, backgroundScope,
                monotonicNow = { testScheduler.currentTime },
            ) { entry ->
                SampleAudioSelection(entry.track.title, SampleAudioSource.Local, "file:///hujan.m4a", entry.id, durationMs = 12_000L)
            }
            controller.add(track)
            controller.setUiVisible(true)
            controller.resume()
            runCurrent()

            val filter = LyricsPositionFilter()
            val posErrors = mutableListOf<Long>()
            val karaokeErrors = mutableListOf<Long>()
            val cueLatencies = mutableListOf<Long>()
            var lastShownActive = -1
            val seenCues = mutableSetOf<Int>()

            val totalWallMs = (9_500.0 / speed).roundToLong()
            var elapsed = 0L
            while (elapsed < totalWallMs) {
                // Step frame by frame (16 ms) and also evaluate exact cue crossings.
                advanceTimeBy(16L)
                runCurrent()
                elapsed += 16L

                val wallNow = testScheduler.currentTime
                val trueAudioPos = output.positionMs
                val syncedPos = filter.resolve(controller.state.value.audio, wallNow)
                posErrors += (syncedPos - trueAudioPos)

                val shownActive = activeLineIndex(lines, syncedPos)
                if (shownActive != lastShownActive && shownActive >= 0 && seenCues.add(shownActive)) {
                    cueLatencies += (trueAudioPos - lines[shownActive].startMs)
                }
                lastShownActive = shownActive
                if (shownActive >= 0) {
                    val words = timings[shownActive].words
                    val renderedFraction = words.sumOf { it.graphemesAt(syncedPos) } / words.sumOf { it.graphemeCount }
                    val start = words.first().startMs
                    val effectiveKaraokePos = start + (renderedFraction * (words.last().endMs - start)).roundToLong()
                    karaokeErrors += (effectiveKaraokePos - trueAudioPos)
                }
                if (activeLineIndex(lines, trueAudioPos) >= lines.lastIndex) break
            }

            val stats = summarize(posErrors)
            val karaokeStats = summarize(karaokeErrors)
            val cueStats = summarize(cueLatencies)
            println(
                "AFTER_FIX speed=${speed}x | posErr(mean=${stats.meanSignedErrorMs}ms, min=${stats.minSignedErrorMs}ms, max=${stats.maxSignedErrorMs}ms) " +
                    "| cueLatency(mean=${cueStats.meanSignedErrorMs}ms, max=${cueStats.maxSignedErrorMs}ms) " +
                    "| karaokeErr(mean=${karaokeStats.meanSignedErrorMs.toInt()}ms, min=${karaokeStats.minSignedErrorMs}ms, max=${karaokeStats.maxSignedErrorMs}ms)"
            )
            assertTrue(stats.maxAbsErrorMs <= 1L, "Expected <=1ms pos error at ${speed}x, got ${stats.maxAbsErrorMs}ms")
            assertTrue(cueStats.maxAbsErrorMs <= 32L, "Expected frame-granularity cue latency at ${speed}x, got ${cueStats.maxAbsErrorMs}ms")
            assertTrue(karaokeStats.maxAbsErrorMs <= 2L, "Expected smooth karaoke within 2ms, got ${karaokeStats.maxAbsErrorMs}ms")
        }
    }

    @Test
    fun cueBoundariesBeforeExactAndAfterMatchAudioAtAllSpeeds() = runTest {
        val lines = fixtureLines()
        val track = Track("demo:track:hujan", "Selepas Hujan", listOf("Senada"), 12_000L, source = "demo")

        for (speed in listOf(0.5f, 1.0f, 2.0f)) {
            val output = ClockDrivenAudioOutput { testScheduler.currentTime }
            output.playbackRate = speed
            val controller = PlaybackController(
                output, backgroundScope,
                monotonicNow = { testScheduler.currentTime },
            ) { entry ->
                SampleAudioSelection(entry.track.title, SampleAudioSource.Local, "file:///hujan.m4a", entry.id, durationMs = 12_000L)
            }
            controller.add(track)
            controller.setUiVisible(true)
            controller.resume()
            runCurrent()

            val filter = LyricsPositionFilter()
            // Cue 0 is at 800 ms, Cue 1 is at 2150 ms.
            val cueMs = lines[1].startMs // 2150 ms
            val beforeWall = ((cueMs - 1L) / speed.toDouble()).toLong()
            advanceTimeBy(beforeWall)
            runCurrent()
            val posBefore = filter.resolve(controller.state.value.audio, testScheduler.currentTime)
            if (output.positionMs < cueMs) {
                assertEquals(0, activeLineIndex(lines, posBefore), "Before cue at ${speed}x")
            }

            // Advance using nextCueWaitWallMs directly to the exact cue boundary.
            val waitWall = nextCueWaitWallMs(lines, 0, posBefore, controller.state.value.audio.playbackSpeed)
            if (waitWall != null) {
                advanceTimeBy(waitWall)
                runCurrent()
            }
            val posAtCue = filter.resolve(controller.state.value.audio, testScheduler.currentTime)
            assertTrue(posAtCue >= cueMs, "Expected >= $cueMs at ${speed}x, got $posAtCue")
            assertEquals(1, activeLineIndex(lines, posAtCue), "At cue boundary at ${speed}x")

            advanceTimeBy((120.0 / speed).roundToLong())
            runCurrent()
            val posAfter = filter.resolve(controller.state.value.audio, testScheduler.currentTime)
            assertEquals(1, activeLineIndex(lines, posAfter), "After cue at ${speed}x")
            assertTrue(karaokeTiming(lines[1], lines[2].startMs).words.first().graphemesAt(posAfter) > 0)
        }
    }

    @Test
    fun bidirectionalSeekPauseBufferingResumeAndDynamicSpeedChange() = runTest {
        val lines = wordTimedFixtureLines()
        val track = Track("yt:track:stream", "Lagu Stream", listOf("Senada"), 12_000L, source = "ytmusic")
        val output = ClockDrivenAudioOutput { testScheduler.currentTime }
        val controller = PlaybackController(
            output, backgroundScope,
            monotonicNow = { testScheduler.currentTime },
        ) { entry ->
            SampleAudioSelection(entry.track.title, SampleAudioSource.YouTubeMusic, "https://audio/${entry.id}", entry.id, durationMs = 12_000L)
        }
        controller.add(track)
        controller.setUiVisible(true)
        controller.resume()
        runCurrent()

        val filter = LyricsPositionFilter()

        // Play for 900 ms at 1x -> inside word 1 ("Langit ", 800..1200)
        advanceTimeBy(900L)
        runCurrent()
        assertEquals(900L, filter.resolve(controller.state.value.audio, testScheduler.currentTime))

        // Pause -> advance wall time 3000 ms -> position and karaoke MUST NOT advance
        controller.pause()
        runCurrent()
        val pausedPos = filter.resolve(controller.state.value.audio, testScheduler.currentTime)
        val firstWord = karaokeTiming(lines[0], lines[1].startMs).words.first()
        val pausedSung = firstWord.graphemesAt(pausedPos)
        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(pausedPos, filter.resolve(controller.state.value.audio, testScheduler.currentTime))
        assertEquals(pausedSung, firstWord.graphemesAt(filter.resolve(controller.state.value.audio, testScheduler.currentTime)))

        // Resume -> advance 350 ms -> 1250 ms (inside word 2 "mulai ")
        controller.resume()
        runCurrent()
        advanceTimeBy(350L)
        runCurrent()
        assertEquals(1_250L, filter.resolve(controller.state.value.audio, testScheduler.currentTime))

        // Buffering stall -> advance wall time 2000 ms -> position MUST stay frozen at 1250 ms
        output.setBuffering(true)
        runCurrent()
        assertEquals(SampleAudioStatus.Loading, controller.state.value.audio.status)
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(1_250L, filter.resolve(controller.state.value.audio, testScheduler.currentTime))

        // Resume from buffering -> change speed to 2.0x -> advance 500 ms wall -> 1000 ms audio (2250 ms)
        output.setBuffering(false)
        runCurrent()
        output.playbackRate = 2.0f
        output.onEvent?.invoke()
        runCurrent()
        advanceTimeBy(500L)
        runCurrent()
        val posAfter2x = filter.resolve(controller.state.value.audio, testScheduler.currentTime)
        assertEquals(2_250L, posAfter2x)
        assertEquals(1, activeLineIndex(lines, posAfter2x))

        // Seek forward -> 3200 ms (snaps immediately, discontinuityVersion increments)
        val beforeSeekDisc = controller.state.value.audio.discontinuityVersion
        controller.seekTo(3_200L)
        runCurrent()
        assertTrue(controller.state.value.audio.discontinuityVersion > beforeSeekDisc)
        assertEquals(3_200L, filter.resolve(controller.state.value.audio, testScheduler.currentTime))
        assertEquals(1, activeLineIndex(lines, 3_200L))

        // Seek backward even within 30ms or across lines -> 850 ms (must NOT be suppressed by jitter guard!)
        val afterForwardDisc = controller.state.value.audio.discontinuityVersion
        controller.seekTo(850L)
        runCurrent()
        assertTrue(controller.state.value.audio.discontinuityVersion > afterForwardDisc)
        assertEquals(850L, filter.resolve(controller.state.value.audio, testScheduler.currentTime))
        assertEquals(0, activeLineIndex(lines, 850L))

        // Native external seek (e.g. lockscreen scrub) -> 2900 ms
        output.emitNativeSeek(2_900L)
        runCurrent()
        assertEquals(2_900L, filter.resolve(controller.state.value.audio, testScheduler.currentTime))
        assertEquals(1, activeLineIndex(lines, 2_900L))
    }

    @Test
    fun trackSwitchAndOfflinePlaybackResetAndPreserveWordSync() = runTest {
        val first = Track("yt:track:offline", "Offline Song", listOf("Senada"), 12_000L, source = "ytmusic")
        val second = Track("yt:track:stream2", "Next Song", listOf("Senada"), 12_000L, source = "ytmusic")
        val output = ClockDrivenAudioOutput { testScheduler.currentTime }
        val controller = PlaybackController(
            output, backgroundScope,
            monotonicNow = { testScheduler.currentTime },
            isDownloaded = { it == first },
            offlineSelectionFor = { entry ->
                if (entry.track == first) {
                    SampleAudioSelection(entry.track.title, SampleAudioSource.Offline, "file:///offline.m4a", entry.id, durationMs = 12_000L)
                } else null
            },
        ) { entry ->
            SampleAudioSelection(entry.track.title, SampleAudioSource.YouTubeMusic, "https://stream/${entry.id}", entry.id, durationMs = 12_000L)
        }
        controller.add(first)
        controller.add(second)
        controller.setUiVisible(true)
        controller.resume()
        runCurrent()

        val filter = LyricsPositionFilter()
        assertEquals(SampleAudioSource.Offline, controller.state.value.audio.selection?.source)

        advanceTimeBy(2_500L)
        runCurrent()
        assertEquals(2_500L, filter.resolve(controller.state.value.audio, testScheduler.currentTime))

        // Switch to next streaming track -> position resets cleanly to 0 ms and discontinuity bumps
        val discBeforeSwitch = controller.state.value.audio.discontinuityVersion
        controller.next()
        runCurrent()
        assertEquals(SampleAudioSource.YouTubeMusic, controller.state.value.audio.selection?.source)
        assertTrue(controller.state.value.audio.discontinuityVersion > discBeforeSwitch)
        assertEquals(0L, filter.resolve(controller.state.value.audio, testScheduler.currentTime))

        // Advance and auto-advance via Ended
        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(1_500L, filter.resolve(controller.state.value.audio, testScheduler.currentTime))
        output.emitEnded()
        runCurrent()
        // At end of queue without repeat, stays Ended and does not keep advancing lyrics.
        assertEquals(SampleAudioStatus.Ended, controller.state.value.audio.status)
        val endPos = filter.resolve(controller.state.value.audio, testScheduler.currentTime)
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(endPos, filter.resolve(controller.state.value.audio, testScheduler.currentTime))
    }

    @Test
    fun tinyNativeBackwardJitterIsSuppressedWhileRealJumpsSnap() = runTest {
        val track = Track("demo:track:hujan", "Selepas Hujan", listOf("Senada"), 12_000L, source = "demo")
        val output = ClockDrivenAudioOutput { testScheduler.currentTime }
        val controller = PlaybackController(
            output, backgroundScope,
            monotonicNow = { testScheduler.currentTime },
        ) { entry ->
            SampleAudioSelection(entry.track.title, SampleAudioSource.Local, "file:///hujan.m4a", entry.id, durationMs = 12_000L)
        }
        controller.add(track)
        controller.setUiVisible(true)
        controller.resume()
        runCurrent()

        val filter = LyricsPositionFilter()
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1_000L, filter.resolve(controller.state.value.audio, testScheduler.currentTime))

        // Inject a 15ms backward hardware timestamp jitter without discontinuity.
        output.injectRawPosition(985L)
        advanceTimeBy(100L) // trigger controller tick -> output becomes 1085 (or inject right at tick)
        output.injectRawPosition(985L)
        output.onEvent?.invoke()
        runCurrent()
        // The clock never runs backward, and eases toward the player's 985 ms instead of racing on to 1100 ms.
        val afterJitter = filter.resolve(controller.state.value.audio, testScheduler.currentTime)
        assertTrue(afterJitter in 1_000L..1_030L, "shown $afterJitter ms")

        // A large native backward jump (>50ms) triggers discontinuity and snaps immediately.
        output.injectRawPosition(800L)
        output.onEvent?.invoke()
        runCurrent()
        assertEquals(800L, filter.resolve(controller.state.value.audio, testScheduler.currentTime))
    }
}
