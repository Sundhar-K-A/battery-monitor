package com.example.chargetrack.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.chargetrack.domain.enums.DataQuality
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "charge_transitions",
    foreignKeys = [
        ForeignKey(
            entity = ChargingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("sessionId")
    ],
)
data class ChargeTransitionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val fromPercent: Int,
    val toPercent: Int,
    val startedAt: Instant,
    val endedAt: Instant,
    val durationMs: Long,
    val averagePowerUw: Long? = null,
    val medianPowerUw: Long? = null,
    val peakPowerUw: Long? = null,
    val averageTemperatureDeciC: Int? = null,
    val maxTemperatureDeciC: Int? = null,
    val sampleCount: Int,
    val quality: DataQuality = DataQuality.GOOD,
)
