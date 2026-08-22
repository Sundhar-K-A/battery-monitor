package com.example.chargetrack.domain.model

import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import java.time.Instant
import java.util.UUID

/**
 * A bounded charging measurement event.
 *
 * [endedAt] is null while the session is still in progress.
 * [endPercent] is null if the session was interrupted before a final percentage was recorded.
 * [endReason] must be set when the session ends — never leave it null on a completed session.
 *
 * Invariants enforced at construction:
 * - [startPercent] and [endPercent] must be in 0..100.
 * - If both [startedAt] and [endedAt] are set, [endedAt] must not precede [startedAt].
 */
data class ChargingSession(
    val id: String = UUID.randomUUID().toString(),
    val startedAt: Instant,
    val endedAt: Instant? = null,
    /** Battery percentage when monitoring began. Range: 0..100. */
    val startPercent: Int,
    /** Battery percentage when monitoring ended. Range: 0..100. Null if session is still active. */
    val endPercent: Int? = null,
    /** References the [ChargingSetup] record for this session. */
    val chargingSetupId: String,
    val testType: TestType = TestType.FREE_FORM,
    val userNotes: String? = null,
    /** References the [SoftwareSnapshot] captured at session start. */
    val softwareSnapshotId: String,
    val endReason: SessionEndReason? = null
) {
    init {
        require(startPercent in 0..100) {
            "startPercent must be in 0..100, was $startPercent"
        }
        endPercent?.let {
            require(it in 0..100) {
                "endPercent must be in 0..100, was $it"
            }
        }
        endedAt?.let {
            require(!it.isBefore(startedAt)) {
                "endedAt ($it) must not precede startedAt ($startedAt)"
            }
        }
    }
}
