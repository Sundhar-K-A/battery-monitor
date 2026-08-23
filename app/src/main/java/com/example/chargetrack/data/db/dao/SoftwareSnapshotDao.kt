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

    @Query("SELECT * FROM software_snapshots WHERE id = :id")
    suspend fun getById(id: String): SoftwareSnapshotEntity?

    @Query("SELECT * FROM software_snapshots ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getLatest(): SoftwareSnapshotEntity?

    @Query("DELETE FROM software_snapshots WHERE id = :id")
    suspend fun delete(id: String)
}
