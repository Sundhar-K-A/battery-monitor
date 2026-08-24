package com.example.chargetrack.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingSetupDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(setup: ChargingSetupEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(setup: ChargingSetupEntity): Long

    @Update
    suspend fun update(setup: ChargingSetupEntity)

    @Query("SELECT * FROM charging_setups WHERE id = :id")
    suspend fun getById(id: String): ChargingSetupEntity?

    @Query("SELECT * FROM charging_setups WHERE isTemplate = 1 ORDER BY createdAt DESC")
    fun getAllTemplatesFlow(): Flow<List<ChargingSetupEntity>>

    @Query("SELECT * FROM charging_setups ORDER BY createdAt DESC")
    fun getAllSetupsFlow(): Flow<List<ChargingSetupEntity>>

    @Query("DELETE FROM charging_setups WHERE id = :id")
    suspend fun delete(id: String)
}
