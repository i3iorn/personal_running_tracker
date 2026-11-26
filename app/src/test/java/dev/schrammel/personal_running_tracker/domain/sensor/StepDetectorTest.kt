package dev.schrammel.personal_running_tracker.domain.sensor

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class StepDetectorTest {

    @Test
    fun `no steps from steady gravity signal`() {
        val detector = StepDetector()
        // Constant gravity ~9.81 m/s^2 for 2 seconds at 50 Hz.
        var steps = 0
        for (i in 0 until 100) {
            if (detector.onAccelerometerMagnitude(9.81, i * 20L)) steps++
        }
        assertEquals(0, steps)
    }

    @Test
    fun `detects steps from oscillating signal`() {
        val detector = StepDetector()
        var steps = 0
        // Simulate ~2 steps/second: alternate high/low at 50 Hz for 4 seconds.
        for (i in 0 until 200) {
            val magnitude = if ((i / 12) % 2 == 0) 12.5 else 8.0
            if (detector.onAccelerometerMagnitude(magnitude, i * 20L)) steps++
        }
        assertTrue("expected several steps, got $steps", steps >= 4)
    }

    @Test
    fun `rejects steps closer than minimum interval`() {
        val detector = StepDetector()
        // Two peaks within 50 ms should count as one step.
        assertEquals(true, detector.onAccelerometerMagnitude(13.0, 0L))
        assertEquals(false, detector.onAccelerometerMagnitude(13.0, 50L))
    }

    @Test
    fun `respects refractory period then detects next step`() {
        val detector = StepDetector()
        assertTrue(detector.onAccelerometerMagnitude(13.0, 0L))
        assertTrue(detector.onAccelerometerMagnitude(13.0, 350L))
    }

    @Test
    fun `walking pace detected`() {
        val detector = StepDetector()
        var steps = 0
        // ~1 step/second over 6 seconds.
        var t = 0L
        for (i in 0 until 300) {
            val magnitude = if ((i / 25) % 2 == 0) 11.5 else 9.0
            if (detector.onAccelerometerMagnitude(magnitude, t)) steps++
            t += 20L
        }
        assertTrue("expected at least 3 walking steps, got $steps", steps >= 3)
    }
}