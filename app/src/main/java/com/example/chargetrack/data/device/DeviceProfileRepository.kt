package com.example.chargetrack.data.device

import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.DeviceProfileEntity
import com.example.chargetrack.data.db.mapper.toDomain
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.DeviceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceProfileRepository @Inject constructor(
    private val database: AppDatabase,
) {

    fun getProfileFlow(): Flow<DeviceProfile?> {
        return database.deviceProfileDao().getProfileFlow().map { it?.toDomain() }
    }

    suspend fun getProfile(): DeviceProfile? {
        return database.deviceProfileDao().getProfile()?.toDomain()
    }

    suspend fun updateUserMetadata(
        nickname: String?,
        purchaseDate: LocalDate?,
        firstUseDate: LocalDate?,
        ramStorageVariant: String?,
        notes: String?,
    ) {
        val current = database.deviceProfileDao().getProfile() ?: return
        val updated = current.copy(
            nickname = nickname?.takeIf { it.isNotBlank() },
            purchaseDate = purchaseDate,
            firstUseDate = firstUseDate,
            ramStorageVariant = ramStorageVariant?.takeIf { it.isNotBlank() },
            notes = notes?.takeIf { it.isNotBlank() },
            updatedAt = Instant.now(),
        )
        database.deviceProfileDao().insertOrUpdate(updated)
    }

    fun getSavedSetupsFlow(): Flow<List<ChargingSetup>> {
        return database.chargingSetupDao().getAllSetupsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
