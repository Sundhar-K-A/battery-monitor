package com.example.chargetrack.domain.model

import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import java.time.Instant
import java.util.UUID

/**
 * A bounded charging measurement event.
 *
 * While the session is active: [endedAt], [endReason], and [endPercent] are all null.
 * When the session completes: [endedAt] and [endReason] must both be set.
 * [endPercent] may still be null on a completed session if the final percentage
 * could not be recorded (e.g. service killed before last sample).
 *
 * Invariants enforced at construction:
 * - [startPercent] and [endPercent] must be in 0..100.
 * - [endedAt] must not precede [startedAt].
 * - [endedAt] and [endReason] must either both be present or both be absent.
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
        require((endedAt == null) == (endReason == null)) {
            "endedAt and endReason must either both be present or both be absent. " +
                "endedAt=$endedAt, endReason=$endReason"
        }
    }
}
