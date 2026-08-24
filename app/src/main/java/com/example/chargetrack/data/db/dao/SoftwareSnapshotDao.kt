package com.example.chargetrack.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.chargetrack.data.db.entity.SoftwareSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SoftwareSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(snapshot: SoftwareSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(snapshot: SoftwareSnapshotEntity): Long

    @Query("SELECT * FROM software_snapshots WHERE id = :id")
    suspend fun getById(id: String): SoftwareSnapshotEntity?

    @Query("SELECT * FROM software_snapshots ORDER BY capturedAt ASC")
    fun getAllSnapshotsFlow(): Flow<List<SoftwareSnapshotEntity>>

    @Query("SELECT * FROM software_snapshots ORDER BY capturedAt ASC")
    suspend fun getAllSnapshots(): List<SoftwareSnapshotEntity>

    @Query("DELETE FROM software_snapshots WHERE id = :id")
    suspend fun delete(id: String)
}
