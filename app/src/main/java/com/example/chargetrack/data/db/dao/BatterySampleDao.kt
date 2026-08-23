package com.example.chargetrack.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.chargetrack.data.db.entity.BatterySampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatterySampleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSample(sample: BatterySampleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSamples(samples: List<BatterySampleEntity>): List<Long>

    @Query("SELECT * FROM battery_samples WHERE sessionId = :sessionId ORDER BY elapsedMs ASC")
    fun getSamplesForSessionFlow(sessionId: String): Flow<List<BatterySampleEntity>>

    @Query("SELECT * FROM battery_samples WHERE sessionId = :sessionId ORDER BY elapsedMs ASC")
    suspend fun getSamplesForSessionOrdered(sessionId: String): List<BatterySampleEntity>

    @Query("SELECT * FROM battery_samples WHERE sessionId = :sessionId ORDER BY elapsedMs DESC LIMIT :limit")
    suspend fun getRecentSamplesForSession(sessionId: String, limit: Int): List<BatterySampleEntity>

    @Query("SELECT COUNT(*) FROM battery_samples WHERE sessionId = :sessionId")
    suspend fun getSampleCountForSession(sessionId: String): Int

    @Query("DELETE FROM battery_samples WHERE sessionId = :sessionId")
    suspend fun deleteSamplesForSession(sessionId: String)
}
