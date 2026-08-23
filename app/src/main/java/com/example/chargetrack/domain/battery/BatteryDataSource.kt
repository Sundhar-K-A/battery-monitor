package com.example.chargetrack.domain.battery

/**
 * Abstraction over the Android battery hardware APIs.
 *
 * Implementations must:
 * - Never block the calling thread for I/O (use suspend + Dispatchers.IO internally).
 * - Return null for any field that is genuinely unavailable from the device.
 * - Never substitute zero for unavailable values.
 *
 * The session engine (Prompt 07) is responsible for polling frequency.
 * Implementations must not impose their own sampling intervals.
 */
interface BatteryDataSource {

    /**
     * Reads the current battery state and returns it as an immutable [BatterySnapshot].
     *
     * This is a one-shot read. The caller controls when to call it again.
     * Must not be called on the main thread; implementations dispatch to an appropriate context.
     */
    suspend fun readSnapshot(): BatterySnapshot
}
