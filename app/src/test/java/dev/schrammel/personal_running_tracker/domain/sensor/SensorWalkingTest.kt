package dev.schrammel.personal_running_tracker.domain.sensor

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for the walking-detection fix: sensor distance must grow live between
 * GPS anchors so indoor walks register distance/pace, and GPS glitches must not
 * poison that estimate.
 */
class SensorWalkingTest {

    private fun anchor(lat: Double, lng: Double, tMs: Long) = GpsAnchor(
        latitude = lat, longitude = lng,
        altitudeMeters = 0.0, timestampMs = tMs,
        accuracyMeters = 5.0f, speedMps = 1.5f,
        bearingDegrees = 0.0f,
    )

    @Test
    fun `sensor distance grows live between GPS anchors`() {
        val fusion = SensorFusion(maxGpsSegmentRatio = 10.0, uncertaintyPerStepMeters = 5.0)
        val stride = 0.75 // meters per step

        // Baseline anchor
        fusion.onGpsAnchor(anchor(37.7749, -122.4194, 0L))
        assertEquals(0.0, fusion.totalDistanceMeters, 0.001)

        // Walk 2000 steps before the next anchor arrives — distance must be visible immediately (this is the indoor-walking bug fix).
        repeat(2000) { fusion.onSensorStep(stride) }
        assertEquals(1500.0, fusion.pendingSensorDistanceMeters, 0.001)
        assertEquals(1500.0, fusion.totalDistanceMeters, 0.001)
    }

    @Test
    fun `GPS anchor replaces pending sensor estimate without double counting`() {
        val fusion = SensorFusion(maxGpsSegmentRatio = 10.0, uncertaintyPerStepMeters = 5.0)

        fusion.onGpsAnchor(anchor(37.7749, -122.4194, 0L))
        repeat(1000) { fusion.onSensorStep(0.75) } // 750m pending estimate

        // Jump > MAX_JUMP_METERS (5000m) — treated as a discontinuity, sensor kept.
        val update = fusion.onGpsAnchor(anchor(38.2, -122.4194, 60000L))
        assertFalse(update.usedGps)
        assertEquals(750.0, update.totalDistanceMeters, 0.001)
    }

    @Test
    fun `walking pace estimated from step cadence and stride`() {
        // 2 steps/second, 0.75m stride = 1.5 m/s ≈ 11:06 min/km pace
        val speedMps = 2.0 * 0.75
        assertEquals(1.5, speedMps, 0.001)
        assertEquals(666.67, 1000.0 / speedMps, 1.0)
    }
}
