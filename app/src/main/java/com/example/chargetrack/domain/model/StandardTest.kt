package com.example.chargetrack.domain.model

import com.example.chargetrack.domain.enums.TestValidity
import java.time.Instant
import java.util.UUID

/**
 * Marks a [ChargingSession] as a controlled Standard Test and records its target range
 * and validity state.
 *
 * ## Baseline policy
 * [isBaseline] must only be set `true` by an explicit user action — never automatically.
 * The baseline is the fixed reference point for longitudinal comparison.
 * [baselineSetAt] must be non-null whenever [isBaseline] is true.
 * Replacing the baseline also requires explicit user action.
 *
 * ## Validity
 * [TestValidity.VALID]        — conditions met; eligible for comparison.
 * [TestValidity.QUESTIONABLE] — data exists but conditions deviated slightly.
 * [TestValidity.INVALID]      — conditions deviated significantly; exclude from baselines.
 * [invalidationReason] must not be set on a VALID test.
 *
 * ## Comparison grouping
 * [comparisonGroupKey] allows grouping tests with matching conditions
 * (e.g. "wired-official-20-80") so only compatible tests are compared directly.
 *
 * Invariants enforced at construction:
 * - [targetStartPercent] in 0..99; [targetEndPercent] in 1..100.
 * - [targetEndPercent] > [targetStartPercent].
 * - [isBaseline] == true requires [baselineSetAt] to be non-null.
 * - [invalidationReason] must be null when [validity] == VALID.
 */
data class StandardTest(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    /** Lower bound of the target range (0..99). Default: 20. */
    val targetStartPercent: Int = 20,
    /** Upper bound of the target range (1..100). Must be > [targetStartPercent]. Default: 80. */
    val targetEndPercent: Int = 80,
    /**
     * `true` only when the user has explicitly designated this as the baseline.
     * Must not be set automatically. Requires [baselineSetAt] to be set.
     */
    val isBaseline: Boolean = false,
    /**
     * The [Instant] when the user explicitly set this test as the baseline.
     * Null if [isBaseline] is false.
     */
    val baselineSetAt: Instant? = null,
    /**
     * Optional key grouping comparable tests (e.g. "wired-official-20-80").
     * Tests in the same group are eligible for direct comparison.
     */
    val comparisonGroupKey: String? = null,
    val validity: TestValidity = TestValidity.VALID,
    /** Required for QUESTIONABLE or INVALID tests; must be null for VALID tests. */
    val invalidationReason: String? = null
) {
    init {
        require(targetStartPercent in 0..99) {
            "targetStartPercent must be in 0..99, was $targetStartPercent"
        }
        require(targetEndPercent in 1..100) {
            "targetEndPercent must be in 1..100, was $targetEndPercent"
        }
        require(targetEndPercent > targetStartPercent) {
            "targetEndPercent ($targetEndPercent) must be > targetStartPercent ($targetStartPercent)"
        }
        if (isBaseline) {
            requireNotNull(baselineSetAt) {
                "baselineSetAt must be set when isBaseline is true"
            }
        }
        if (invalidationReason != null) {
            require(validity != TestValidity.VALID) {
                "invalidationReason must only be set for QUESTIONABLE or INVALID tests, not VALID"
            }
        }
    }
}
