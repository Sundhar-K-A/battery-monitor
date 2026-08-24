package com.example.chargetrack.data.system

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultSoftwareSnapshotProviderTest {

    @Test
    fun `captureCurrentSnapshot populates all software version attributes`() {
        val provider = DefaultSoftwareSnapshotProvider()
        val snapshot = provider.captureCurrentSnapshot()

        assertNotNull(snapshot.id)
        assertNotNull(snapshot.capturedAt)
        assertNotNull(snapshot.androidVersion)
        assertTrue(snapshot.sdkInt > 0)
        assertNotNull(snapshot.appVersionName)
    }
}
