package dev.schrammel.personal_running_tracker.domain.sensor

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class StrideEstimatorTest {

    private val defaultStride = 0.75

    @Test
    fun `returns default stride when no calibration data exists`() {
        val estimator = StrideEstimator(defaultStrideMeters = defaultStride)
        assertEquals(defaultStride, estimator.strideLengthMeters(), 0.0001)
    }

    @Test
    fun `calibrates stride from GPS distance and step count`() {
        val estimator = StrideEstimator(defaultStrideMeters = defaultStride)
        // 4000 meters / 5000 steps = 0.8 m/step
        estimator.observeCalibration(distanceMeters = 4000.0, steps = 5000L)
        assertEquals(0.8, estimator.strideLengthMeters(), 0.0001)
    }

    @Test
    fun `ignores zero-step calibration to avoid division by zero`() {
        val estimator = StrideEstimator(defaultStrideMeters = defaultStride)
        estimator.observeCalibration(distanceMeters = 1000.0, steps = 0L)
        assertEquals(defaultStride, estimator.strideLengthMeters(), 0.0001)
    }

    @Test
    fun `ignores negative distance calibration`() {
        val estimator = StrideEstimator(defaultStrideMeters = defaultStride)
        estimator.observeCalibration(distanceMeters = -100.0, steps = 100L)
        assertEquals(defaultStride, estimator.strideLengthMeters(), 0.0001)
    }

    @Test
    fun `weights recent calibration higher than older`() {
        val estimator = StrideEstimator(defaultStrideMeters = defaultStride)
        estimator.observeCalibration(distanceMeters = 2000.0, steps = 4000L) // 0.5
        estimator.observeCalibration(distanceMeters = 4000.0, steps = 4000L) // 1.0
        // weighted mean = (0.5*1 + 1.0*2)/3 = 0.833
        assertEquals(0.8333, estimator.strideLengthMeters(), 0.001)
    }

    @Test
    fun `stride length clamped to sane range`() {
        val estimator = StrideEstimator(defaultStrideMeters = defaultStride)
        estimator.observeCalibration(distanceMeters = 100.0, steps = 1L) // 100 m/step -> clamp
        assertTrue("upper clamp failed", estimator.strideLengthMeters() <= 2.5)
        estimator.observeCalibration(distanceMeters = 0.01, steps = 1000L) // tiny -> clamp
        assertTrue("lower clamp failed", estimator.strideLengthMeters() >= 0.4)
    }
}