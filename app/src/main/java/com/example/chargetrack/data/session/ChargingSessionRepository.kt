package com.example.chargetrack.data.session

import androidx.room.withTransaction
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.StandardTestEntity
import com.example.chargetrack.data.db.mapper.toDomain
import com.example.chargetrack.data.db.mapper.toEntity
import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.model.ChargingSession
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.session.SessionConfig
import com.example.chargetrack.domain.session.SessionEvent
import com.example.chargetrack.domain.session.SessionState
import com.example.chargetrack.domain.session.SessionStateMachine
import com.example.chargetrack.domain.time.DefaultTimeSource
import com.example.chargetrack.domain.time.TimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository and coordinator for charging session lifecycle management and database persistence.
 *
 * Responsibilities:
 * - Bridges the deterministic [SessionStateMachine] with Room persistence.
 * - Guarantees atomic session creation and finalization via [AppDatabase.withTransaction].
 * - Detects and finalizes in-flight sessions after device reboot with [SessionEndReason.DEVICE_RESTARTED].
 * - Exposes a thread-safe [sessionState] Flow.
 */
@Singleton
class ChargingSessionRepository @Inject constructor(
    private val database: AppDatabase,
    private val config: SessionConfig = SessionConfig(),
    private val timeSource: TimeSource = DefaultTimeSource(),
) {
    private val stateMachine = SessionStateMachine(config, timeSource)
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val mutex = Mutex()

    /**
     * Atomically starts a charging session if confirmed charging evidence is present.
     *
     * Persists:
     * 1. [SoftwareSnapshot]
     * 2. Immutable [ChargingSetup] session snapshot (`isTemplate = false`)
     * 3. [ChargingSession]
     * 4. [StandardTestEntity] (if [testType] == [TestType.STANDARD])
     */
    suspend fun startSession(
        snapshot: BatterySnapshot,
        setup: ChargingSetup,
        softwareSnapshot: SoftwareSnapshot,
        testType: TestType = TestType.FREE_FORM,
        userNotes: String? = null,
        comparisonGroupKey: String = "standard_20_80_wired_official",
    ): Result<ChargingSession> = mutex.withLock {
        val currentState = _sessionState.value
        val event = SessionEvent.StartSession(
            snapshot = snapshot,
            setup = setup,
            softwareSnapshot = softwareSnapshot,
            testType = testType,
            userNotes = userNotes,
        )

        val nextState = stateMachine.transition(currentState, event)
        if (nextState is SessionState.Active && currentState !is SessionState.Active) {
            // Atomically persist records in Room
            val session = nextState.session
            val sessionSetupSnapshot = setup.copy(
                id = UUID.randomUUID().toString(),
                isFrozen = true,
            )
            val updatedSession = session.copy(chargingSetupId = sessionSetupSnapshot.id)

            database.withTransaction {
                // 1. Snapshot software environment
                database.softwareSnapshotDao().insert(softwareSnapshot.toEntity())

                // 2. Persist immutable setup snapshot
                database.chargingSetupDao().insert(sessionSetupSnapshot.toEntity(isTemplate = false))

                // 3. Persist session entity
                database.chargingSessionDao().insert(updatedSession.toEntity())

                // 4. If Standard Test, persist standard test entity
                if (testType == TestType.STANDARD) {
                    val standardTest = StandardTestEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = updatedSession.id,
                        targetStartPercent = 20,
                        targetEndPercent = 80,
                        comparisonGroupKey = comparisonGroupKey,
                    )
                    database.standardTestDao().insert(standardTest)
                }
            }

            _sessionState.value = nextState.copy(
                session = updatedSession,
                setupSnapshot = sessionSetupSnapshot,
            )
            Result.success(updatedSession)
        } else {
            Result.failure(IllegalStateException("Cannot start session: charging condition not met or session already active"))
        }
    }

    /**
     * Ingests a battery measurement tick into the session state machine.
     */
    suspend fun onBatteryTick(snapshot: BatterySnapshot) = mutex.withLock {
        val previous = _sessionState.value
        val next = stateMachine.transition(previous, SessionEvent.BatteryTick(snapshot))
        handleStateTransition(previous, next)
    }

    /**
     * Ingests a timer tick for gap detection and debounce expiration.
     */
    suspend fun onTimeTick(currentTime: Instant, elapsedRealtimeMs: Long) = mutex.withLock {
        val previous = _sessionState.value
        val next = stateMachine.transition(previous, SessionEvent.TimeTick(currentTime, elapsedRealtimeMs))
        handleStateTransition(previous, next)
    }

    /**
     * User requested manual termination of the active session.
     */
    suspend fun stopSession() = mutex.withLock {
        val previous = _sessionState.value
        val next = stateMachine.transition(previous, SessionEvent.UserStop(timeSource.now(), timeSource.elapsedRealtime()))
        handleStateTransition(previous, next)
    }

    /**
     * Recovers and cleans up any abandoned active session left in the database after a device reboot.
     */
    suspend fun recoverOrFinalizeRebootedSession(): ChargingSession? = mutex.withLock {
        val activeEntity = database.chargingSessionDao().getActiveSession()
        if (activeEntity != null) {
            val now = timeSource.now()
            database.withTransaction {
                database.chargingSessionDao().finalizeSession(
                    sessionId = activeEntity.id,
                    endedAt = now,
                    endPercent = activeEntity.endPercent,
                    endReason = SessionEndReason.DEVICE_RESTARTED,
                )
            }
            _sessionState.value = SessionState.Idle
            activeEntity.toDomain().copy(
                endedAt = now,
                endReason = SessionEndReason.DEVICE_RESTARTED,
            )
        } else {
            null
        }
    }

    /**
     * Resets a completed session back to [SessionState.Idle].
     */
    fun resetSession() {
        _sessionState.value = stateMachine.transition(_sessionState.value, SessionEvent.Reset)
    }

    private suspend fun handleStateTransition(previous: SessionState, next: SessionState) {
        if (previous != next) {
            _sessionState.value = next
            if (next is SessionState.Completed && previous !is SessionState.Completed) {
                // Finalize session in Room transaction
                val session = next.session
                database.withTransaction {
                    database.chargingSessionDao().finalizeSession(
                        sessionId = session.id,
                        endedAt = session.endedAt ?: timeSource.now(),
                        endPercent = session.endPercent,
                        endReason = session.endReason ?: SessionEndReason.UNKNOWN,
                    )
                }
            }
        }
    }
}
