package dev.schrammel.personal_running_tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.schrammel.personal_running_tracker.model.GpsObservation
import dev.schrammel.personal_running_tracker.data.GpxImporter
import java.io.StringReader
import java.time.Instant

class GpxImporterTest {

    private val gpxContent = """
            <gpx version="1.1" creator="Personal Running Tracker" name="Run">
              <metadata><name>Imported Run</name></metadata>
              <trk>
                <name>Imported Run</name>
                <trkseg>
                  <trkpt lat="40.7128" lon="-74.0060" elevation="10" time="2026-11-26T08:00:00"/>
                  <trkpt lat="40.7130" lon="-74.0065" elevation="12" time="2026-11-26T08:05:00"/>
                  <trkpt lat="40.7140" lon="-74.0070" elevation="15" time="2026-11-26T08:10:00"/>
                </trkseg>
              </trk>
            </gpx>
          """;

    @Test
    fun `imports simple trkpt points into run`() {
        val result = GpxImporter().importGpx(gpxContent)
        assertTrue(result.run.id > 0)
        assertEquals(3, result.gpsObservations.size)
        assertNotNull(result.gpsObservations[0].latitude)
        assertNotNull(result.gpsObservations[0].longitude)
    }
}