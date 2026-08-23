package com.example.chargetrack.domain.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BootInfoProviderTest {

    @Test
    fun `getBootId returns non-blank boot identifier`() {
        val provider = DefaultBootInfoProvider()
        val bootId = provider.getBootId()

        assertNotNull(bootId)
        assertTrue(bootId!!.isNotBlank())
    }

    @Test
    fun `getBootId returns stable boot identifier across multiple calls`() {
        val provider = DefaultBootInfoProvider()
        val firstBootId = provider.getBootId()
        val secondBootId = provider.getBootId()
        val thirdBootId = provider.getBootId()

        assertEquals("Boot ID must remain stable across calls within same process", firstBootId, secondBootId)
        assertEquals("Boot ID must remain stable across calls within same process", secondBootId, thirdBootId)
    }
}
