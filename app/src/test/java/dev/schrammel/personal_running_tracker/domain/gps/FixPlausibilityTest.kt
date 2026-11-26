package dev.schrammel.personal_running_tracker.domain.gps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixPlausibilityTest {

    @Test
    fun `walking pace is plausible`() {
        // 5 m over 1 s = 5 m/s, accuracy 8 m (< trust threshold) → kept
        assertFalse(FixPlausibility.isImplausible(5.0, 1_000, 8f))
        // 5 m over 1 s, accuracy 20 m: 5 m/s < 15 m/s cap → plausible
        assertFalse(FixPlausibility.isImplausible(5.0, 1_000, 20f))
    }

    @Test
    fun `wild inaccurate jump is rejected`() {
        // 1 km in 1 s at accuracy 30 m → 1000 m/s, clearly a glitch
        assertTrue(FixPlausibility.isImplausible(1000.0, 1_000, 30f))
    }

    @Test
    fun `high speed with accurate fix is kept`() {
        // riding home: 20 m/s but accuracy 5 m (trusted) → plausible
        assertFalse(FixPlausibility.isImplausible(20.0, 1_000, 5f))
    }

    @Test
    fun `zero duration is never implausible`() {
        assertFalse(FixPlausibility.isImplausible(1000.0, 0, 50f))
    }
}
