package com.example.chargetrack.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "software_snapshots",
    indices = [
        Index("capturedAt")
    ]
)
data class SoftwareSnapshotEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val capturedAt: Instant,
    val androidVersion: String,
    val sdkInt: Int,
    val originOsVersion: String? = null,
    val buildFingerprint: String,
    val appVersionName: String,
    val appVersionCode: Int,
)
