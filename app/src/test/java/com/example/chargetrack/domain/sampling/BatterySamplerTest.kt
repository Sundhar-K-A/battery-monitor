package com.example.chargetrack.domain.sampling

import com.example.chargetrack.domain.enums.QualityFlag
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.session.SessionConfig
import com.example.chargetrack.domain.time.TimeSource
import com.example.chargetrack.testutil.FakeBatteryDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BatterySamplerTest {

    private class TestTimeSource(
        var currentInstant: Instant = Instant.parse("2026-08-23T10:00:00Z"),
        var currentRealtimeMs: Long = 100_000L,
    ) : TimeSource {
        override fun now(): Instant = currentInstant
        override fun elapsedRealtime(): Long = currentRealtimeMs

        fun advanceSeconds(seconds: Long) {
            currentInstant = currentInstant.plusSeconds(seconds)
            currentRealtimeMs += (seconds * 1000L)
        }
    }

    private lateinit var timeSource: TestTimeSource
    private lateinit var fakeBatteryDataSource: FakeBatteryDataSource
    private lateinit var config: SessionConfig
    private lateinit var sampler: BatterySampler

    @Before
    fun setUp() {
        timeSource = TestTimeSource()
        fakeBatteryDataSource = FakeBatteryDataSource()
        config = SessionConfig(
            expectedSampleIntervalMs = 5_000L,
            measurementGapTimeoutMs = 30_000L,
            unplugDebounceMs = 5_000L,
        )
        sampler = BatterySampler(
            batteryDataSource = fakeBatteryDataSource,
            timeSource = timeSource,
            config = config,
        )
    }

    @Test
    fun `immediate first sample is captured upon start`() = runTest {
        val collectedSamples = mutableListOf<BatterySample>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sampler.sampleStream.collect { collectedSamples.add(it) }
        }

        sampler.start("session-1", startRealtimeMs = 100_000L, scope = backgroundScope)
        testScheduler.runCurrent()

        // First sample captured immediately without waiting 5s
        assertEquals(1, collectedSamples.size)
        assertEquals("session-1", collectedSamples[0].sessionId)
        assertEquals(0L, collectedSamples[0].elapsedMs)
        assertEquals(20, collectedSamples[0].percent)
        assertEquals(60_750_000L, collectedSamples[0].derivedPowerUw) // Calculated in Prompt 09: 4050 mV * 15,000,000 uA / 1000 = 60,750,000 uW

        sampler.stop()
        collectJob.cancel()
    }

    @Test
    fun `samples follow 5-second cadence and monotonic elapsed timing`() = runTest {
        val collectedSamples = mutableListOf<BatterySample>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sampler.sampleStream.collect { collectedSamples.add(it) }
        }

        sampler.start("session-1", startRealtimeMs = 100_000L, scope = backgroundScope)
        testScheduler.runCurrent()
        assertEquals(1, collectedSamples.size)

        // Advance 5s
        timeSource.advanceSeconds(5)
        advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertEquals(2, collectedSamples.size)
        assertEquals(5_000L, collectedSamples[1].elapsedMs)

        // Advance another 5s
        timeSource.advanceSeconds(5)
        advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertEquals(3, collectedSamples.size)
        assertEquals(10_000L, collectedSamples[2].elapsedMs)

        sampler.stop()
        collectJob.cancel()
    }

    @Test
    fun `gap is flagged when sample is delayed without fabricating extra samples`() = runTest {
        val collectedSamples = mutableListOf<BatterySample>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sampler.sampleStream.collect { collectedSamples.add(it) }
        }

        sampler.start("session-1", startRealtimeMs = 100_000L, scope = backgroundScope)
        testScheduler.runCurrent()
        assertEquals(1, collectedSamples.size)

        // Simulate 12-second delay (> 1.5x expected 5s interval)
        timeSource.advanceSeconds(12)
        advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        // 2nd sample arrived late -> GAP_DETECTED flagged
        assertEquals(2, collectedSamples.size)
        assertEquals(12_000L, collectedSamples[1].elapsedMs)
        assertTrue(collectedSamples[1].qualityFlags.contains(QualityFlag.GAP_DETECTED))

        sampler.stop()
        collectJob.cancel()
    }

    @Test
    fun `missing values are preserved as null and not coerced to zero`() = runTest {
        val collectedSamples = mutableListOf<BatterySample>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sampler.sampleStream.collect { collectedSamples.add(it) }
        }

        fakeBatteryDataSource.currentSnapshot = FakeBatteryDataSource.defaultSnapshot(
            percent = null,
            voltageMv = null,
            currentNowUa = null,
        )

        sampler.start("session-1", startRealtimeMs = 100_000L, scope = backgroundScope)
        testScheduler.runCurrent()

        assertEquals(1, collectedSamples.size)
        val sample = collectedSamples[0]
        assertNull(sample.percent)
        assertNull(sample.voltageMv)
        assertNull(sample.currentNowUa)
        assertTrue(sample.qualityFlags.contains(QualityFlag.MISSING_REQUIRED_VALUE))

        sampler.stop()
        collectJob.cancel()
    }

    @Test
    fun `duplicate start calls do not launch multiple concurrent loops`() = runTest {
        val collectedSamples = mutableListOf<BatterySample>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sampler.sampleStream.collect { collectedSamples.add(it) }
        }

        val job1 = sampler.start("session-1", startRealtimeMs = 100_000L, scope = backgroundScope)
        val job2 = sampler.start("session-1", startRealtimeMs = 100_000L, scope = backgroundScope)
        testScheduler.runCurrent()

        assertEquals(job1, job2)
        assertEquals(1, collectedSamples.size) // Only 1 immediate sample, not 2

        timeSource.advanceSeconds(5)
        advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertEquals(2, collectedSamples.size) // Cadence remains 1 per tick

        sampler.stop()
        collectJob.cancel()
    }

    @Test
    fun `stop prevents further sample emissions and clean restart works`() = runTest {
        val collectedSamples = mutableListOf<BatterySample>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sampler.sampleStream.collect { collectedSamples.add(it) }
        }

        sampler.start("session-1", startRealtimeMs = 100_000L, scope = backgroundScope)
        testScheduler.runCurrent()
        assertEquals(1, collectedSamples.size)

        sampler.stop()
        testScheduler.runCurrent()
        assertFalse(sampler.isSampling.value)

        // Advance time while stopped -> no new samples
        timeSource.advanceSeconds(10)
        advanceTimeBy(10_000L)
        testScheduler.runCurrent()
        assertEquals(1, collectedSamples.size)

        // Restart for new session
        sampler.start("session-2", startRealtimeMs = timeSource.elapsedRealtime(), scope = backgroundScope)
        testScheduler.runCurrent()
        assertTrue(sampler.isSampling.value)
        assertEquals(2, collectedSamples.size)
        assertEquals("session-2", collectedSamples[1].sessionId)

        sampler.stop()
        collectJob.cancel()
    }
}
