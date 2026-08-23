package com.example.chargetrack.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.chargetrack.domain.enums.QualityFlag
import java.time.Instant
import java.util.UUID

/**
 * Room entity for [com.example.chargetrack.domain.model.BatterySample].
 *
 * Raw measurement values are always stored. [derivedPowerUw] is stored alongside
 * the raw inputs to optimize chart queries.
 * Null means the field was unavailable from the device; zero is never a sentinel.
 */
@Entity(
    tableName = "battery_samples",
    foreignKeys = [
        ForeignKey(
            entity = ChargingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("sessionId"),
        Index("timestamp"),
        Index(value = ["sessionId", "elapsedMs"], unique = true),
    ],
)
data class BatterySampleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val timestamp: Instant,
    val elapsedMs: Long,
    val percent: Int? = null,
    val voltageMv: Int? = null,
    val currentNowUa: Int? = null,
    val currentAverageUa: Int? = null,
    val chargeCounterUah: Int? = null,
    val energyCounterNwh: Long? = null,
    val temperatureDeciC: Int? = null,
    val batteryStatus: Int? = null,
    val pluggedType: Int? = null,
    val cycleCount: Int? = null,
    val derivedPowerUw: Long? = null,
    val qualityFlags: Set<QualityFlag> = emptySet(),
)
