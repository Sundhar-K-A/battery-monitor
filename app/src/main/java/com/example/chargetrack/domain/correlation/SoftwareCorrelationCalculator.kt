package com.example.chargetrack.domain.correlation

import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.model.StandardTest
import java.time.Instant

/**
 * Data bundle for inputting a Standard Test with software snapshot into correlation analysis.
 */
data class StandardTestWithSnapshotInput(
    val test: StandardTest,
    val sessionStartedAt: Instant,
    val softwareSnapshot: SoftwareSnapshot,
    val samples: List<BatterySample>,
)

/**
 * Pure domain calculator for firmware and software version correlation.
 *
 * ## Invariants
 * 1. Restricts analysis strictly to the provided comparison group.
 * 2. Metric calculations use only Prompt 18's benchmark interval ([benchmarkStartedElapsedMs] .. [benchmarkEndedElapsedMs]).
 * 3. Robust against N=0 qualifying benchmark sessions without crashing or fabricating points.
 * 4. Flags comparisons as [isLowEvidence] when any build has fewer than 3 sessions.
 */
object SoftwareCorrelationCalculator {

    const val LOW_EVIDENCE_THRESHOLD = 3

    private data class ParsedTestItem(
        val input: StandardTestWithSnapshotInput,
        val benchmarkDurationMs: Long,
        val benchmarkAveragePowerUw: Long,
        val benchmarkMaxTempDeciC: Int?,
        val firmwareKey: String,
        val appKey: String,
    )

    fun calculateCorrelationAnalysis(
        groupKey: String,
        inputs: List<StandardTestWithSnapshotInput>,
    ): SoftwareCorrelationAnalysis {
        val parsedItems = mutableListOf<ParsedTestItem>()

        for (input in inputs) {
            val test = input.test
            val startElapsed = test.benchmarkStartedElapsedMs ?: continue
            val endElapsed = test.benchmarkEndedElapsedMs ?: continue
            val durationMs = endElapsed - startElapsed
            if (durationMs <= 0) continue

            val benchmarkSamples = input.samples.filter { it.elapsedMs in startElapsed..endElapsed }
            val powers = benchmarkSamples.mapNotNull { sample ->
                sample.derivedPowerUw ?: (
                    if (sample.voltageMv != null && sample.currentNowUa != null) {
                        sample.voltageMv.toLong() * sample.currentNowUa
                    } else null
                )
            }

            val avgPowerUw = if (powers.isNotEmpty()) powers.average().toLong() else 0L
            if (avgPowerUw <= 0) continue

            val maxTemp = benchmarkSamples.mapNotNull { it.temperatureDeciC }.maxOrNull()
            val firmwareKey = SoftwareIdentityUtils.computeFirmwareKey(input.softwareSnapshot)
            val appKey = SoftwareIdentityUtils.computeAppKey(input.softwareSnapshot)

            parsedItems.add(
                ParsedTestItem(
                    input = input,
                    benchmarkDurationMs = durationMs,
                    benchmarkAveragePowerUw = avgPowerUw,
                    benchmarkMaxTempDeciC = maxTemp,
                    firmwareKey = firmwareKey,
                    appKey = appKey,
                )
            )
        }

        // Robustness guarantee: N=0 returns clean empty analysis with no crashes or fabricated data
        if (parsedItems.isEmpty()) {
            return SoftwareCorrelationAnalysis(
                comparisonGroupKey = groupKey,
                firmwareSummaries = emptyList(),
                firmwareTransitions = emptyList(),
                buildComparisons = emptyList(),
            )
        }

        val sortedItems = parsedItems.sortedBy { it.input.sessionStartedAt }

        // 1. Detect chronological transitions
        val transitions = mutableListOf<SoftwareVersionTransition>()
        var prevFirmwareKey: String? = null
        var prevFirmwareLabel: String? = null
        var prevAppKey: String? = null

        for (item in sortedItems) {
            val currFwKey = item.firmwareKey
            val currFwLabel = SoftwareIdentityUtils.formatFirmwareDisplayLabel(item.input.softwareSnapshot)
            val currAppKey = item.appKey

            val isFwChanged = prevFirmwareKey != null && prevFirmwareKey != currFwKey
            val isAppChanged = prevAppKey != null && prevAppKey != currAppKey

            if (isFwChanged || isAppChanged) {
                transitions.add(
                    SoftwareVersionTransition(
                        timestamp = item.input.sessionStartedAt,
                        sessionId = item.input.test.sessionId,
                        isFirmwareChanged = isFwChanged,
                        isAppVersionChanged = isAppChanged,
                        previousFirmwareLabel = prevFirmwareLabel,
                        newFirmwareLabel = currFwLabel,
                        previousAppVersion = prevAppKey,
                        newAppVersion = currAppKey,
                    )
                )
            }

            prevFirmwareKey = currFwKey
            prevFirmwareLabel = currFwLabel
            prevAppKey = currAppKey
        }

        // 2. Group by canonical firmwareKey
        val groupedByFirmware = sortedItems.groupBy { it.firmwareKey }
        val summaries = groupedByFirmware.map { (fwKey, items) ->
            val sampleSnapshot = items.first().input.softwareSnapshot
            val durations = items.map { it.benchmarkDurationMs }.sorted()
            val medianDurationMs = if (durations.size % 2 == 1) {
                durations[durations.size / 2]
            } else {
                (durations[durations.size / 2 - 1] + durations[durations.size / 2]) / 2
            }

            val meanPowerUw = items.map { it.benchmarkAveragePowerUw }.average().toLong()
            val maxTemp = items.mapNotNull { it.benchmarkMaxTempDeciC }.maxOrNull()
            val distinctApps = items.map { it.appKey }.distinct()
            val count = items.size

            FirmwareBuildBenchmarkSummary(
                firmwareKey = fwKey,
                firmwareDisplayLabel = SoftwareIdentityUtils.formatFirmwareDisplayLabel(sampleSnapshot),
                androidVersion = sampleSnapshot.androidVersion,
                originOsVersion = sampleSnapshot.originOsVersion,
                buildFingerprint = sampleSnapshot.buildFingerprint,
                appVersionsSeen = distinctApps,
                sessionCount = count,
                isLowEvidence = count < LOW_EVIDENCE_THRESHOLD,
                firstSeenAt = items.minOf { it.input.sessionStartedAt },
                lastSeenAt = items.maxOf { it.input.sessionStartedAt },
                medianBenchmarkDurationMs = medianDurationMs,
                meanBenchmarkAveragePowerUw = meanPowerUw,
                maxBenchmarkTempDeciC = maxTemp,
            )
        }.sortedBy { it.firstSeenAt }

        // 3. Build chronological pairwise comparisons
        val comparisons = mutableListOf<FirmwareBuildComparison>()
        for (i in 0 until summaries.size - 1) {
            val prior = summaries[i]
            val current = summaries[i + 1]

            var durShiftMs: Long? = null
            var durShiftPct: Double? = null
            if (current.medianBenchmarkDurationMs != null && prior.medianBenchmarkDurationMs != null) {
                durShiftMs = current.medianBenchmarkDurationMs - prior.medianBenchmarkDurationMs
                if (prior.medianBenchmarkDurationMs > 0) {
                    durShiftPct = (durShiftMs.toDouble() / prior.medianBenchmarkDurationMs) * 100.0
                }
            }

            var pwrShiftUw: Long? = null
            var pwrShiftPct: Double? = null
            if (current.meanBenchmarkAveragePowerUw != null && prior.meanBenchmarkAveragePowerUw != null) {
                pwrShiftUw = current.meanBenchmarkAveragePowerUw - prior.meanBenchmarkAveragePowerUw
                if (prior.meanBenchmarkAveragePowerUw > 0) {
                    pwrShiftPct = (pwrShiftUw.toDouble() / prior.meanBenchmarkAveragePowerUw) * 100.0
                }
            }

            comparisons.add(
                FirmwareBuildComparison(
                    priorBuild = prior,
                    currentBuild = current,
                    durationShiftMs = durShiftMs,
                    durationShiftPercent = durShiftPct,
                    powerShiftUw = pwrShiftUw,
                    powerShiftPercent = pwrShiftPct,
                    isLowEvidence = prior.isLowEvidence || current.isLowEvidence,
                )
            )
        }

        return SoftwareCorrelationAnalysis(
            comparisonGroupKey = groupKey,
            firmwareSummaries = summaries,
            firmwareTransitions = transitions,
            buildComparisons = comparisons,
        )
    }
}
