package dev.schrammel.personal_running_tracker.domain.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitCalculatorTest {

    private fun point(lat: Double, ts: Long, alt: Double = 0.0) =
        GpsPoint(lat, 0.0, alt, ts)

    /** One degree of latitude ~= 111_195 m; used to build synthetic routes. */
    private val metersPerDegreeLat = 111_195.0

    @Test
    fun `fewer than two points yields no splits`() {
        val one = listOf(point(0.0, 0))
        assertTrue(SplitCalculator.computeSplits(one).isEmpty())
        assertTrue(SplitCalculator.computeSplits(emptyList()).isEmpty())
    }

    @Test
    fun `run of exactly 3km at 5 min per km produces three 300s splits`() {
        // 3 km covered in 900 s, fixes every 100 m (30 s each)
        val points = (0..30).map { i ->
            point(i * 100.0 / metersPerDegreeLat, i * 30_000L)
        }
        val splits = SplitCalculator.computeSplits(points)
        assertEquals(3, splits.size)
        splits.forEachIndexed { idx, s ->
            assertEquals(idx + 1, s.splitNumber)
            assertEquals(1000.0, s.distanceMeters, 0.01)
            assertEquals(300_000.0, s.durationMs.toDouble(), 3_000.0)
        }
        // each split: 1000 m / 300 s = 3.33 m/s
        assertEquals(3.33, splits[0].averageSpeedMetersPerSecond, 0.05)
    }

    @Test
    fun `splits interpolate time when boundary falls between fixes`() {
        // Fix every 300 m, 1000 m boundary falls 1/3 into the 4th fix interval
        // Speed 3 m/s => 100 s per fix interval; km takes 333.3 s
        val points = (0..10).map { i ->
            point(i * 300.0 / metersPerDegreeLat, i * 100_000L)
        }
        val splits = SplitCalculator.computeSplits(points)
        assertTrue(splits.isNotEmpty())
        val first = splits[0]
        assertEquals(1000.0, first.distanceMeters, 0.01)
        assertEquals(333_333.0, first.durationMs.toDouble(), 3_000.0)
    }

    @Test
    fun `elevation change is end minus start altitude of each split`() {
        // 2 km, rising 1 m per 100 m => each split gains ~10 m
        val points = (0..20).map { i ->
            point(i * 100.0 / metersPerDegreeLat, i * 30_000L, alt = i * 1.0)
        }
        val splits = SplitCalculator.computeSplits(points)
        assertEquals(2, splits.size)
        assertTrue(splits[0].elevationChangeMeters > 5.0)
        assertTrue(splits[0].elevationChangeMeters <= 10.5)
    }

    @Test
    fun `unsorted input is ordered by timestamp before computing`() {
        val forward = (0..10).map { i -> point(i * 100.0 / metersPerDegreeLat, i * 30_000L) }
        val shuffled = forward.reversed()
        val a = SplitCalculator.computeSplits(forward)
        val b = SplitCalculator.computeSplits(shuffled)
        assertEquals(a.size, b.size)
        assertEquals(a.first().durationMs, b.first().durationMs)
    }

    @Test
    fun `duplicate timestamps are skipped without dividing by zero`() {
        val points = listOf(
            point(0.0, 0),
            point(0.0, 5_000), // no movement
            point(1000.0 / metersPerDegreeLat, 5_000), // same timestamp
            point(2000.0 / metersPerDegreeLat, 35_000),
        )
        val splits = SplitCalculator.computeSplits(points)
        assertTrue(splits.isNotEmpty())
        assertTrue(splits.all { it.durationMs >= 0 })
    }
}
