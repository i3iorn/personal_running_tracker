package dev.schrammel.personal_running_tracker.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Validates the clamp/validation choke point in TrackingPreferences (security pass). */
@RunWith(RobolectricTestRunner::class)
class TrackingPreferencesTest {

    private lateinit var prefs: TrackingPreferences

    @Before
    fun setUp() {
        prefs = TrackingPreferences(ApplicationProvider.getApplicationContext<Application>())
    }

    @Test
    fun `defaults are the documented values`() {
        assertEquals("Balanced", prefs.trackingProfile)
        assertEquals(60_000L, prefs.saverGpsMinIntervalMs)
        assertEquals(10_000L, prefs.paceSmoothingWindowMs)
        assertEquals(1000f, prefs.splitLengthMeters.toFloat(), 0.01f)
    }

    @Test
    fun `gps intervals are clamped to a safe range`() {
        prefs.saverGpsMinIntervalMs = 1L          // below floor
        assertEquals(1_000L, prefs.saverGpsMinIntervalMs)
        prefs.saverGpsMinIntervalMs = 999_999L    // above ceiling
        assertEquals(600_000L, prefs.saverGpsMinIntervalMs)
        prefs.highAccuracyGpsMinIntervalMs = 0L
        assertEquals(1_000L, prefs.highAccuracyGpsMinIntervalMs)
    }

    @Test
    fun `distances are clamped to positive values`() {
        prefs.saverGpsMinDistanceMeters = 0f
        assertEquals(1f, prefs.saverGpsMinDistanceMeters, 0.01f)
        prefs.splitLengthMeters = 1.0             // absurdly short split
        assertEquals(100.0, prefs.splitLengthMeters, 0.01)
        prefs.paceMaxAccuracyMeters = 99_999f
        assertEquals(1_000f, prefs.paceMaxAccuracyMeters, 0.01f)
    }

    @Test
    fun `profile values normalize to canonical casing`() {
        prefs.trackingProfile = "SAVER"
        assertEquals("Saver", prefs.trackingProfile)
        prefs.trackingProfile = "high accuracy"
        assertEquals("High Accuracy", prefs.trackingProfile)
        prefs.trackingProfile = "garbage"
        assertEquals("Balanced", prefs.trackingProfile)
    }

    @Test
    fun `pr distance list rejects invalid entries and falls back`() {
        prefs.prDistanceKmList = "abc,-2,0,999,5"
        val parsed = prefs.prDistanceKmList.split(",").map { it.toDouble() }
        assertEquals(listOf(5.0), parsed)
        prefs.prDistanceKmList = "junk"
        assertEquals("1,3,5,10", prefs.prDistanceKmList)
    }

    @Test
    fun `rating bands stay positive`() {
        prefs.ratingExcellentMeters = -5f
        assertEquals(1f, prefs.ratingExcellentMeters, 0.01f)
        prefs.mapAssistedMeters = -1f
        assertEquals(1f, prefs.mapAssistedMeters, 0.01f)
    }

    @Test
    fun `boolean toggles round trip`() {
        prefs.batteryBenchmarkEnabled = true
        assertTrue(prefs.batteryBenchmarkEnabled)
        prefs.autoPauseEnabled = false
        assertEquals(false, prefs.autoPauseEnabled)
    }

    @Test
    fun testRunSpeedPauseThresholdKmhClampsToRange() {
        assertEquals(30f, prefs.runSpeedPauseThresholdKmh, 0.01f)
        prefs.runSpeedPauseThresholdKmh = 10f   // below floor
        assertEquals(15f, prefs.runSpeedPauseThresholdKmh, 0.01f)
        prefs.runSpeedPauseThresholdKmh = 100f  // above ceiling
        assertEquals(40f, prefs.runSpeedPauseThresholdKmh, 0.01f)
    }
}
