package com.example.chargetrack.domain.system

import com.example.chargetrack.domain.model.SoftwareSnapshot

/**
 * Provider interface for capturing immutable snapshots of the current software and OS environment.
 */
interface SoftwareSnapshotProvider {
    /**
     * Captures the current OS and application version state.
     */
    fun captureCurrentSnapshot(): SoftwareSnapshot
}
