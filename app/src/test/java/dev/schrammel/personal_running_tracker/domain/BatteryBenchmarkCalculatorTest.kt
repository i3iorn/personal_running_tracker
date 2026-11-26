package dev.schrammel.personal_running_tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryBenchmarkCalculatorTest {

    @Test
    fun `consumed percent is start minus end`() {
        assertEquals(7, BatteryBenchmarkCalculator.consumedPercent(88, 81))
    }

    @Test
    fun `charging during run clamps to zero`() {
        assertEquals(0, BatteryBenchmarkCalculator.consumedPercent(80, 95))
    }

    @Test
    fun `missing levels return null`() {
        assertNull(BatteryBenchmarkCalculator.consumedPercent(null, 90))
        assertNull(BatteryBenchmarkCalculator.consumedPercent(90, null))
    }

    @Test
    fun `huge drop clamps to one hundred`() {
        assertEquals(100, BatteryBenchmarkCalculator.consumedPercent(100, 0))
    }
}
