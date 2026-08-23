package com.example.chargetrack.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Room entity for [com.example.chargetrack.domain.model.DeviceProfile].
 *
 * Combines build-reported fields, manufacturer reference spec (iQOO 15),
 * user-entered metadata, and onboarding completion state.
 */
@Entity(tableName = "device_profiles")
data class DeviceProfileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    // ── Build-reported (read-only) ─────────────────────────────────────
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val androidVersion: String,
    val sdkInt: Int,
    val buildFingerprint: String,
    val buildDisplay: String,
    val buildIncremental: String,
    val originOsBuildLabel: String? = null,
    val matchedDeviceName: String? = null,

    // ── Reference spec (iQOO 15 only; null for unknown devices) ───────
    val typicalCapacityMah: Int? = null,
    val ratedCapacityMah: Int? = null,
    val typicalEnergyWh: Double? = null,
    val ratedEnergyWh: Double? = null,
    val wiredReferenceW: Int? = null,
    val wirelessReferenceW: Int? = null,

    // ── User-entered ───────────────────────────────────────────────────
    val nickname: String? = null,
    val purchaseDate: LocalDate? = null,
    val firstUseDate: LocalDate? = null,
    val ramStorageVariant: String? = null,
    val notes: String? = null,

    // ── Lifecycle ─────────────────────────────────────────────────────
    val createdAt: Instant,
    val updatedAt: Instant,
    val isOnboardingComplete: Boolean = false,
)
