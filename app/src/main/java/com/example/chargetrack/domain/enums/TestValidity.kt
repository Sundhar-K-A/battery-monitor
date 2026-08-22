package com.example.chargetrack.domain.enums

/**
 * Validity classification for a [StandardTest].
 *
 * Uses three states so that "conditions weren't ideal but data exists" (QUESTIONABLE)
 * is explicitly distinct from "data is unusable for comparison" (INVALID).
 */
enum class TestValidity {
    /** Test ran under the intended conditions. Eligible for longitudinal comparison. */
    VALID,
    /** Test completed but some conditions deviated (e.g. slightly elevated temperature).
     *  Data is retained with a caveat. */
    QUESTIONABLE,
    /** Test conditions deviated significantly or data integrity was compromised.
     *  Should be excluded from comparison baselines. */
    INVALID
}
