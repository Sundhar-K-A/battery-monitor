package com.example.chargetrack.domain.enums

/**
 * Overall quality level for a derived summary such as [ChargeTransition] or session analytics.
 *
 * Exactly one level applies to a summary — these values are mutually exclusive.
 * For fine-grained per-sample flags that can coexist, use [QualityFlag].
 */
enum class DataQuality {
    /** All required data was available and within expected ranges. */
    GOOD,
    /** Some data was missing or marginal, but the result is still usable. */
    DEGRADED,
    /** Too little valid data to produce a reliable result. */
    INSUFFICIENT
}
