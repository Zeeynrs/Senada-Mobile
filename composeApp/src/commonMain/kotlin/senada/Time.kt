package senada

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Waktu dinding saat ini, untuk membandingkan dengan masa berlaku alamat audio. */
@OptIn(ExperimentalTime::class)
internal fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()
