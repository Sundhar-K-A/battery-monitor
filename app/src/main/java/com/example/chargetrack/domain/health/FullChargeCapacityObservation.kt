package com.example.chargetrack.domain.health

import java.time.Instant

/**
 * A single full-charge capacity observation derived from a historical charging session.
 *
 * @param sessionId         The ID of the source session.
 * @param timestamp         The timestamp when the full-charge event occurred.
 * @param capacityMah       The derived capacity in mAh (median of 100% window divided by 1000).
 * @param rawMedianUah      The exact median charge counter reading in µAh.
 * @param sampleCountAtFull The number of qualifying samples in the 100% state-of-charge window.
 */
data class FullChargeCapacityObservation(
    val sessionId: String,
    val timestamp: Instant,
    val capacityMah: Int,
    val rawMedianUah: Long,
    val sampleCountAtFull: Int,
)
