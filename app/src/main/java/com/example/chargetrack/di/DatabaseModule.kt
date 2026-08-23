package com.example.chargetrack.di

import android.content.Context
import androidx.room.Room
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.dao.BatterySampleDao
import com.example.chargetrack.data.db.dao.ChargeTransitionDao
import com.example.chargetrack.data.db.dao.ChargingSessionDao
import com.example.chargetrack.data.db.dao.ChargingSetupDao
import com.example.chargetrack.data.db.dao.DeviceProfileDao
import com.example.chargetrack.data.db.dao.SoftwareSnapshotDao
import com.example.chargetrack.data.db.dao.StandardTestDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME
    ).build()

    @Provides
    fun provideDeviceProfileDao(database: AppDatabase): DeviceProfileDao =
        database.deviceProfileDao()

    @Provides
    fun provideSoftwareSnapshotDao(database: AppDatabase): SoftwareSnapshotDao =
        database.softwareSnapshotDao()

    @Provides
    fun provideChargingSetupDao(database: AppDatabase): ChargingSetupDao =
        database.chargingSetupDao()

    @Provides
    fun provideChargingSessionDao(database: AppDatabase): ChargingSessionDao =
        database.chargingSessionDao()

    @Provides
    fun provideBatterySampleDao(database: AppDatabase): BatterySampleDao =
        database.batterySampleDao()

    @Provides
    fun provideChargeTransitionDao(database: AppDatabase): ChargeTransitionDao =
        database.chargeTransitionDao()

    @Provides
    fun provideStandardTestDao(database: AppDatabase): StandardTestDao =
        database.standardTestDao()
}
