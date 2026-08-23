package com.example.chargetrack.domain.model

/**
 * Domain constants for standardized charging tests.
 */
object StandardTestConstants {
    /** Minimum required percentage span between start and target percentages. */
    const val MIN_STANDARD_TEST_PERCENT_SPAN = 5

    /** Canonical start percentage for iQOO 15 longitudinal degradation benchmarks. */
    const val CANONICAL_START_PERCENT = 20

    /** Canonical target percentage for iQOO 15 longitudinal degradation benchmarks. */
    const val CANONICAL_TARGET_PERCENT = 80
}

/**
 * Standard test configuration presets.
 *
 * `STANDARD_20_80` is the canonical benchmark representing the primary comparison series
 * for battery health tracking and software update correlation.
 */
enum class StandardTestPreset(
    val title: String,
    val description: String,
    val startPercent: Int,
    val targetPercent: Int,
    val isCanonical: Boolean = false,
) {
    STANDARD_20_80(
        title = "20% → 80% (Canonical Benchmark)",
        description = "Official longitudinal degradation benchmark. Covers peak FlashCharge phase.",
        startPercent = StandardTestConstants.CANONICAL_START_PERCENT,
        targetPercent = StandardTestConstants.CANONICAL_TARGET_PERCENT,
        isCanonical = true,
    ),
    FULL_10_100(
        title = "10% → 100% (Full Capacity)",
        description = "Comprehensive full-cycle test from low battery to 100% full.",
        startPercent = 10,
        targetPercent = 100,
    ),
    EXTENDED_20_100(
        title = "20% → 100% (Extended Test)",
        description = "Standard start with full top-up including taper and trickle phases.",
        startPercent = 20,
        targetPercent = 100,
    ),
    QUICK_30_80(
        title = "30% → 80% (Quick Benchmark)",
        description = "Rapid standardized test when battery is above 20%.",
        startPercent = 30,
        targetPercent = 80,
    ),
    FAST_20_60(
        title = "20% → 60% (Peak Speed)",
        description = "Measures maximum speed stage before thermal or constant-voltage taper.",
        startPercent = 20,
        targetPercent = 60,
    ),
    CUSTOM(
        title = "Custom Range",
        description = "User-defined custom start and target percentage boundaries.",
        startPercent = 20,
        targetPercent = 80,
    );

    init {
        require(targetPercent >= startPercent + StandardTestConstants.MIN_STANDARD_TEST_PERCENT_SPAN) {
            "targetPercent ($targetPercent) must be at least ${StandardTestConstants.MIN_STANDARD_TEST_PERCENT_SPAN}% greater than startPercent ($startPercent)"
        }
    }
}
