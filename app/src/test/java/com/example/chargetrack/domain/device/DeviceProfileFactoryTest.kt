package com.example.chargetrack.domain.device

import com.example.chargetrack.domain.device.DeviceIdentifier.KnownDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileFactoryTest {

    // ── iQOO 15 proposal ─────────────────────────────────────────────────

    @Test
    fun `iQOO 15 build produces IQOO_15 matched device`() {
        val proposal = DeviceProfileFactory.buildProposal(BuildInfoFakes.iqoo15())
        assertEquals(KnownDevice.IQOO_15, proposal.matchedDevice)
    }

    @Test
    fun `iQOO 15 proposal isIqoo15 is true`() {
        assertTrue(DeviceProfileFactory.buildProposal(BuildInfoFakes.iqoo15()).isIqoo15)
    }

    @Test
    fun `iQOO 15 proposal hasReferenceData is true`() {
        assertTrue(DeviceProfileFactory.buildProposal(BuildInfoFakes.iqoo15()).hasReferenceData)
    }

    @Test
    fun `iQOO 15 profile has correct typical capacity`() {
        val profile = DeviceProfileFactory.buildProposal(BuildInfoFakes.iqoo15()).proposedProfile
        assertEquals(Iqoo15ReferenceData.TYPICAL_CAPACITY_MAH, profile.typicalCapacityMah)
    }

    @Test
    fun `iQOO 15 profile has correct rated capacity`() {
        val profile = DeviceProfileFactory.buildProposal(BuildInfoFakes.iqoo15()).proposedProfile
        assertEquals(Iqoo15ReferenceData.RATED_CAPACITY_MAH, profile.ratedCapacityMah)
    }

    @Test
    fun `iQOO 15 profile has correct typical energy`() {
        val profile = DeviceProfileFactory.buildProposal(BuildInfoFakes.iqoo15()).proposedProfile
        assertEquals(Iqoo15ReferenceData.TYPICAL_ENERGY_WH, profile.typicalEnergyWh)
    }

    @Test
    fun `iQOO 15 profile has correct wired reference wattage`() {
        val profile = DeviceProfileFactory.buildProposal(BuildInfoFakes.iqoo15()).proposedProfile
        assertEquals(Iqoo15ReferenceData.WIRED_REFERENCE_W, profile.wiredReferenceW)
    }

    @Test
    fun `iQOO 15 profile has correct wireless reference wattage`() {
        val profile = DeviceProfileFactory.buildProposal(BuildInfoFakes.iqoo15()).proposedProfile
        assertEquals(Iqoo15ReferenceData.WIRELESS_REFERENCE_W, profile.wirelessReferenceW)
    }

    @Test
    fun `iQOO 15 profile Build fields are copied from BuildInfo`() {
        val info = BuildInfoFakes.iqoo15()
        val profile = DeviceProfileFactory.buildProposal(info).proposedProfile
        assertEquals(info.manufacturer, profile.manufacturer)
        assertEquals(info.brand, profile.brand)
        assertEquals(info.model, profile.model)
        assertEquals(info.device, profile.device)
        assertEquals(info.product, profile.product)
        assertEquals(info.androidVersionRelease, profile.androidVersion)
        assertEquals(info.sdkInt, profile.sdkInt)
        assertEquals(info.buildFingerprint, profile.buildFingerprint)
    }

    @Test
    fun `iQOO 15 profile originOsBuildLabel is populated from buildDisplay`() {
        val info = BuildInfoFakes.iqoo15(buildDisplay = "OriginOS 5.0.1_20260101")
        val profile = DeviceProfileFactory.buildProposal(info).proposedProfile
        assertNotNull(profile.originOsBuildLabel)
    }

    // ── Generic device proposal ───────────────────────────────────────────

    @Test
    fun `generic device produces UNKNOWN matched device`() {
        val proposal = DeviceProfileFactory.buildProposal(BuildInfoFakes.generic())
        assertEquals(KnownDevice.UNKNOWN, proposal.matchedDevice)
    }

    @Test
    fun `generic device isIqoo15 is false`() {
        assertFalse(DeviceProfileFactory.buildProposal(BuildInfoFakes.generic()).isIqoo15)
    }

    @Test
    fun `generic device hasReferenceData is false`() {
        assertFalse(DeviceProfileFactory.buildProposal(BuildInfoFakes.generic()).hasReferenceData)
    }

    @Test
    fun `generic device profile reference fields are all null`() {
        val profile = DeviceProfileFactory.buildProposal(BuildInfoFakes.generic()).proposedProfile
        assertNull("typicalCapacityMah", profile.typicalCapacityMah)
        assertNull("ratedCapacityMah", profile.ratedCapacityMah)
        assertNull("typicalEnergyWh", profile.typicalEnergyWh)
        assertNull("ratedEnergyWh", profile.ratedEnergyWh)
        assertNull("wiredReferenceW", profile.wiredReferenceW)
        assertNull("wirelessReferenceW", profile.wirelessReferenceW)
    }

    @Test
    fun `generic device profile Build fields are still populated`() {
        val info = BuildInfoFakes.generic()
        val profile = DeviceProfileFactory.buildProposal(info).proposedProfile
        assertEquals(info.manufacturer, profile.manufacturer)
        assertEquals(info.brand, profile.brand)
        assertEquals(info.model, profile.model)
    }

    @Test
    fun `generic device with no OriginOS pattern has null originOsBuildLabel`() {
        val info = BuildInfoFakes.generic(buildDisplay = "S925BXXS1AXE3", buildIncremental = "S925BXXS1AXE3")
        val profile = DeviceProfileFactory.buildProposal(info).proposedProfile
        assertNull(profile.originOsBuildLabel)
    }

    // ── Proposal does not pre-fill user-entered fields ────────────────────

    @Test
    fun `iQOO 15 proposal leaves user-entered fields null (require user input)`() {
        val profile = DeviceProfileFactory.buildProposal(BuildInfoFakes.iqoo15()).proposedProfile
        assertNull("nickname should be null — requires user input", profile.nickname)
        assertNull("purchaseDate should be null — requires user input", profile.purchaseDate)
        assertNull("firstUseDate should be null — requires user input", profile.firstUseDate)
        assertNull("ramStorageVariant should be null — requires user input", profile.ramStorageVariant)
    }
}
