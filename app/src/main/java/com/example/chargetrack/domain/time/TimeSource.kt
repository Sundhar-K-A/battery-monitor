package com.example.chargetrack.domain.time

import android.os.SystemClock
import java.time.Instant

/**
 * Abstraction for system time sources.
 *
 * Separates wall-clock time ([Instant]) for event timestamps from
 * monotonic elapsed time ([elapsedRealtime]) for duration measurements.
 * Injectable to guarantee 100% deterministic unit tests.
 */
interface TimeSource {
    /** Current absolute wall-clock time. */
    fun now(): Instant

    /** Current monotonic elapsed milliseconds since boot (including deep sleep). */
    fun elapsedRealtime(): Long
}

/**
 * Production implementation backed by Android [SystemClock] and [Instant.now].
 */
class DefaultTimeSource : TimeSource {
    override fun now(): Instant = Instant.now()
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}
