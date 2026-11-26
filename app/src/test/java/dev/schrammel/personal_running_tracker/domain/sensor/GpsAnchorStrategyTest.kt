package dev.schrammel.personal_running_tracker.domain.sensor

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class GpsAnchorStrategyTest {

    private val config = GpsAcquisitionConfig.forProfile(BatteryProfile.BALANCED)

    @Test
    fun `initial acquisition requests a fix immediately`() {
        val strategy = GpsAnchorStrategy(config)
        val decision = strategy.onTick(
            nowMs = 0L,
            state = TrackingState(),
            movementDetected = false,
            gpsAccuracyMeters = 100f,
        )
        assertEquals(TrackingMode.INITIAL_ACQUISITION, decision.mode)
        assertTrue("initial fix must be requested", decision.requestGps)
    }

    @Test
    fun `stable movement requests fix only after min interval`() {
        val strategy = GpsAnchorStrategy(config)
        strategy.onTick(0L, TrackingState(), false, 100f) // initial
        val tooSoon = strategy.onTick(
            nowMs = config.minIntervalMs - 1,
            state = TrackingState(uncertaintyMeters = 5.0),
            movementDetected = true,
            gpsAccuracyMeters = 10f,
        )
        assertEquals(false, tooSoon.requestGps)
        val ready = strategy.onTick(
            nowMs = config.minIntervalMs,
            state = TrackingState(uncertaintyMeters = 5.0),
            movementDetected = true,
            gpsAccuracyMeters = 10f,
        )
        assertEquals(true, ready.requestGps)
    }

    @Test
    fun `high uncertainty forces early fix even before min interval`() {
        val strategy = GpsAnchorStrategy(config)
        strategy.onTick(0L, TrackingState(), false, 100f)
        val decision = strategy.onTick(
            nowMs = 1000L,
            state = TrackingState(uncertaintyMeters = 200.0),
            movementDetected = true,
            gpsAccuracyMeters = 10f,
        )
        assertEquals(TrackingMode.UNSTABLE_MOVEMENT, decision.mode)
        assertTrue("high uncertainty forces fix", decision.requestGps)
    }

    @Test
    fun `poor gps reception requests frequent fixes`() {
        val strategy = GpsAnchorStrategy(config)
        strategy.onTick(0L, TrackingState(), false, 100f)
        val decision = strategy.onTick(
            nowMs = config.minIntervalMs,
            state = TrackingState(uncertaintyMeters = 10.0),
            movementDetected = true,
            gpsAccuracyMeters = 80f, // poor
        )
        assertEquals(TrackingMode.POOR_GPS_RECEPTION, decision.mode)
        assertTrue(decision.requestGps)
    }

    @Test
    fun `stationary suppresses gps after initial acquisition`() {
        val strategy = GpsAnchorStrategy(config)
        strategy.onTick(0L, TrackingState(), false, 20f) // good initial
        val decision = strategy.onTick(
            nowMs = config.maxIntervalMs * 2,
            state = TrackingState(uncertaintyMeters = 1.0),
            movementDetected = false,
            gpsAccuracyMeters = 10f,
        )
        assertEquals(TrackingMode.STATIONARY, decision.mode)
        assertEquals(false, decision.requestGps)
    }

    @Test
    fun `max interval forces fix during movement`() {
        val strategy = GpsAnchorStrategy(config)
        strategy.onTick(0L, TrackingState(), false, 10f)
        val decision = strategy.onTick(
            nowMs = config.maxIntervalMs + 1,
            state = TrackingState(uncertaintyMeters = 5.0),
            movementDetected = true,
            gpsAccuracyMeters = 10f,
        )
        assertTrue("max interval must force a fix", decision.requestGps)
    }

    @Test
    fun `distance since anchor triggers fix`() {
        val strategy = GpsAnchorStrategy(config)
        strategy.onTick(0L, TrackingState(), false, 10f)
        val decision = strategy.onTick(
            nowMs = config.minIntervalMs,
            state = TrackingState(
                uncertaintyMeters = 5.0,
                distanceSinceLastAnchor = config.minDistanceMeters + 5.0,
            ),
            movementDetected = true,
            gpsAccuracyMeters = 10f,
        )
        assertTrue("distance threshold must trigger fix", decision.requestGps)
    }

    @Test
    fun `saver profile uses longer intervals than balanced`() {
        val saver = GpsAnchorStrategy(GpsAcquisitionConfig.forProfile(BatteryProfile.SAVER))
        saver.onTick(0L, TrackingState(), false, 10f)
        val saverDecision = saver.onTick(
            nowMs = config.minIntervalMs, // 30s, still under saver's 60s
            state = TrackingState(uncertaintyMeters = 5.0),
            movementDetected = true,
            gpsAccuracyMeters = 10f,
        )
        assertEquals(false, saverDecision.requestGps)
    }
}