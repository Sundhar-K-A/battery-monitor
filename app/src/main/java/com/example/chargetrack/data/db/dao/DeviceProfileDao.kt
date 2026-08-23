package com.example.chargetrack.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.chargetrack.data.db.entity.DeviceProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceProfileDao {

    @Query("SELECT * FROM device_profiles LIMIT 1")
    fun getProfileFlow(): Flow<DeviceProfileEntity?>

    @Query("SELECT * FROM device_profiles LIMIT 1")
    suspend fun getProfile(): DeviceProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: DeviceProfileEntity)

    @Query("DELETE FROM device_profiles")
    suspend fun deleteAll()
}
