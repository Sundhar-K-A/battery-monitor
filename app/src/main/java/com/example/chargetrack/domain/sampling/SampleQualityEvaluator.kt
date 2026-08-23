package com.example.chargetrack.domain.sampling

import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.enums.QualityFlag
import com.example.chargetrack.domain.model.BatterySample
import kotlin.math.abs

/**
 * Pure evaluator that computes quality flags for each captured battery sample.
 *
 * Enforces non-destructive quality evaluation:
 * - Flags anomalies and gaps without modifying or discarding raw measurements.
 * - Missing values result in [QualityFlag.MISSING_REQUIRED_VALUE] but do not invalidate the observation.
 * - Outliers result in [QualityFlag.OUTLIER] against configurable [OutlierThresholds].
 * - Percentage jitter results in [QualityFlag.PERCENTAGE_JITTER].
 */
object SampleQualityEvaluator {

    /**
     * Evaluates quality flags for a newly captured [currentSnapshot].
     *
     * @param currentSnapshot The newly acquired battery snapshot.
     * @param previousSample The preceding sample in the session (if any).
     * @param elapsedIntervalMs The actual elapsed monotonic milliseconds since the preceding sample.
     * @param expectedIntervalMs The configured nominal sampling interval (e.g. 5,000 ms).
     * @param thresholds Configurable physical sanity bounds.
     * @return Immutable set of all detected [QualityFlag]s.
     */
    fun evaluate(
        currentSnapshot: BatterySnapshot,
        previousSample: BatterySample?,
        elapsedIntervalMs: Long?,
        expectedIntervalMs: Long = 5_000L,
        thresholds: OutlierThresholds = OutlierThresholds(),
    ): Set<QualityFlag> {
        val flags = mutableSetOf<QualityFlag>()
        flags.addAll(currentSnapshot.qualityFlags)

        // 1. Gap detection (> 1.5x expected interval)
        if (elapsedIntervalMs != null && elapsedIntervalMs > (expectedIntervalMs * 1.5).toLong()) {
            flags.add(QualityFlag.GAP_DETECTED)
        }

        // 2. Missing required value for power calculations (voltage or current unavailable)
        if (currentSnapshot.voltageMv == null || currentSnapshot.currentNowUa == null) {
            flags.add(QualityFlag.MISSING_REQUIRED_VALUE)
        }

        // 3. Percentage jitter (percent decreased during charging without unplug)
        if (currentSnapshot.percent != null && previousSample?.percent != null) {
            if (currentSnapshot.percent < previousSample.percent) {
                flags.add(QualityFlag.PERCENTAGE_JITTER)
            }
        }

        // 4. Outlier evaluation against configurable bounds
        val voltage = currentSnapshot.voltageMv
        if (voltage != null && (voltage < thresholds.minVoltageMv || voltage > thresholds.maxVoltageMv)) {
            flags.add(QualityFlag.OUTLIER)
        }

        val current = currentSnapshot.currentNowUa
        if (current != null && abs(current) > thresholds.maxCurrentNowUa) {
            flags.add(QualityFlag.OUTLIER)
        }

        val temp = currentSnapshot.temperatureDeciC
        if (temp != null && (temp < thresholds.minTemperatureDeciC || temp > thresholds.maxTemperatureDeciC)) {
            flags.add(QualityFlag.OUTLIER)
        }

        return flags
    }
}
