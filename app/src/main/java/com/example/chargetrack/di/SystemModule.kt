package com.example.chargetrack.di

import com.example.chargetrack.data.system.DefaultSoftwareSnapshotProvider
import com.example.chargetrack.domain.system.SoftwareSnapshotProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SystemModule {

    @Binds
    @Singleton
    abstract fun bindSoftwareSnapshotProvider(
        impl: DefaultSoftwareSnapshotProvider,
    ): SoftwareSnapshotProvider
}
