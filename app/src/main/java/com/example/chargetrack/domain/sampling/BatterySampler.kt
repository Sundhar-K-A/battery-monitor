package com.example.chargetrack.domain.sampling

import com.example.chargetrack.domain.battery.BatteryDataSource
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.session.SessionConfig
import com.example.chargetrack.domain.time.DefaultTimeSource
import com.example.chargetrack.domain.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight, coroutine-based 5-second sampling loop.
 *
 * Responsibilities:
 * - Collects raw [BatterySample]s periodically from [BatteryDataSource].
 * - Performs monotonic time-drift compensation to prevent interval creep.
 * - Captures the first sample immediately upon start.
 * - Evaluates non-destructive quality flags via [SampleQualityEvaluator].
 * - Guarantees a single active sampling loop per session.
 * - Leaves `derivedPowerUw` as null (raw-only for Prompt 08).
 */
@Singleton
class BatterySampler @Inject constructor(
    private val batteryDataSource: BatteryDataSource,
    private val timeSource: TimeSource = DefaultTimeSource(),
    private val config: SessionConfig = SessionConfig(),
    private val outlierThresholds: OutlierThresholds = OutlierThresholds(),
) {
    private val mutex = Mutex()
    private var samplingJob: Job? = null
    private var activeSessionId: String? = null

    private val _isSampling = MutableStateFlow(false)
    val isSampling: StateFlow<Boolean> = _isSampling.asStateFlow()

    private val _latestSample = MutableStateFlow<BatterySample?>(null)
    val latestSample: StateFlow<BatterySample?> = _latestSample.asStateFlow()

    private val _sampleStream = MutableSharedFlow<BatterySample>(replay = 1, extraBufferCapacity = 64)
    val sampleStream: SharedFlow<BatterySample> = _sampleStream.asSharedFlow()

    /**
     * Starts the periodic sampling loop for the given [sessionId].
     *
     * Captures the initial sample immediately, then schedules periodic wakeups with drift compensation.
     * If already sampling for the same session, returns the active [Job] without launching duplicate loops.
     */
    suspend fun start(
        sessionId: String,
        startRealtimeMs: Long = timeSource.elapsedRealtime(),
        scope: CoroutineScope,
    ): Job = mutex.withLock {
        if (samplingJob?.isActive == true && activeSessionId == sessionId) {
            return samplingJob!!
        }

        // Cancel previous job if running for a different session
        samplingJob?.cancel()

        activeSessionId = sessionId
        _isSampling.value = true

        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            var previousSample: BatterySample? = null
            var lastSampleRealtimeMs = timeSource.elapsedRealtime()

            try {
                // 1. Immediate first sample
                val initialSample = captureSample(
                    sessionId = sessionId,
                    startRealtimeMs = startRealtimeMs,
                    currentRealtimeMs = lastSampleRealtimeMs,
                    previousSample = null,
                    elapsedIntervalMs = null,
                )
                previousSample = initialSample
                emitSample(initialSample)

                // 2. Schedule monotonic target wake time
                var targetWakeTimeMs = startRealtimeMs + config.expectedSampleIntervalMs

                while (isActive) {
                    val currentRealtime = timeSource.elapsedRealtime()
                    val delayMs = (targetWakeTimeMs - currentRealtime).coerceAtLeast(0L)

                    if (delayMs > 0) {
                        delay(delayMs)
                    }

                    val sampleRealtime = timeSource.elapsedRealtime()
                    val elapsedIntervalMs = sampleRealtime - lastSampleRealtimeMs

                    val sample = captureSample(
                        sessionId = sessionId,
                        startRealtimeMs = startRealtimeMs,
                        currentRealtimeMs = sampleRealtime,
                        previousSample = previousSample,
                        elapsedIntervalMs = elapsedIntervalMs,
                    )

                    previousSample = sample
                    lastSampleRealtimeMs = sampleRealtime
                    targetWakeTimeMs += config.expectedSampleIntervalMs

                    // If wakeup was severely delayed beyond the next target interval, realign targetWakeTime
                    if (sampleRealtime > targetWakeTimeMs) {
                        targetWakeTimeMs = sampleRealtime + config.expectedSampleIntervalMs
                    }

                    emitSample(sample)
                }
            } catch (e: CancellationException) {
                // Clean shutdown
            } finally {
                _isSampling.value = false
            }
        }

        samplingJob = job
        job
    }

    /**
     * Stops the active sampling loop and awaits cancellation.
     */
    suspend fun stop() = mutex.withLock {
        samplingJob?.cancel()
        samplingJob?.join()
        samplingJob = null
        activeSessionId = null
        _isSampling.value = false
    }

    private suspend fun captureSample(
        sessionId: String,
        startRealtimeMs: Long,
        currentRealtimeMs: Long,
        previousSample: BatterySample?,
        elapsedIntervalMs: Long?,
    ): BatterySample {
        val snapshot = batteryDataSource.readSnapshot()
        val elapsedMs = (currentRealtimeMs - startRealtimeMs).coerceAtLeast(0L)

        val flags = SampleQualityEvaluator.evaluate(
            currentSnapshot = snapshot,
            previousSample = previousSample,
            elapsedIntervalMs = elapsedIntervalMs,
            expectedIntervalMs = config.expectedSampleIntervalMs,
            thresholds = outlierThresholds,
        )

        return BatterySample(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            timestamp = snapshot.timestamp,
            elapsedMs = elapsedMs,
            percent = snapshot.percent,
            voltageMv = snapshot.voltageMv,
            currentNowUa = snapshot.currentNowUa,
            currentAverageUa = snapshot.currentAverageUa,
            chargeCounterUah = snapshot.chargeCounterUah,
            energyCounterNwh = snapshot.energyCounterNwh,
            temperatureDeciC = snapshot.temperatureDeciC,
            batteryStatus = snapshot.batteryStatus,
            pluggedType = snapshot.pluggedType,
            cycleCount = snapshot.cycleCount,
            derivedPowerUw = com.example.chargetrack.domain.power.BatteryPowerEstimator.calculatePowerUw(
                voltageMv = snapshot.voltageMv,
                currentNowUa = snapshot.currentNowUa,
            ),
            qualityFlags = flags,
        )
    }

    private suspend fun emitSample(sample: BatterySample) {
        _latestSample.value = sample
        _sampleStream.emit(sample)
    }
}
