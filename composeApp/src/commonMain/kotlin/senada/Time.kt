package senada

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

private val monotonicOrigin = TimeSource.Monotonic.markNow()

/** Waktu dinding saat ini, untuk membandingkan dengan masa berlaku alamat audio. */
@OptIn(ExperimentalTime::class)
internal fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Jam monotonik (ms) untuk menyelaraskan posisi audio dan tampilan di antara sampel. */
internal fun monotonicMillis(): Long = monotonicOrigin.elapsedNow().inWholeMilliseconds
