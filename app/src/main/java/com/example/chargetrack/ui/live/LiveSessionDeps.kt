package com.example.chargetrack.ui.live

import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.session.SessionState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Minimum session surface consumed by [LiveSessionViewModel].
 * Implemented by [com.example.chargetrack.data.session.ChargingSessionRepository].
 */
interface SessionProvider {
    val sessionState: StateFlow<SessionState>
    suspend fun stopSession()
    fun resetSession()
}

/**
 * Minimum sample-stream surface consumed by [LiveSessionViewModel].
 * Implemented by [com.example.chargetrack.data.sampling.SamplingRepository].
 */
interface SampleProvider {
    val sampleStream: SharedFlow<BatterySample>
}
