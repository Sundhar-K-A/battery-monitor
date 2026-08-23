package com.example.chargetrack.domain.time

import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Provides access to the operating system kernel's unique boot identifier.
 *
 * Used for establishing reliable, evidence-based device reboot boundaries
 * without relying on fallible wall-clock heuristic comparisons.
 */
interface BootInfoProvider {
    /**
     * Returns the unique identifier for the current device boot, or a stable fallback
     * if the kernel boot ID cannot be read.
     */
    fun getBootId(): String?
}

/**
 * Standard implementation of [BootInfoProvider] reading `/proc/sys/kernel/random/boot_id`.
 *
 * If `/proc/sys/kernel/random/boot_id` is unavailable, a single stable in-memory UUID is generated
 * and retained for the duration of the current process lifecycle. It is **never** regenerated
 * on subsequent calls within the same process to prevent false-positive reboot classifications.
 */
class DefaultBootInfoProvider : BootInfoProvider {

    private val stableFallbackId: String by lazy {
        UUID.randomUUID().toString()
    }

    override fun getBootId(): String? {
        val bootIdFile = File("/proc/sys/kernel/random/boot_id")
        return if (bootIdFile.exists() && bootIdFile.canRead()) {
            try {
                val content = bootIdFile.readText().trim()
                content.ifEmpty { stableFallbackId }
            } catch (_: IOException) {
                stableFallbackId
            } catch (_: SecurityException) {
                stableFallbackId
            }
        } else {
            stableFallbackId
        }
    }
}
