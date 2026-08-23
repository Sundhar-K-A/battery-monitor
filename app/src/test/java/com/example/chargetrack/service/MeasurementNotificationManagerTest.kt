package com.example.chargetrack.service

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.domain.model.BatterySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.example.chargetrack.ChargeTrackApplication
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = ChargeTrackApplication::class, sdk = [34])
class MeasurementNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var notificationManager: MeasurementNotificationManager
    private lateinit var systemNotificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager = MeasurementNotificationManager(context)
    }

    @Test
    fun `notification channel is created with low importance and correct id`() {
        val channel = systemNotificationManager.getNotificationChannel(MeasurementNotificationManager.CHANNEL_ID)
        assertNotNull("Channel must exist", channel)
        assertEquals(MeasurementNotificationManager.CHANNEL_NAME, channel.name)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test
    fun `active notification formats percent power elapsed time and sample count`() {
        val sample = BatterySample(
            id = "s-1",
            sessionId = "session-1",
            timestamp = Instant.parse("2026-08-23T10:00:00Z"),
            elapsedMs = 120_000L,
            percent = 71,
            voltageMv = 4200,
            currentNowUa = 15_000_000,
            currentAverageUa = null,
            chargeCounterUah = null,
            energyCounterNwh = null,
            temperatureDeciC = 350,
            batteryStatus = 2,
            pluggedType = 1,
            cycleCount = null,
            derivedPowerUw = 63_000_000L, // 63.00 W
            qualityFlags = emptySet(),
        )

        val notification = notificationManager.buildNotification(
            session = null,
            sample = sample,
            elapsedMs = 120_000L,
            isDebouncing = false,
            sampleCount = 24,
        )

        assertEquals("ChargeTrack — Charging Active", notification.extras.getString("android.title"))
        val text = notification.extras.getCharSequence("android.text")?.toString()
        assertNotNull(text)
        assertTrue("Must contain percentage", text!!.contains("71%"))
        assertTrue("Must contain power in Watts", text.contains("63.00 W"))
        assertTrue("Must contain elapsed time", text.contains("02:00"))
        assertTrue("Must contain sample count", text.contains("24 samples"))
    }

    @Test
    fun `debouncing state updates notification title`() {
        val notification = notificationManager.buildNotification(
            session = null,
            sample = null,
            elapsedMs = 10_000L,
            isDebouncing = true,
            sampleCount = 5,
        )

        assertEquals("ChargeTrack — Debouncing unplug...", notification.extras.getString("android.title"))
    }

    @Test
    fun `null metrics in sample display as dashes without zero coercion`() {
        val nullSample = BatterySample(
            id = "s-null",
            sessionId = "session-null",
            timestamp = Instant.parse("2026-08-23T10:00:00Z"),
            elapsedMs = 0L,
            percent = null,
            voltageMv = null,
            currentNowUa = null,
            currentAverageUa = null,
            chargeCounterUah = null,
            energyCounterNwh = null,
            temperatureDeciC = null,
            batteryStatus = 2,
            pluggedType = 1,
            cycleCount = null,
            derivedPowerUw = null,
            qualityFlags = emptySet(),
        )

        val notification = notificationManager.buildNotification(
            session = null,
            sample = nullSample,
            elapsedMs = 0L,
            isDebouncing = false,
            sampleCount = 1,
        )

        val text = notification.extras.getCharSequence("android.text")?.toString()
        assertNotNull(text)
        assertTrue("Null percent must display as dash", text!!.contains("— · — · 00:00 elapsed · 1 samples"))
    }
}
