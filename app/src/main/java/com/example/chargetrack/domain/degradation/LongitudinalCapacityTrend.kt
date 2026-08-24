package com.example.chargetrack.domain.degradation

import java.time.Instant

/**
 * A single full-charge capacity observation plotted on the longitudinal timeline.
 */
data class CapacityTrendPoint(
    val sessionId: String,
    val timestamp: Instant,
    val observedCapacityMah: Int,
    val retentionPercent: Double, // Relative to reference capacity (e.g. 7000 mAh)
)

/**
 * Longitudinal capacity degradation analysis derived from historical full-charge events.
 */
data class CapacityDegradationAnalysis(
    val referenceCapacityMah: Int,
    val estimatedCapacityMah: Int?,        // Robust median across all qualifying observations
    val estimatedHealthPercent: Int?,       // User-facing percentage, capped at 100%
    val latestObservation: CapacityTrendPoint?,
    val confidence: DegradationConfidence,
    val isConsistent: Boolean,              // CV <= 0.05 AND spread <= 8%
    val coefficientOfVariation: Double?,    // Sample CV
    val spreadPercent: Double?,             // (max - min) / referenceCapacity
    val observationCount: Int,
    val points: List<CapacityTrendPoint>,  // Sorted chronologically
    val changeFromReferenceMah: Int?,      // estimatedCapacity - referenceCapacity
    val changeFromReferencePercent: Double?,
)
