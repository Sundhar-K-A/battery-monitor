package com.example.chargetrack.data.sampling

import com.example.chargetrack.data.db.dao.BatterySampleDao
import com.example.chargetrack.data.db.mapper.toEntity
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.sampling.BatterySampler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository coordinating [BatterySampler] with Room database persistence.
 *
 * Automatically saves incoming raw samples to [BatterySampleDao] on an IO dispatcher.
 */
@Singleton
class SamplingRepository @Inject constructor(
    private val batterySampler: BatterySampler,
    private val batterySampleDao: BatterySampleDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private var persistenceJob: Job? = null

    val latestSample: StateFlow<BatterySample?> = batterySampler.latestSample
    val isSampling: StateFlow<Boolean> = batterySampler.isSampling

    /**
     * Starts sampling and automatic Room persistence for the given [sessionId].
     */
    suspend fun startSampling(
        sessionId: String,
        startRealtimeMs: Long,
        scope: CoroutineScope,
    ): Job = mutex.withLock {
        // Launch persistence collector
        if (persistenceJob == null || persistenceJob?.isActive != true) {
            persistenceJob = scope.launch(context = ioDispatcher, start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                batterySampler.sampleStream.collect { sample ->
                    batterySampleDao.insertSample(sample.toEntity())
                }
            }
        }

        batterySampler.start(sessionId, startRealtimeMs, scope)
    }

    /**
     * Stops the active sampling loop and persistence collector.
     */
    suspend fun stopSampling() = mutex.withLock {
        batterySampler.stop()
        persistenceJob?.cancel()
        persistenceJob = null
    }

    /**
     * Returns the total count of persisted samples for [sessionId].
     */
    suspend fun getSampleCount(sessionId: String): Int =
        batterySampleDao.getSampleCountForSession(sessionId)
}
