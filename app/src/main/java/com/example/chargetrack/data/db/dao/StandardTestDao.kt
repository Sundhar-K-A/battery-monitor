package com.example.chargetrack.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.chargetrack.data.db.entity.StandardTestEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface StandardTestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(test: StandardTestEntity): Long

    @Update
    suspend fun update(test: StandardTestEntity)

    @Query("SELECT * FROM standard_tests WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getForSession(sessionId: String): StandardTestEntity?

    @Query("SELECT * FROM standard_tests WHERE sessionId = :sessionId LIMIT 1")
    fun getForSessionFlow(sessionId: String): Flow<StandardTestEntity?>

    @Query("SELECT * FROM standard_tests ORDER BY targetStartPercent ASC")
    fun getAllStandardTestsFlow(): Flow<List<StandardTestEntity>>

    @Query("SELECT * FROM standard_tests WHERE isBaseline = 1")
    fun getBaselineTestsFlow(): Flow<List<StandardTestEntity>>

    @Query("SELECT * FROM standard_tests WHERE comparisonGroupKey = :comparisonGroupKey AND isBaseline = 1 LIMIT 1")
    suspend fun getBaselineForGroup(comparisonGroupKey: String): StandardTestEntity?

    @Query("UPDATE standard_tests SET isBaseline = 0, baselineSetAt = NULL WHERE comparisonGroupKey = :comparisonGroupKey")
    suspend fun clearBaselinesForGroup(comparisonGroupKey: String)

    @Query("UPDATE standard_tests SET isBaseline = 1, baselineSetAt = :baselineSetAt WHERE id = :testId")
    suspend fun setBaseline(testId: String, baselineSetAt: Instant)

    @Transaction
    suspend fun setBaselineForGroup(testId: String, comparisonGroupKey: String, baselineSetAt: Instant) {
        clearBaselinesForGroup(comparisonGroupKey)
        setBaseline(testId, baselineSetAt)
    }

    @Query("DELETE FROM standard_tests WHERE id = :id")
    suspend fun delete(id: String)
}
