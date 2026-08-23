package com.example.chargetrack.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Persisted device profile combining three distinct data categories:
 *
 * 1. **Device-reported** — read from Android [android.os.Build] at first launch.
 * 2. **Manufacturer reference** — static spec values applied only after a confirmed device
 *    match (e.g. iQOO 15). Never derived from runtime measurements. Default null/0
 *    until Prompt 03 populates them for a matched device.
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
    /**
     * Best-effort OriginOS identification derived from public Build fields only
     * (e.g. [android.os.Build.DISPLAY] or [android.os.Build.VERSION.INCREMENTAL]).
     * Null if no OriginOS-pattern string is found. Never reads private Build extras.
     */
    val originOsBuildLabel: String? = null,
    /** android.os.Build.FINGERPRINT */
    val buildFingerprint: String? = null,

    // ── 2. Manufacturer reference data (device-specific spec — NOT runtime measurements) ──
    // Populated only after a confirmed device match in Prompt 03.
    // All null/0 for unrecognised devices; do NOT apply iQOO 15 values generically.
    /** Typical (marketing) battery capacity from manufacturer spec. Null if unknown device. */
    val typicalCapacityMah: Int? = null,
    /** Rated battery capacity from manufacturer spec. Null if unknown device. */
    val ratedCapacityMah: Int? = null,
    /** Typical energy from manufacturer spec in Wh. Null if unknown device. */
    val typicalEnergyWh: Double? = null,
    /** Rated energy from manufacturer spec in Wh. Null if unknown device. */
    val ratedEnergyWh: Double? = null,
    /** Max wired charging power per manufacturer spec in W. Null if unknown device. */
    val wiredReferenceW: Int? = null,
    /** Max wireless charging power per manufacturer spec in W. Null if unknown device. */
    val wirelessReferenceW: Int? = null,

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
