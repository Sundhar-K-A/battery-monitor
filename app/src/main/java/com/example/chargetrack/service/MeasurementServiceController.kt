package com.example.chargetrack.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controller interface for safely starting and stopping [MeasurementService].
 */
interface MeasurementServiceController {
    /**
     * Attempts to start [MeasurementService] in the foreground for the given session.
     *
     * @param sessionId The active charging session ID.
     * @param startRealtimeMs Monotonic elapsedRealtime at session start.
     * @return True if the service start command was successfully dispatched; false otherwise.
     */
    fun startService(sessionId: String, startRealtimeMs: Long): Boolean

    /**
     * Dispatches a stop command to [MeasurementService].
     */
    fun stopService()
}

/**
 * Default implementation of [MeasurementServiceController] interacting with Android OS services.
 */
@Singleton
class DefaultMeasurementServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) : MeasurementServiceController {

    override fun startService(sessionId: String, startRealtimeMs: Long): Boolean {
        return try {
            val intent = MeasurementService.createStartIntent(context, sessionId, startRealtimeMs)
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Foreground service start rejected by Android OS: background launch not allowed", e)
            } else {
                Log.e(TAG, "Failed to start MeasurementService", e)
            }
            false
        }
    }

    override fun stopService() {
        try {
            val intent = MeasurementService.createStopIntent(context)
            context.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch stop intent to MeasurementService", e)
        }
    }

    companion object {
        private const val TAG = "MeasurementServiceCtrl"
    }
}
