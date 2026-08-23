package com.example.chargetrack.di

import com.example.chargetrack.domain.time.BootInfoProvider
import com.example.chargetrack.domain.time.DefaultBootInfoProvider
import com.example.chargetrack.service.DefaultMeasurementServiceController
import com.example.chargetrack.service.MeasurementServiceController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceBindingModule {

    @Binds
    @Singleton
    abstract fun bindMeasurementServiceController(
        impl: DefaultMeasurementServiceController,
    ): MeasurementServiceController
}

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideBootInfoProvider(): BootInfoProvider = DefaultBootInfoProvider()
}
