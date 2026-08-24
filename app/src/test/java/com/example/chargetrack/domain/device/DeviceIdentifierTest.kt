package com.example.chargetrack.domain.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentifierTest {

    // ── iQOO 15 positive matches ──────────────────────────────────────────

    @Test
    fun `canonical iQOO 15 build is identified as IQOO_15`() {
        val result = DeviceIdentifier.identify(BuildInfoFakes.iqoo15())
        assertEquals(DeviceIdentifier.KnownDevice.IQOO_15, result)
    }

    @Test
    fun `isIqoo15 returns true for canonical iQOO 15`() {
        assertTrue(DeviceIdentifier.isIqoo15(BuildInfoFakes.iqoo15()))
    }

    @Test
    fun `brand matching is case-insensitive — lowercase iqoo matches`() {
        assertTrue(DeviceIdentifier.isIqoo15(BuildInfoFakes.iqoo15(brand = "iqoo")))
    }

    @Test
    fun `brand matching is case-insensitive — uppercase IQOO matches`() {
        assertTrue(DeviceIdentifier.isIqoo15(BuildInfoFakes.iqoo15(brand = "IQOO")))
    }

    @Test
    fun `model matching is case-insensitive — lowercase model matches`() {
        assertTrue(DeviceIdentifier.isIqoo15(BuildInfoFakes.iqoo15(model = "iqoo 15")))
    }

    @Test
    fun `model matching ignores leading and trailing whitespace`() {
        assertTrue(DeviceIdentifier.isIqoo15(BuildInfoFakes.iqoo15(model = "  iQOO 15  ")))
    }

    @Test
    fun `physical iQOO 15 telemetry matches IQOO_15`() {
        val physicalDeviceBuild = BuildInfo(
            manufacturer = "vivo",
            brand = "vivo",
            model = "I2501",
            device = "I2501",
            product = "I2501i",
            androidVersionRelease = "16",
            sdkInt = 36,
            buildFingerprint = "vivo/I2501i/I2501:16/PD2505CF_EX_A_16.0.22.3.W30/compiler260616230953:user/release-keys",
            buildDisplay = "PD2505CF_EX_A_16.0.22.3.W30",
            buildIncremental = "compiler260616230953",
        )
        assertEquals(DeviceIdentifier.KnownDevice.IQOO_15, DeviceIdentifier.identify(physicalDeviceBuild))
        assertTrue(DeviceIdentifier.isIqoo15(physicalDeviceBuild))
    }

    @Test
    fun `physical iQOO 15 with brand iQOO and model I2501 matches IQOO_15`() {
        val build = BuildInfoFakes.iqoo15(
            manufacturer = "vivo",
            brand = "iQOO",
            model = "I2501",
            device = "I2501",
            product = "I2501i",
        )
        assertEquals(DeviceIdentifier.KnownDevice.IQOO_15, DeviceIdentifier.identify(build))
        assertTrue(DeviceIdentifier.isIqoo15(build))
    }

    // ── iQOO 15 negative matches ──────────────────────────────────────────

    @Test
    fun `iQOO 12 is not identified as IQOO_15`() {
        assertFalse(DeviceIdentifier.isIqoo15(BuildInfoFakes.iqoo15(model = "iQOO 12")))
    }

    @Test
    fun `iQOO 13 is not identified as IQOO_15`() {
        assertFalse(DeviceIdentifier.isIqoo15(BuildInfoFakes.iqoo15(model = "iQOO 13")))
    }

    @Test
    fun `iQOO 15 Pro would not match (not in catalogue — requires separate entry)`() {
        // "iqoo 15 pro" is not in KNOWN_MODEL_NAMES; a separate entry is required
        assertFalse(DeviceIdentifier.isIqoo15(BuildInfoFakes.iqoo15(model = "iQOO 15 Pro")))
    }

    @Test
    fun `Samsung device is identified as UNKNOWN`() {
        val result = DeviceIdentifier.identify(BuildInfoFakes.generic())
        assertEquals(DeviceIdentifier.KnownDevice.UNKNOWN, result)
    }

    @Test
    fun `Samsung device isIqoo15 returns false`() {
        assertFalse(DeviceIdentifier.isIqoo15(BuildInfoFakes.generic()))
    }

    @Test
    fun `wrong brand with correct model is not an iQOO 15`() {
        assertFalse(DeviceIdentifier.isIqoo15(
            BuildInfoFakes.iqoo15(brand = "samsung", model = "iQOO 15")
        ))
    }

    @Test
    fun `correct brand with wrong model is not an iQOO 15`() {
        assertFalse(DeviceIdentifier.isIqoo15(
            BuildInfoFakes.iqoo15(brand = "iQOO", model = "Pixel 9")
        ))
    }
}
