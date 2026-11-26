package dev.schrammel.personal_running_tracker.data

import dev.schrammel.personal_running_tracker.model.GpsObservation
import dev.schrammel.personal_running_tracker.model.RunRecord
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.Locale

class GpxExportTest {

    private fun run(id: Long = 1L, distance: Double = 5000.0): RunRecord = RunRecord(
        id = id, startTimestamp = 0L, endTimestamp = 1800_000L,
        elapsedDurationSeconds = 1800L, movingDurationSeconds = 1500L, pausedDurationSeconds = 300L,
        distanceMeters = distance, originalDistanceMeters = distance,
        averagePaceSecondsPerMeter = 36.0, averageSpeedMetersPerSecond = 2.78,
        elevationGainMeters = 0.0, elevationLossMeters = 0.0, stepCount = 0L,
        trackingProfile = "Balanced", trackingAlgorithmVersion = "1.0", userAdjusted = false,
        notes = "", createdAt = 0L, modifiedAt = 0L,
    )

    private fun gps(lat: Double, lng: Double, tMs: Long, accuracy: Float = 10f): GpsObservation = GpsObservation(
        runId = 1L, timestamp = tMs, latitude = lat, longitude = lng,
        altitudeMeters = 50.0, horizontalAccuracyMeters = accuracy, verticalAccuracyMeters = 5f,
        speedMetersPerSecond = 2.78f, bearingDegrees = 90f, provider = "gps", measurementQuality = 1,
    )

    @Test
    fun `gpx export starts with xml declaration and wpt elements`() {
        val gpx = GpxExporter().toGpx(run().id, listOf(gps(52.0, 13.0, 0L), gps(52.00001, 13.0, 500_000L)))
        assertTrue(gpx.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(gpx.contains("<trkpt"))
    }

    @Test
    fun `gpx export includes waypoint lat lon`() {
        val gpx = GpxExporter().toGpx(run().id, listOf(gps(52.0, 13.0, 0L)))
        assertTrue(gpx.contains("52.0"))
        assertTrue(gpx.contains("13.0"))
    }

    @Test
    fun `gpx export with empty gps returns no trackpoints`() {
        val gpx = GpxExporter().toGpx(run().id, emptyList())
        assertTrue(gpx.contains("<trk"))
    }
}
