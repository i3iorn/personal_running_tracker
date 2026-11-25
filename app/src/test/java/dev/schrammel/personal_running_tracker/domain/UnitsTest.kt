package dev.schrammel.personal_running_tracker.domain

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class UnitsTest {
    @Test
    fun `Kilometers conversion`() {
        val unit = DistanceUnit.KILOMETERS
        assertEquals("Kilometers", unit.label)
        val formatted = unit.format(10000.0)
        assertTrue(formatted.startsWith("10.0"))
    }

    @Test
    fun `Miles conversion`() {
        val unit = DistanceUnit.MILES
        assertEquals("Miles", unit.label)
        val formatted = unit.format(10000.0)
        assertTrue(formatted.startsWith("6.2"))
    }
}