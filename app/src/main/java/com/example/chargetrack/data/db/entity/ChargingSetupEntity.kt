package com.example.chargetrack.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import java.time.Instant
import java.util.UUID

/**
 * Room entity for charger/cable configuration.
 *
 * Represents an immutable configuration snapshot when referenced by a session,
 * or a reusable user template preset when [isTemplate] is true.
 */
@Entity(
    tableName = "charging_setups",
    indices = [
        Index("isTemplate")
    ]
)
data class ChargingSetupEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val chargerBrand: String? = null,
    val chargerModel: String? = null,
    val advertisedWattageW: Int? = null,
    val protocol: String? = null,
    val isOfficialCharger: Boolean = false,
    val cableBrand: String? = null,
    val cableModel: String? = null,
    val isOfficialCable: Boolean = false,
    val chargingType: ChargingType = ChargingType.WIRED,
    val chargingMode: ChargingMode = ChargingMode.UNKNOWN,
    val notes: String? = null,
    val createdAt: Instant,
    val isTemplate: Boolean = false,
)
