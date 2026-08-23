package com.example.chargetrack.di

import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.session.ChargingSessionRepository
import com.example.chargetrack.domain.session.SessionConfig
import com.example.chargetrack.domain.session.SessionStateMachine
import com.example.chargetrack.domain.time.DefaultTimeSource
import com.example.chargetrack.domain.time.TimeSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    @Provides
    @Singleton
    fun provideTimeSource(): TimeSource = DefaultTimeSource()

    @Provides
    @Singleton
    fun provideSessionConfig(): SessionConfig = SessionConfig()

    @Provides
    @Singleton
    fun provideSessionStateMachine(
        config: SessionConfig,
        timeSource: TimeSource,
    ): SessionStateMachine = SessionStateMachine(config, timeSource)

    @Provides
    @Singleton
    fun provideChargingSessionRepository(
        database: AppDatabase,
        config: SessionConfig,
        timeSource: TimeSource,
    ): ChargingSessionRepository = ChargingSessionRepository(database, config, timeSource)
}
