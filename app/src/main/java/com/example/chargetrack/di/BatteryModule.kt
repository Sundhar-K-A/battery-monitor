package com.example.chargetrack.di

import com.example.chargetrack.data.battery.BatteryManagerDataSource
import com.example.chargetrack.domain.battery.BatteryDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BatteryModule {

    @Binds
    @Singleton
    abstract fun bindBatteryDataSource(
        impl: BatteryManagerDataSource
    ): BatteryDataSource
}
