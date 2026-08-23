package com.example.chargetrack.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.chargetrack.data.db.entity.ChargeTransitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargeTransitionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transition: ChargeTransitionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transitions: List<ChargeTransitionEntity>): List<Long>

    @Query("SELECT * FROM charge_transitions WHERE sessionId = :sessionId ORDER BY fromPercent ASC")
    fun getTransitionsForSessionFlow(sessionId: String): Flow<List<ChargeTransitionEntity>>

    @Query("SELECT * FROM charge_transitions WHERE sessionId = :sessionId ORDER BY fromPercent ASC")
    suspend fun getTransitionsForSession(sessionId: String): List<ChargeTransitionEntity>

    @Query("DELETE FROM charge_transitions WHERE sessionId = :sessionId")
    suspend fun deleteTransitionsForSession(sessionId: String)
}
