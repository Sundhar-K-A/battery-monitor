package com.example.chargetrack.domain.device

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginOsBuildLabelExtractorTest {

    // ── Positive matches ──────────────────────────────────────────────────

    @Test
    fun `OriginOS in buildDisplay is extracted`() {
        val info = BuildInfoFakes.iqoo15(buildDisplay = "OriginOS 5.0.1_20260101")
        assertNotNull(OriginOsBuildLabelExtractor.extract(info))
    }

    @Test
    fun `extracted label contains the OriginOS version fragment`() {
        val info = BuildInfoFakes.iqoo15(buildDisplay = "OriginOS 5.0.1_20260101")
        val label = OriginOsBuildLabelExtractor.extract(info)
        assertTrue("Label should start with OriginOS", label!!.startsWith("OriginOS", ignoreCase = true))
    }

    @Test
    fun `OriginOS in buildIncremental is extracted when buildDisplay has no match`() {
        val info = BuildInfoFakes.iqoo15(
            buildDisplay = "B.001.release",
            buildIncremental = "OriginOS5.1"
        )
        assertNotNull(OriginOsBuildLabelExtractor.extract(info))
    }

    @Test
    fun `matching is case-insensitive for OriginOS prefix`() {
        val info = BuildInfoFakes.iqoo15(buildDisplay = "originos 5.0")
        assertNotNull(OriginOsBuildLabelExtractor.extract(info))
    }

    @Test
    fun `OS version pattern in buildIncremental is extracted`() {
        val info = BuildInfoFakes.iqoo15(
            buildDisplay = "B.001.release",
            buildIncremental = "OS5.0.1.2.W20260101"
        )
        assertNotNull(OriginOsBuildLabelExtractor.extract(info))
    }

    // ── Negative matches ──────────────────────────────────────────────────

    @Test
    fun `Samsung build with no OriginOS pattern returns null`() {
        val info = BuildInfoFakes.generic(
            buildDisplay = "S925BXXS1AXE3",
            buildIncremental = "S925BXXS1AXE3"
        )
        assertNull(OriginOsBuildLabelExtractor.extract(info))
    }

    @Test
    fun `generic AOSP build returns null`() {
        val info = BuildInfoFakes.generic(
            buildDisplay = "AP1A.240905.004",
            buildIncremental = "12345678"
        )
        assertNull(OriginOsBuildLabelExtractor.extract(info))
    }

    @Test
    fun `build display containing only digits returns null`() {
        val info = BuildInfoFakes.iqoo15(
            buildDisplay = "20260101",
            buildIncremental = "20260101"
        )
        assertNull(OriginOsBuildLabelExtractor.extract(info))
    }
}
