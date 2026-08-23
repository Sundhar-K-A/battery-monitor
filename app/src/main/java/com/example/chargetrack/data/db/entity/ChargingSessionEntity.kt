package com.example.chargetrack.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "charging_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ChargingSetupEntity::class,
            parentColumns = ["id"],
            childColumns = ["chargingSetupId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = SoftwareSnapshotEntity::class,
            parentColumns = ["id"],
            childColumns = ["softwareSnapshotId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("startedAt"),
        Index("chargingSetupId"),
        Index("softwareSnapshotId"),
        Index("testType"),
    ],
)
data class ChargingSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val startPercent: Int,
    val endPercent: Int? = null,
    val chargingSetupId: String,
    val softwareSnapshotId: String,
    val testType: TestType = TestType.FREE_FORM,
    val userNotes: String? = null,
    val endReason: SessionEndReason? = null,
)
