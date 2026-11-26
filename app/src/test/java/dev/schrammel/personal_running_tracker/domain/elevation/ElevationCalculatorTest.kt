package dev.schrammel.personal_running_tracker.domain.elevation

import org.junit.Assert.assertEquals
import org.junit.Test

class ElevationCalculatorTest {

    @Test
    fun `steady climb accumulates gain`() {
        // +1 m per point, well above the 2 m threshold once smoothing catches up
        val altitudes = (0..20).map { it * 2.0 }
        val (gain, loss) = ElevationCalculator.totals(altitudes)
        assertEquals(40.0, gain, 4.0)
        assertEquals(0.0, loss, 0.01)
    }

    @Test
    fun `noise around flat ground accumulates nothing`() {
        // ±1 m flutter below the threshold
        val altitudes = (0..40).map { if (it % 2 == 0) 500.0 else 501.0 }
        val (gain, loss) = ElevationCalculator.totals(altitudes)
        assertEquals(0.0, gain, 2.0)
        assertEquals(0.0, loss, 2.0)
    }

    @Test
    fun `climb then descent yields both gain and loss`() {
        val up = (0..10).map { it * 3.0 }          // 0..30
        val down = (1..10).map { 30.0 - it * 3.0 } // back to 0
        val (gain, loss) = ElevationCalculator.totals(up + down)
        assertEquals(30.0, gain, 10.0)
        assertEquals(30.0, loss, 10.0)
    }

    @Test
    fun `fewer than two points yields zero`() {
        assertEquals(0.0 to 0.0, ElevationCalculator.totals(emptyList()))
        assertEquals(0.0 to 0.0, ElevationCalculator.totals(listOf(500.0)))
    }

    @Test
    fun `smoothing suppresses single-fx spikes`() {
        val base = List(10) { 500.0 }
        val spiked = base.toMutableList().also { it[5] = 520.0 }
        val (gain, _) = ElevationCalculator.totals(spiked)
        // A one-fix 20 m spike is smoothed away; raw accumulation would add 20
        assertEquals(0.0, gain, 8.0)
    }
}
