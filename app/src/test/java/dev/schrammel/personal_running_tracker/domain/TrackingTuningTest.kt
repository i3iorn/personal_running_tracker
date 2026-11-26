package dev.schrammel.personal_running_tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingTuningTest {

    @Test
    fun `balanced profile uses conservative defaults`() {
        val t = TrackingTuning.forProfile("BALANCED")
        assertEquals(30_000L, t.gpsMinIntervalMs)
        assertEquals(30f, t.gpsMinDistanceMeters, 0.01f)
        assertEquals(0.75, t.strideLengthMeters, 1e-9)
    }

    @Test
    fun `saver profile is slower and coarser than balanced`() {
        val saver = TrackingTuning.forProfile("SAVER")
        val balanced = TrackingTuning.forProfile("BALANCED")
        assertTrue(saver.gpsMinIntervalMs > balanced.gpsMinIntervalMs)
        assertTrue(saver.gpsMinDistanceMeters > balanced.gpsMinDistanceMeters)
        assertTrue(saver.sensorFusionMaxGpsSegmentRatio > balanced.sensorFusionMaxGpsSegmentRatio)
    }

    @Test
    fun `high accuracy profile is faster and tighter than balanced`() {
        val ha = TrackingTuning.forProfile("HIGH_ACCURACY")
        val balanced = TrackingTuning.forProfile("BALANCED")
        assertTrue(ha.gpsMinIntervalMs < balanced.gpsMinIntervalMs)
        assertTrue(ha.gpsMinDistanceMeters < balanced.gpsMinDistanceMeters)
    }

    @Test
    fun `unknown profile falls back to balanced`() {
        val t = TrackingTuning.forProfile("NONSENSE")
        assertEquals(30_000L, t.gpsMinIntervalMs)
    }
}
