package com.example.chargetrack.domain.enums

/**
 * Charging mode as declared by the user.
 *
 * IMPORTANT: Proprietary iQOO FlashCharge mode is NOT automatically detectable via
 * public Android APIs. FLASH_CHARGE must only be set based on explicit user input —
 * never inferred from measured power or current values alone.
 */
enum class ChargingMode {
    NORMAL,
    FLASH_CHARGE,
    BYPASS,
    OTHER,
    UNKNOWN
}
