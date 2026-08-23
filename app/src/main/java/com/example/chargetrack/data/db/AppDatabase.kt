package com.example.chargetrack.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.chargetrack.data.db.converter.RoomTypeConverters
import com.example.chargetrack.data.db.dao.BatterySampleDao
import com.example.chargetrack.data.db.dao.ChargeTransitionDao
import com.example.chargetrack.data.db.dao.ChargingSessionDao
import com.example.chargetrack.data.db.dao.ChargingSetupDao
import com.example.chargetrack.data.db.dao.DeviceProfileDao
import com.example.chargetrack.data.db.dao.SoftwareSnapshotDao
import com.example.chargetrack.data.db.dao.StandardTestDao
import com.example.chargetrack.data.db.entity.BatterySampleEntity
import com.example.chargetrack.data.db.entity.ChargeTransitionEntity
import com.example.chargetrack.data.db.entity.ChargingSessionEntity
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import com.example.chargetrack.data.db.entity.DeviceProfileEntity
import com.example.chargetrack.data.db.entity.SoftwareSnapshotEntity
import com.example.chargetrack.data.db.entity.StandardTestEntity

@Database(
    entities = [
        DeviceProfileEntity::class,
        SoftwareSnapshotEntity::class,
        ChargingSetupEntity::class,
        ChargingSessionEntity::class,
        BatterySampleEntity::class,
        ChargeTransitionEntity::class,
        StandardTestEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceProfileDao(): DeviceProfileDao
    abstract fun softwareSnapshotDao(): SoftwareSnapshotDao
    abstract fun chargingSetupDao(): ChargingSetupDao
    abstract fun chargingSessionDao(): ChargingSessionDao
    abstract fun batterySampleDao(): BatterySampleDao
    abstract fun chargeTransitionDao(): ChargeTransitionDao
    abstract fun standardTestDao(): StandardTestDao

    companion object {
        const val DATABASE_NAME = "chargetrack.db"
    }
}
