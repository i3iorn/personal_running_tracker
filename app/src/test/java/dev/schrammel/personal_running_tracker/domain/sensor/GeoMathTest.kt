package dev.schrammel.personal_running_tracker.domain.sensor

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class GeoMathTest {

    @Test
    fun `haversine of identical points is zero`() {
        assertEquals(0.0, GeoMath.haversineMeters(52.5, 13.4, 52.5, 13.4), 0.0001)
    }

    @Test
    fun `haversine of one degree latitude is about 111 km`() {
        val d = GeoMath.haversineMeters(52.0, 13.0, 53.0, 13.0)
        assertEquals(111_195.0, d, 500.0)
    }

    @Test
    fun `haversine is symmetric`() {
        val a = GeoMath.haversineMeters(52.0, 13.0, 52.1, 13.2)
        val b = GeoMath.haversineMeters(52.1, 13.2, 52.0, 13.0)
        assertEquals(a, b, 1e-6)
    }

    @Test
    fun `bearing due north is zero degrees`() {
        val bearing = GeoMath.bearingDegrees(52.0, 13.0, 52.1, 13.0)
        assertEquals(0.0, bearing, 0.5)
    }

    @Test
    fun `bearing due east is about ninety degrees`() {
        val bearing = GeoMath.bearingDegrees(52.0, 13.0, 52.0, 13.1)
        assertEquals(90.0, bearing, 1.0)
    }

    @Test
    fun `bearing is normalized to 0-360`() {
        val bearing = GeoMath.bearingDegrees(52.0, 13.0, 51.9, 13.0) // due south
        assertTrue(bearing >= 0.0 && bearing < 360.0)
    }

    @Test
    fun `offset point moves north by given meters`() {
        val (lat, lng) = GeoMath.offsetPointMeters(52.0, 13.0, metersNorth = 111.0, metersEast = 0.0)
        assertEquals(52.001, lat, 0.0005)
        assertEquals(13.0, lng, 0.0005)
    }
}