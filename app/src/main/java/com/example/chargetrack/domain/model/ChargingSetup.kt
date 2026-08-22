package com.example.chargetrack.domain.model

import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import java.time.Instant
import java.util.UUID

/**
 * Describes the charger, cable, and mode used during a charging session.
 *
 * ## Immutability contract
 * Once [isFrozen] is `true`, this record must not be mutated.
 * A setup is frozen when it is first referenced by a completed session,
 * preserving accurate historical test conditions.
 * If the user later changes charger or cable details, a **new** [ChargingSetup]
 * record must be created — do not update the existing frozen record.
 *
 * ## FlashCharge detection
 * [chargingMode] = [ChargingMode.FLASH_CHARGE] must only be set by explicit user action.
 * Do not auto-detect proprietary iQOO FlashCharge mode from measured power or current.
 */
data class ChargingSetup(
    val id: String = UUID.randomUUID().toString(),
    val chargerBrand: String? = null,
    val chargerModel: String? = null,
    /** Advertised output wattage from the charger label — not a measured value. */
    val advertisedWattageW: Int? = null,
    /** Charging protocol label as entered by the user (e.g. "PD 3.0", "PPS"). */
    val protocol: String? = null,
    val isOfficialCharger: Boolean = false,
    val cableBrand: String? = null,
    val cableModel: String? = null,
    val isOfficialCable: Boolean = false,
    val chargingType: ChargingType = ChargingType.WIRED,
    val chargingMode: ChargingMode = ChargingMode.UNKNOWN,
    val notes: String? = null,
    val createdAt: Instant = Instant.now(),
    /**
     * `true` once this setup is referenced by a completed session.
     * Frozen records must not be edited. Create a new record for any changes.
     */
    val isFrozen: Boolean = false
)
