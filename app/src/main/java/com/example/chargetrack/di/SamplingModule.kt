package com.example.chargetrack.di

import com.example.chargetrack.data.db.dao.BatterySampleDao
import com.example.chargetrack.data.sampling.SamplingRepository
import com.example.chargetrack.domain.battery.BatteryDataSource
import com.example.chargetrack.domain.sampling.BatterySampler
import com.example.chargetrack.domain.sampling.OutlierThresholds
import com.example.chargetrack.domain.session.SessionConfig
import com.example.chargetrack.domain.time.TimeSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SamplingModule {

    @Provides
    @Singleton
    fun provideOutlierThresholds(): OutlierThresholds = OutlierThresholds()

    @Provides
    @Singleton
    fun provideBatterySampler(
        batteryDataSource: BatteryDataSource,
        timeSource: TimeSource,
        config: SessionConfig,
        outlierThresholds: OutlierThresholds,
    ): BatterySampler = BatterySampler(batteryDataSource, timeSource, config, outlierThresholds)

    @Provides
    @Singleton
    fun provideSamplingRepository(
        batterySampler: BatterySampler,
        batterySampleDao: BatterySampleDao,
    ): SamplingRepository = SamplingRepository(batterySampler, batterySampleDao, Dispatchers.IO)
}
