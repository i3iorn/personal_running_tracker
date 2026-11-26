package dev.schrammel.personal_running_tracker.data

import dev.schrammel.personal_running_tracker.model.GpsObservation
import dev.schrammel.personal_running_tracker.model.RunRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory
import java.io.StringReader
import org.xml.sax.InputSource

class GpxExporterTest {

    private fun run(id: Long = 1L) = RunRecord(
        id = id, startTimestamp = 1_700_000_000_000L, endTimestamp = 1_700_000_060_000L,
        elapsedDurationSeconds = 60, movingDurationSeconds = 60, pausedDurationSeconds = 0,
        distanceMeters = 200.0, originalDistanceMeters = 200.0,
        averagePaceSecondsPerMeter = 0.3, averageSpeedMetersPerSecond = 3.33,
        elevationGainMeters = 0.0, elevationLossMeters = 0.0, stepCount = 0,
        trackingProfile = "Balanced", trackingAlgorithmVersion = "1.0.0",
        createdAt = 1_700_000_000_000L, modifiedAt = 1_700_000_000_000L,
    )

    private fun obs(ts: Long, lat: Double = 47.0, lon: Double = 11.0, ele: Double = 500.0) =
        GpsObservation(
            runId = 1, timestamp = ts, latitude = lat, longitude = lon,
            altitudeMeters = ele, horizontalAccuracyMeters = 5f, verticalAccuracyMeters = 0f,
            speedMetersPerSecond = 3f, bearingDegrees = 0f, provider = "gps", measurementQuality = 1,
        )

    @Test
    fun `export produces valid GPX with all track points`() {
        val gps = listOf(obs(1_700_000_000_000L), obs(1_700_000_030_000L, 47.001), obs(1_700_000_060_000L, 47.002))
        val xml = GpxExporter().toGpx(1L, gps)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
        assertEquals(3, doc.getElementsByTagName("trkpt").length)
        assertTrue(xml.contains("<ele>500.00</ele>"))
        assertTrue(xml.contains("2023-11-14T22:13:20Z")) // epoch ms formatted as ISO UTC
    }

    @Test
    fun `points are emitted in timestamp order regardless of input order`() {
        val gps = listOf(obs(1_700_000_030_000L, 47.001), obs(1_700_000_000_000L))
        val xml = GpxExporter().toGpx(1L, gps)
        val firstIdx = xml.indexOf("47.0000000")
        val secondIdx = xml.indexOf("47.0010000")
        assertTrue(firstIdx in 0 until secondIdx)
    }

    @Test
    fun `empty route produces valid but empty track`() {
        val xml = GpxExporter().toGpx(1L, emptyList())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
        assertEquals(0, doc.getElementsByTagName("trkpt").length)
    }

    @Test
    fun `round trip - exporter output imports back with same timestamps and points`() {
        val gps = listOf(obs(1_700_000_000_000L), obs(1_700_000_030_000L, 47.001))
        val xml = GpxExporter().toGpx(1L, gps)
        val imported = GpxImporter().importGpx(xml)
        assertEquals(2, imported.gpsObservations.size)
        assertEquals(1_700_000_000_000L, imported.gpsObservations[0].timestamp)
        assertEquals(47.001, imported.gpsObservations[1].latitude, 1e-7)
        assertEquals(30L, imported.run.elapsedDurationSeconds)
    }
}
