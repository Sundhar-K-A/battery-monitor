package com.example.chargetrack.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.chargetrack.MainActivity
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.ChargingSession
import com.example.chargetrack.domain.power.BatteryPowerEstimator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages notification channel configuration and notification layout generation
 * for the foreground charging measurement service.
 */
@Singleton
class MeasurementNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    init {
        createNotificationChannel()
    }

    /**
     * Ensures the low-importance notification channel is registered with Android OS.
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds an ongoing status notification displaying live charging metrics.
     *
     * @param session Active charging session, or null if starting.
     * @param sample Most recent raw battery sample observation.
     * @param elapsedMs Monotonic elapsed milliseconds since session start.
     * @param isDebouncing True if currently in [com.example.chargetrack.domain.session.SessionState.UnpluggedPending].
     * @param sampleCount Total number of raw samples collected so far.
     */
    fun buildNotification(
        session: ChargingSession?,
        sample: BatterySample?,
        elapsedMs: Long,
        isDebouncing: Boolean,
        sampleCount: Int = 1,
    ): Notification {
        val title = if (isDebouncing) {
            "ChargeTrack — Debouncing unplug..."
        } else {
            "ChargeTrack — Charging Active"
        }

        val percentText = sample?.percent?.let { "$it%" } ?: "—"
        val powerText = BatteryPowerEstimator.formatWattsWithUnit(sample?.derivedPowerUw) ?: "—"
        val elapsedText = formatElapsed(elapsedMs)
        val contentText = "$percentText · $powerText · $elapsedText elapsed · $sampleCount samples"

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(context, MeasurementService::class.java).apply {
            action = MeasurementService.ACTION_STOP_SESSION
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            REQUEST_CODE_STOP_SESSION,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val iconRes = android.R.drawable.ic_lock_idle_charging

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(iconRes)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Session",
                stopPendingIntent,
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs / 1000).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    companion object {
        const val CHANNEL_ID = "charging_measurement_channel"
        const val CHANNEL_NAME = "Charging Measurement"
        const val CHANNEL_DESCRIPTION = "Ongoing notification displaying live battery charging metrics and power"
        const val NOTIFICATION_ID = 1001

        private const val REQUEST_CODE_OPEN_APP = 101
        private const val REQUEST_CODE_STOP_SESSION = 102
    }
}
