package dev.schrammel.personal_running_tracker.domain.units

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {

    @Test
    fun `metric distance converts meters to kilometres`() {
        assertEquals(1.0, Units.distance(1000.0, "KILOMETERS"), 1e-9)
        assertEquals(5.0, Units.distance(5000.0, "KILOMETERS"), 1e-9)
        assertEquals("km", Units.distanceLabel("KILOMETERS"))
    }

    @Test
    fun `imperial distance converts meters to miles`() {
        assertEquals(1.0, Units.distance(1609.344, "MILES"), 1e-6)
        assertEquals("mi", Units.distanceLabel("MILES"))
    }

    @Test
    fun `speed converts m per s to kmh and mph`() {
        assertEquals(36.0, Units.speed(10.0, "KILOMETERS"), 1e-6)
        assertEquals(22.3694, Units.speed(10.0, "MILES"), 1e-3)
    }

    @Test
    fun `pace converts seconds per meter to per-km and per-mile`() {
        // 6 min/km pace = 0.36 s/m
        assertEquals(360.0, Units.paceSecondsPerUnit(0.36, "MIN_PER_KM"), 1e-6)
        // same runner per mile: 0.36 * 1609.344 = 579.4 s/mi (~9:39/mi)
        assertEquals(579.4, Units.paceSecondsPerUnit(0.36, "MIN_PER_MILE"), 0.1)
    }

    @Test
    fun `paceText formats mss with clamping`() {
        assertEquals("5:00", Units.paceText(0.3, "MIN_PER_KM"))
        assertEquals("0:00", Units.paceText(-0.5, "MIN_PER_KM")) // negative clamps to 0:00
    }
}
