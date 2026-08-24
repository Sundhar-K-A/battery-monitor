package com.example.chargetrack.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.chargetrack.data.db.entity.ChargingSessionEntity
import com.example.chargetrack.domain.enums.SessionEndReason
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ChargingSessionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: ChargingSessionEntity): Long

    @Update
    suspend fun update(session: ChargingSessionEntity)

    @Query("SELECT * FROM charging_sessions WHERE id = :id")
    suspend fun getById(id: String): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions WHERE id = :id")
    fun getByIdFlow(id: String): Flow<ChargingSessionEntity?>

    @Query("SELECT * FROM charging_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun getActiveSessionFlow(): Flow<ChargingSessionEntity?>

    @Query("SELECT * FROM charging_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveSession(): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions ORDER BY startedAt DESC")
    fun getAllSessionsFlow(): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions WHERE endPercent = 100 ORDER BY startedAt DESC")
    suspend fun getSessionsReachedFull(): List<ChargingSessionEntity>

    @Query("UPDATE charging_sessions SET endedAt = :endedAt, endPercent = :endPercent, endReason = :endReason WHERE id = :sessionId")
    suspend fun updateSessionEnd(sessionId: String, endedAt: Instant, endPercent: Int?, endReason: SessionEndReason)

    @Transaction
    suspend fun finalizeSession(sessionId: String, endedAt: Instant, endPercent: Int?, endReason: SessionEndReason) {
        updateSessionEnd(sessionId, endedAt, endPercent, endReason)
    }

    @Query("DELETE FROM charging_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)
}
