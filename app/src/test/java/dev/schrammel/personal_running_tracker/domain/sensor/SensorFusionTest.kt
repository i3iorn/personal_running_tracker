package dev.schrammel.personal_running_tracker.domain.sensor

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class SensorFusionTest {

    private fun anchor(t: Long, lat: Double, lng: Double, accuracy: Float = 10f): GpsAnchor = GpsAnchor(
        latitude = lat, longitude = lng, altitudeMeters = 50.0, timestampMs = t,
        accuracyMeters = accuracy, speedMps = 3.0f, bearingDegrees = 90.0f,
    )

    @Test
    fun `first gps anchor establishes baseline without distance`() {
        val fusion = SensorFusion()
        val update = fusion.onGpsAnchor(anchor(t = 0L, lat = 52.0, lng = 13.0))
        assertEquals(0.0, update.totalDistanceMeters, 0.0001)
        assertEquals(false, update.usedGps)
        // After first anchor: no distance confirmed yet, confidence max, uncertainty = anchor accuracy
        assertEquals(0.0, fusion.totalDistanceMeters, 0.0001)
        assertEquals(1.0, fusion.confidence, 0.0001)
        assertEquals(10.0, fusion.uncertaintyMeters, 0.0001)
    }

    @Test
    fun `sensor steps accumulate distance before anchor`() {
        val fusion = SensorFusion()
        fusion.onGpsAnchor(anchor(t = 0L, lat = 52.0, lng = 13.0))
        fusion.onSensorStep(strideLengthMeters = 0.5)
        fusion.onSensorStep(strideLengthMeters = 0.5)
        // 2 steps * 0.5m = 1.0m pending sensor distance
        assertEquals(1.0, fusion.totalDistanceMeters, 0.0001)
        assertEquals(1.0, fusion.pendingSensorDistanceMeters, 0.0001)
        // uncertainty = anchor accuracy (10) + 2 steps * 5 = 20
        assertEquals(20.0, fusion.uncertaintyMeters, 1e-6)
    }

    @Test
    fun `good gps anchor replaces sensor estimate without double counting`() {
        val fusion = SensorFusion()
        fusion.onGpsAnchor(anchor(t = 0L, lat = 52.0, lng = 13.0))
        fusion.onSensorStep(strideLengthMeters = 0.5)
        fusion.onSensorStep(strideLengthMeters = 0.5) // pending = 1.0
        // GPS anchor ~1.1m away; GPS segment replaces sensor segment (no double count)
        val update = fusion.onGpsAnchor(anchor(t = 1000L, lat = 52.00001, lng = 13.0))
        // GPS haversine distance ~1.113m; total should be GPS segment, NOT sensor+GPS
        val expectedGpsSegment = GeoMath.haversineMeters(52.0, 13.0, 52.00001, 13.0)
        assertTrue("no double count allowed, got ${update.totalDistanceMeters}",
            kotlin.math.abs(update.totalDistanceMeters - expectedGpsSegment) < 0.01)
        assertEquals(true, update.usedGps)
        // pending reset after anchor
        assertEquals(0.0, fusion.pendingSensorDistanceMeters, 0.0001)
        // confirmed distance now equals GPS segment
        assertEquals(expectedGpsSegment, fusion.totalDistanceMeters, 0.0001)
    }

    @Test
    fun `gps glitch above sensor guidance is ignored`() {
        val fusion = SensorFusion()
        fusion.onGpsAnchor(anchor(t = 0L, lat = 52.0, lng = 13.0))
        fusion.onSensorStep(strideLengthMeters = 0.5) // sensor says ~0.5m
        val update = fusion.onGpsAnchor(anchor(t = 1000L, lat = 52.01, lng = 13.0)) // ~1.1km -> glitch
        assertEquals(false, update.usedGps)
        // Sensor estimate retained -> no massive false jump
        assertTrue(kotlin.math.abs(fusion.totalDistanceMeters - 0.5) < 0.01)
    }

    @Test
    fun `uncertainty decreases on good gps and grows with steps`() {
        val fusion = SensorFusion()
        fusion.onGpsAnchor(anchor(t = 0L, lat = 52.0, lng = 13.0, accuracy = 10f))
        // After first anchor with good accuracy: confidence max, uncertainty = anchor accuracy
        assertEquals(1.0, fusion.confidence, 0.0001)
        assertEquals(10.0, fusion.uncertaintyMeters, 1e-6)
        fusion.onSensorStep(strideLengthMeters = 0.5)
        // uncertainty grows with steps
        assertEquals(15.0, fusion.uncertaintyMeters, 1e-6)
        val update = fusion.onGpsAnchor(anchor(t = 1000L, lat = 52.00001, lng = 13.0, accuracy = 10f))
        // Good GPS resets uncertainty to its accuracy
        assertTrue(kotlin.math.abs(update.uncertaintyMeters - 10.0) < 0.001)
        assertEquals(1.0, update.confidence, 0.0001)
    }
}
