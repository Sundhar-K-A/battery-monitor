package com.example.chargetrack.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Persisted device profile combining three distinct data categories:
 *
 * 1. **Device-reported** — read from Android [android.os.Build] at first launch.
 * 2. **Manufacturer reference** — static iQOO 15 spec values; never derived from runtime.
 * 3. **User-entered** — purchase date, nickname, etc.; must not be silently overwritten.
 *
 * Room TypeConverters for [Instant] and [LocalDate] are added in Prompt 06.
 */
data class DeviceProfile(
    val id: String = UUID.randomUUID().toString(),

    // ── 1. Device-reported Build fields ──────────────────────────────────
    /** android.os.Build.MANUFACTURER */
    val manufacturer: String,
    /** android.os.Build.BRAND */
    val brand: String,
    /** android.os.Build.MODEL */
    val model: String,
    /** android.os.Build.DEVICE */
    val device: String,
    /** android.os.Build.PRODUCT */
    val product: String,
    /** android.os.Build.VERSION.RELEASE */
    val androidVersion: String,
    /** android.os.Build.VERSION.SDK_INT */
    val sdkInt: Int,
    /** OriginOS version string from Build extras if available; null on non-iQOO/vivo devices. */
    val originOsVersion: String? = null,
    /** android.os.Build.FINGERPRINT for software-version correlation. */
    val buildFingerprint: String? = null,

    // ── 2. Manufacturer reference data (iQOO 15 spec — NOT runtime measurements) ──
    /** Typical (marketing) battery capacity. iQOO 15: 7000 mAh. */
    val typicalCapacityMah: Int = 7000,
    /** Rated battery capacity. iQOO 15: 6830 mAh. */
    val ratedCapacityMah: Int = 6830,
    /** Typical energy. iQOO 15: 26.25 Wh. */
    val typicalEnergyWh: Double = 26.25,
    /** Rated energy. iQOO 15: 25.62 Wh. */
    val ratedEnergyWh: Double = 25.62,
    /** Max wired charging power per manufacturer spec. iQOO 15: 100 W. */
    val wiredReferenceW: Int = 100,
    /** Max wireless charging power per manufacturer spec. iQOO 15: 40 W. */
    val wirelessReferenceW: Int = 40,

    // ── 3. User-entered fields ────────────────────────────────────────────
    val nickname: String? = null,
    val purchaseDate: LocalDate? = null,
    val firstUseDate: LocalDate? = null,
    val ramStorageVariant: String? = null,
    val notes: String? = null,

    // ── Record metadata ───────────────────────────────────────────────────
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
