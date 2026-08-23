package com.example.chargetrack.data.db.converter

import androidx.room.TypeConverter
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.domain.enums.QualityFlag
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.enums.TestValidity
import java.time.Instant
import java.time.LocalDate

/**
 * Room TypeConverters for all non-primitive domain types.
 *
 * Conventions:
 * - [Instant] ↔ epoch milliseconds (Long) — preserves sub-second precision.
 * - [LocalDate] ↔ epoch day (Long).
 * - Enums ↔ token-safe strings with safe fallbacks (resilient to renaming / additions).
 * - [Set]<[QualityFlag]> ↔ pipe-separated tokens ("outlier|gap_detected").
 *   Empty set ↔ empty string "".
 */
class RoomTypeConverters {

    // ── Instant ───────────────────────────────────────────────────────────
    @TypeConverter fun instantToEpochMs(v: Instant?): Long? = v?.toEpochMilli()
    @TypeConverter fun epochMsToInstant(v: Long?): Instant? = v?.let { Instant.ofEpochMilli(it) }

    // ── LocalDate ─────────────────────────────────────────────────────────
    @TypeConverter fun localDateToEpochDay(v: LocalDate?): Long? = v?.toEpochDay()
    @TypeConverter fun epochDayToLocalDate(v: Long?): LocalDate? = v?.let { LocalDate.ofEpochDay(it) }

    // ── Set<QualityFlag> ──────────────────────────────────────────────────
    @TypeConverter
    fun qualityFlagsToString(flags: Set<QualityFlag>?): String =
        flags?.joinToString("|") { flagToToken(it) } ?: ""

    @TypeConverter
    fun stringToQualityFlags(value: String?): Set<QualityFlag> {
        if (value.isNullOrEmpty()) return emptySet()
        return value.split("|")
            .mapNotNull { tokenToFlag(it) }
            .toSet()
    }

    private fun flagToToken(flag: QualityFlag): String = when (flag) {
        QualityFlag.GAP_DETECTED -> "gap_detected"
        QualityFlag.MISSING_REQUIRED_VALUE -> "missing_required_value"
        QualityFlag.OUTLIER -> "outlier"
        QualityFlag.PERCENTAGE_JITTER -> "percentage_jitter"
        QualityFlag.SERVICE_INTERRUPTED -> "service_interrupted"
    }

    private fun tokenToFlag(token: String): QualityFlag? = when (token) {
        "gap_detected" -> QualityFlag.GAP_DETECTED
        "missing_required_value" -> QualityFlag.MISSING_REQUIRED_VALUE
        "outlier" -> QualityFlag.OUTLIER
        "percentage_jitter" -> QualityFlag.PERCENTAGE_JITTER
        "service_interrupted" -> QualityFlag.SERVICE_INTERRUPTED
        else -> QualityFlag.entries.find { it.name.equals(token, ignoreCase = true) }
    }

    // ── ChargingType ──────────────────────────────────────────────────────
    @TypeConverter
    fun chargingTypeToString(v: ChargingType?): String? = when (v) {
        ChargingType.WIRED -> "wired"
        ChargingType.WIRELESS -> "wireless"
        ChargingType.UNKNOWN -> "unknown"
        null -> null
    }

    @TypeConverter
    fun stringToChargingType(v: String?): ChargingType? = when (v) {
        "wired" -> ChargingType.WIRED
        "wireless" -> ChargingType.WIRELESS
        "unknown" -> ChargingType.UNKNOWN
        null -> null
        else -> ChargingType.UNKNOWN
    }

    // ── ChargingMode ──────────────────────────────────────────────────────
    @TypeConverter
    fun chargingModeToString(v: ChargingMode?): String? = when (v) {
        ChargingMode.NORMAL -> "normal"
        ChargingMode.FLASH_CHARGE -> "flash_charge"
        ChargingMode.BYPASS -> "bypass"
        ChargingMode.OTHER -> "other"
        ChargingMode.UNKNOWN -> "unknown"
        null -> null
    }

    @TypeConverter
    fun stringToChargingMode(v: String?): ChargingMode? = when (v) {
        "normal" -> ChargingMode.NORMAL
        "flash_charge" -> ChargingMode.FLASH_CHARGE
        "bypass" -> ChargingMode.BYPASS
        "other" -> ChargingMode.OTHER
        "unknown" -> ChargingMode.UNKNOWN
        null -> null
        else -> ChargingMode.UNKNOWN
    }

    // ── SessionEndReason ──────────────────────────────────────────────────
    @TypeConverter
    fun sessionEndReasonToString(v: SessionEndReason?): String? = when (v) {
        SessionEndReason.CHARGING_STOPPED -> "charging_stopped"
        SessionEndReason.UNPLUGGED -> "unplugged"
        SessionEndReason.USER_STOPPED -> "user_stopped"
        SessionEndReason.MEASUREMENT_LOST -> "measurement_lost"
        SessionEndReason.DEVICE_RESTARTED -> "device_restarted"
        SessionEndReason.UNKNOWN -> "unknown"
        null -> null
    }

    @TypeConverter
    fun stringToSessionEndReason(v: String?): SessionEndReason? = when (v) {
        "charging_stopped" -> SessionEndReason.CHARGING_STOPPED
        "unplugged" -> SessionEndReason.UNPLUGGED
        "user_stopped" -> SessionEndReason.USER_STOPPED
        "measurement_lost" -> SessionEndReason.MEASUREMENT_LOST
        "device_restarted" -> SessionEndReason.DEVICE_RESTARTED
        "unknown" -> SessionEndReason.UNKNOWN
        null -> null
        else -> SessionEndReason.UNKNOWN
    }

    // ── TestType ──────────────────────────────────────────────────────────
    @TypeConverter
    fun testTypeToString(v: TestType?): String? = when (v) {
        TestType.STANDARD -> "standard"
        TestType.FREE_FORM -> "free_form"
        null -> null
    }

    @TypeConverter
    fun stringToTestType(v: String?): TestType? = when (v) {
        "standard" -> TestType.STANDARD
        "free_form" -> TestType.FREE_FORM
        null -> null
        else -> TestType.FREE_FORM
    }

    // ── DataQuality ───────────────────────────────────────────────────────
    @TypeConverter
    fun dataQualityToString(v: DataQuality?): String? = when (v) {
        DataQuality.GOOD -> "good"
        DataQuality.DEGRADED -> "degraded"
        DataQuality.INSUFFICIENT -> "insufficient"
        null -> null
    }

    @TypeConverter
    fun stringToDataQuality(v: String?): DataQuality? = when (v) {
        "good" -> DataQuality.GOOD
        "degraded" -> DataQuality.DEGRADED
        "insufficient" -> DataQuality.INSUFFICIENT
        null -> null
        else -> DataQuality.INSUFFICIENT
    }

    // ── TestValidity ──────────────────────────────────────────────────────
    @TypeConverter
    fun testValidityToString(v: TestValidity?): String? = when (v) {
        TestValidity.VALID -> "valid"
        TestValidity.QUESTIONABLE -> "questionable"
        TestValidity.INVALID -> "invalid"
        null -> null
    }

    @TypeConverter
    fun stringToTestValidity(v: String?): TestValidity? = when (v) {
        "valid" -> TestValidity.VALID
        "questionable" -> TestValidity.QUESTIONABLE
        "invalid" -> TestValidity.INVALID
        null -> null
        else -> TestValidity.INVALID
    }
}
