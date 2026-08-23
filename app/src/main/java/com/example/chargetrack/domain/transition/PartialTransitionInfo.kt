package com.example.chargetrack.domain.transition

import java.time.Instant

/**
 * Transient summary of a transition that was open when a charging session ended.
 *
 * This value is NOT persisted to the database. It is exposed by
 * [ChargeTransitionDetector.onSessionEnd] so that callers can log, surface in the UI,
 * or otherwise account for the fact that an in-progress transition was abandoned
 * rather than silently discarded.
 *
 * A partial transition is definitionally incomplete: the battery never reached
 * [fromPercent] + 1 before the session ended, so no [com.example.chargetrack.domain.model.ChargeTransition]
 * can honestly represent it.
 *
 * @property fromPercent    The percent level that was open when the session ended.
 * @property samplesCollected Number of [com.example.chargetrack.domain.model.BatterySample] records
 *                          accumulated during this incomplete window (including null-percent samples).
 * @property startedAt      Wall-clock [Instant] when this transition window opened.
 * @property startElapsedMs Monotonic elapsed-realtime (ms) when this transition window opened.
 */
data class PartialTransitionInfo(
    val fromPercent: Int,
    val samplesCollected: Int,
    val startedAt: Instant,
    val startElapsedMs: Long,
)
