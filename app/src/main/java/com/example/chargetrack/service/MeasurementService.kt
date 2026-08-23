package com.example.chargetrack.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.chargetrack.data.sampling.SamplingRepository
import com.example.chargetrack.data.session.ChargingSessionRepository
import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.ChargingSession
import com.example.chargetrack.domain.session.SessionState
import com.example.chargetrack.domain.time.TimeSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Android Foreground Service owning the long-running charging measurement loop.
 *
 * ## Invariants
 * - Sole owner of background sampling execution; decoupled from UI activity lifecycle.
 * - Displays a persistent low-importance ongoing notification updated on sample ingestion.
 * - Reflects state machine's 5-second unplug debounce without separate service-level timers.
 * - Gracefully terminates upon session completion or user stop action.
 */
@AndroidEntryPoint
class MeasurementService : Service() {

    @Inject lateinit var sessionRepository: ChargingSessionRepository
    @Inject lateinit var samplingRepository: SamplingRepository
    @Inject lateinit var notificationManager: MeasurementNotificationManager
    @Inject lateinit var timeSource: TimeSource

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private val systemNotificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private var activeSessionId: String? = null
    private var sessionStartRealtimeMs: Long = 0L
    private var activeSession: ChargingSession? = null
    private var isDebouncing: Boolean = false
    private var latestSample: BatterySample? = null
    private var currentSampleCount: Int = 1

    private var sessionStateJob: Job? = null
    private var sampleCollectionJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SESSION -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                val startRealtimeMs = intent.getLongExtra(EXTRA_START_REALTIME_MS, timeSource.elapsedRealtime())

                if (!sessionId.isNullOrBlank()) {
                    handleStartSession(sessionId, startRealtimeMs)
                }
            }

            ACTION_STOP_SESSION -> {
                handleStopSession()
            }

            ACTION_STOP_SERVICE -> {
                handleStopService()
            }
        }

        return START_NOT_STICKY
    }

    private fun handleStartSession(sessionId: String, startRealtimeMs: Long) {
        if (activeSessionId == sessionId) return // Idempotent

        activeSessionId = sessionId
        sessionStartRealtimeMs = startRealtimeMs

        // 1. Immediately start foreground with initial notification
        val initialNotification = notificationManager.buildNotification(
            session = activeSession,
            sample = latestSample,
            elapsedMs = (timeSource.elapsedRealtime() - startRealtimeMs).coerceAtLeast(0L),
            isDebouncing = false,
            sampleCount = currentSampleCount,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                MeasurementNotificationManager.NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(MeasurementNotificationManager.NOTIFICATION_ID, initialNotification)
        }

        // 2. Launch sampling loop in serviceScope
        serviceScope.launch {
            samplingRepository.startSampling(sessionId, startRealtimeMs, serviceScope)
        }

        // 3. Observe SessionState transitions
        sessionStateJob?.cancel()
        sessionStateJob = serviceScope.launch {
            sessionRepository.sessionState.collect { state ->
                when (state) {
                    is SessionState.Active -> {
                        activeSession = state.session
                        isDebouncing = false
                        currentSampleCount = state.sampleCount
                        updateNotification()
                    }

                    is SessionState.UnpluggedPending -> {
                        activeSession = state.activeState.session
                        isDebouncing = true
                        currentSampleCount = state.activeState.sampleCount
                        updateNotification()
                    }

                    is SessionState.Completed -> {
                        stopSamplingAndShutdown()
                    }

                    is SessionState.Idle -> {
                        // Idle state
                    }
                }
            }
        }

        // 4. Observe sampleStream to feed ticks and update notification
        sampleCollectionJob?.cancel()
        sampleCollectionJob = serviceScope.launch {
            samplingRepository.sampleStream
                .filter { it.sessionId == sessionId }
                .collect { sample ->
                    latestSample = sample
                    val snapshot = BatterySnapshot(
                        timestamp = sample.timestamp,
                        percent = sample.percent,
                        voltageMv = sample.voltageMv,
                        currentNowUa = sample.currentNowUa,
                        currentAverageUa = sample.currentAverageUa,
                        chargeCounterUah = sample.chargeCounterUah,
                        energyCounterNwh = sample.energyCounterNwh,
                        temperatureDeciC = sample.temperatureDeciC,
                        batteryStatus = sample.batteryStatus,
                        pluggedType = sample.pluggedType,
                        cycleCount = sample.cycleCount,
                        qualityFlags = sample.qualityFlags,
                    )
                    sessionRepository.onBatteryTick(snapshot)
                    updateNotification()
                }
        }
    }

    private fun updateNotification() {
        val elapsedMs = (timeSource.elapsedRealtime() - sessionStartRealtimeMs).coerceAtLeast(0L)
        val notification = notificationManager.buildNotification(
            session = activeSession,
            sample = latestSample,
            elapsedMs = elapsedMs,
            isDebouncing = isDebouncing,
            sampleCount = currentSampleCount,
        )
        systemNotificationManager.notify(MeasurementNotificationManager.NOTIFICATION_ID, notification)
    }

    private fun handleStopSession() {
        serviceScope.launch {
            sessionRepository.stopSession()
        }
    }

    private fun handleStopService() {
        stopSamplingAndShutdown()
    }

    private fun stopSamplingAndShutdown() {
        sessionStateJob?.cancel()
        sampleCollectionJob?.cancel()

        serviceScope.launch {
            samplingRepository.stopSampling()
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START_SESSION = "com.example.chargetrack.action.START_SESSION"
        const val ACTION_STOP_SESSION = "com.example.chargetrack.action.STOP_SESSION"
        const val ACTION_STOP_SERVICE = "com.example.chargetrack.action.STOP_SERVICE"

        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_START_REALTIME_MS = "extra_start_realtime_ms"

        fun createStartIntent(context: Context, sessionId: String, startRealtimeMs: Long): Intent {
            return Intent(context, MeasurementService::class.java).apply {
                action = ACTION_START_SESSION
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_START_REALTIME_MS, startRealtimeMs)
            }
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, MeasurementService::class.java).apply {
                action = ACTION_STOP_SESSION
            }
        }
    }
}
