package dev.schrammel.personal_running_tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccuracyBenchmarkCalculatorTest {

    @Test
    fun `perfect gps match yields zero error ratio`() {
        assertEquals(0.0f, AccuracyBenchmarkCalculator.ratio(fusedDistanceMeters = 1000.0, gpsDistanceMeters = 1000.0))
    }

    @Test
    fun `fused shorter than gps yields positive ratio`() {
        // |900-1000| / max(900,1000) = 100/1000 = 0.1 — underestimates count too
        assertEquals(0.1,
            AccuracyBenchmarkCalculator.ratio(fusedDistanceMeters = 900.0, gpsDistanceMeters = 1000.0)!!.toDouble(), 1e-4)
    }

    @Test
    fun `fused longer than gps yields ratio based on overshoot`() {
        // |1200-1000| / 1200
        assertEquals(200.0 / 1200.0,
            AccuracyBenchmarkCalculator.ratio(fusedDistanceMeters = 1200.0, gpsDistanceMeters = 1000.0)!!.toDouble(), 1e-4)
    }

    @Test
    fun `zero gps distance returns null`() {
        assertNull(AccuracyBenchmarkCalculator.ratio(fusedDistanceMeters = 100.0, gpsDistanceMeters = 0.0))
        assertNull(AccuracyBenchmarkCalculator.ratio(fusedDistanceMeters = 100.0, gpsDistanceMeters = -5.0))
    }

    @Test
    fun `ratio never exceeds one`() {
        assertEquals(1.0,
            AccuracyBenchmarkCalculator.ratio(fusedDistanceMeters = 1e9, gpsDistanceMeters = 1.0)!!.toDouble(), 1e-4)
    }
}
