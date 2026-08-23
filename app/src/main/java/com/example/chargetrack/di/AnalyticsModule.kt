package com.example.chargetrack.di

import com.example.chargetrack.data.analytics.SessionSummaryRepository
import com.example.chargetrack.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {

    @Provides
    @Singleton
    fun provideSessionSummaryRepository(
        database: AppDatabase,
    ): SessionSummaryRepository = SessionSummaryRepository(database)
}
