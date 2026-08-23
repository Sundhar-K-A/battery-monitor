package com.example.chargetrack.domain.transition

import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.domain.enums.QualityFlag
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.ChargeTransition
import java.time.Instant
import java.util.UUID

/**
 * Processes one ordered [BatterySample] at a time and emits at most one completed
 * [ChargeTransition] per sample.
 *
 * ## Responsibilities
 * - Detects when the battery percentage advances from integer level N to N+1 (or jumps to N+k).
 * - Aggregates power and temperature statistics from the samples accumulated while the
 *   battery held at level N.
 * - Applies non-destructive quality assessment to every emitted transition.
 *
 * ## Design invariants preserved
 * - **Null-percent samples** (Decision 1): accumulated into the current open window;
 *   counted in [ChargeTransition.sampleCount]; power/temperature contributed when valid;
 *   do not advance the percentage boundary.
 * - **Jitter / decreases** (Decision 2): samples whose [BatterySample.percent] falls below
 *   the confirmed [highWaterMark] are accumulated (already carrying [QualityFlag.PERCENTAGE_JITTER]);
 *   no reverse transition is ever created; the completed transition is marked [DataQuality.DEGRADED].
 * - **Single-sample transitions** (Decision 3): emitted with [DataQuality.DEGRADED].
 * - **Boundary ownership** (Decision 4): the first sample observed at N+1% **closes** the N→N+1
 *   transition but its measurements are NOT included in that transition's power/temperature
 *   aggregates — they belong to the new N+1→N+2 accumulator. The closing sample's
 *   [BatterySample.elapsedMs] and [BatterySample.timestamp] define the transition's end boundary.
 * - **Skipped percentages** (Decision 5): a jump from N% to N+k% (k ≥ 2) emits a single honest
 *   gap [ChargeTransition] with `fromPercent=N`, `toPercent=N+k`, the real observed duration,
 *   stats from the accumulated N%-samples only, and [DataQuality.INSUFFICIENT]. Duration is
 *   never split or fabricated for individual skipped steps.
 * - **Incomplete transition at session end** (Decision 6): [onSessionEnd] returns a
 *   [PartialTransitionInfo] instead of emitting a spurious [ChargeTransition].
 *
 * ## Thread safety
 * Not thread-safe. All calls must be made from the same coroutine/thread context.
 *
 * @param sessionId The ID of the session whose samples are being processed.
 *                  Embedded in every emitted [ChargeTransition].
 */
class ChargeTransitionDetector(private val sessionId: String) {

    // ── Internal state ────────────────────────────────────────────────────────

    private data class Accumulator(
        val fromPercent: Int,
        val startedAt: Instant,
        val startElapsedMs: Long,
        val samples: MutableList<BatterySample> = mutableListOf(),
    )

    /** Currently open transition window; null when the detector has not yet seen a valid percent. */
    private var accumulator: Accumulator? = null

    /**
     * The highest integer percent confirmed so far in this session.
     * Never decreases. Used to identify [QualityFlag.PERCENTAGE_JITTER] samples
     * without creating reverse transitions.
     */
    private var highWaterMark: Int? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Processes the next ordered [BatterySample] in the session.
     *
     * @return A completed [ChargeTransition] if processing this sample closed an open
     *         transition window; `null` otherwise.
     */
    fun onSample(sample: BatterySample): ChargeTransition? {
        val pct = sample.percent

        // ── Decision 1: null percent — accumulate, do not advance boundary ──
        if (pct == null) {
            accumulator?.samples?.add(sample)
            return null
        }

        val acc = accumulator

        // ── No open window yet: open a fresh accumulator ─────────────────────
        if (acc == null) {
            highWaterMark = pct
            accumulator = Accumulator(pct, sample.timestamp, sample.elapsedMs)
                .also { it.samples.add(sample) }
            return null
        }

        // ── Decision 2: jitter — percent fell below the confirmed high-water mark ──
        if (pct < highWaterMark!!) {
            // Sample already carries PERCENTAGE_JITTER from SampleQualityEvaluator.
            acc.samples.add(sample)
            return null
        }

        // ── Same percent level — keep accumulating ────────────────────────────
        if (pct == acc.fromPercent) {
            highWaterMark = pct
            acc.samples.add(sample)
            return null
        }

        // ── Percent advanced (N+1 or N+k): close the current window ──────────
        // Decision 4: the closing sample's boundary values define endedAt and durationMs,
        // but its measurements do NOT enter the closing transition's aggregates.
        val completed = buildTransition(
            acc              = acc,
            closingTimestamp = sample.timestamp,
            closingElapsedMs = sample.elapsedMs,
            targetToPercent  = pct,
        )

        // Open the next window with the closing sample as its first entry.
        highWaterMark = pct
        accumulator = Accumulator(pct, sample.timestamp, sample.elapsedMs)
            .also { it.samples.add(sample) }

        return completed
    }

    /**
     * Signals that the charging session has ended.
     *
     * The incomplete transition (if any) is **not** emitted as a [ChargeTransition].
     * Its summary is returned as a [PartialTransitionInfo] so callers can log or
     * surface it without the detector silently swallowing the information.
     *
     * After this call the detector is reset and ready for reuse with a new session.
     *
     * @return [PartialTransitionInfo] if a transition was in progress, `null` if the
     *         detector was idle (no valid percent sample had been received yet).
     */
    fun onSessionEnd(): PartialTransitionInfo? {
        val acc = accumulator ?: return null
        val info = PartialTransitionInfo(
            fromPercent      = acc.fromPercent,
            samplesCollected = acc.samples.size,
            startedAt        = acc.startedAt,
            startElapsedMs   = acc.startElapsedMs,
        )
        accumulator    = null
        highWaterMark  = null
        return info
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildTransition(
        acc: Accumulator,
        closingTimestamp: Instant,
        closingElapsedMs: Long,
        targetToPercent: Int,
    ): ChargeTransition {
        val samples = acc.samples
        val isGap   = targetToPercent > acc.fromPercent + 1

        // Quality: INSUFFICIENT wins over DEGRADED for gap transitions.
        val quality = when {
            isGap -> DataQuality.INSUFFICIENT
            samples.size < 2 -> DataQuality.DEGRADED
            samples.any {
                QualityFlag.PERCENTAGE_JITTER in it.qualityFlags ||
                QualityFlag.GAP_DETECTED      in it.qualityFlags ||
                QualityFlag.OUTLIER           in it.qualityFlags
            } -> DataQuality.DEGRADED
            else -> DataQuality.GOOD
        }

        // Power and temperature aggregated from accumulated samples only.
        // The closing sample's measurements belong to the next window (Decision 4).
        val validPower = samples.mapNotNull { it.derivedPowerUw }
        val validTemp  = samples.mapNotNull { it.temperatureDeciC }

        return ChargeTransition(
            id                      = UUID.randomUUID().toString(),
            sessionId               = sessionId,
            fromPercent             = acc.fromPercent,
            toPercent               = targetToPercent,
            startedAt               = acc.startedAt,
            endedAt                 = closingTimestamp,
            durationMs              = (closingElapsedMs - acc.startElapsedMs).coerceAtLeast(0L),
            averagePowerUw          = validPower.averageLong(),
            medianPowerUw           = validPower.medianLong(),
            peakPowerUw             = validPower.maxOrNull(),
            averageTemperatureDeciC = validTemp.averageInt(),
            maxTemperatureDeciC     = validTemp.maxOrNull(),
            sampleCount             = samples.size,
            quality                 = quality,
        )
    }

    // ── Aggregate helpers ─────────────────────────────────────────────────────

    private fun List<Long>.averageLong(): Long? =
        if (isEmpty()) null else sum() / size

    private fun List<Long>.medianLong(): Long? {
        if (isEmpty()) return null
        val s   = sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }

    private fun List<Int>.averageInt(): Int? =
        if (isEmpty()) null else (sum().toLong() / size).toInt()
}
