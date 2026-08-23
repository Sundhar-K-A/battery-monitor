package com.example.chargetrack.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.chargetrack.domain.enums.TestValidity
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "standard_tests",
    foreignKeys = [
        ForeignKey(
            entity = ChargingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["sessionId"], unique = true),
        Index(value = ["comparisonGroupKey", "isBaseline"]),
    ],
)
data class StandardTestEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val targetStartPercent: Int = 20,
    val targetEndPercent: Int = 80,
    val isBaseline: Boolean = false,
    val baselineSetAt: Instant? = null,
    val comparisonGroupKey: String = "standard_20_80_wired_official",
    val validity: TestValidity = TestValidity.VALID,
    val invalidationReason: String? = null,
    val benchmarkStartedElapsedMs: Long? = null,
    val benchmarkEndedElapsedMs: Long? = null,
)
