package dev.schrammel.personal_running_tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Security-focused tests for the untrusted GPX import path. */
class GpxImporterSecurityTest {

    @Test
    fun `doctype declarations are rejected - XXE guard`() {
        val xxe = """
            <?xml version="1.0"?>
            <!DOCTYPE gpx [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <gpx version="1.1"><trk><trkseg>
              <trkpt lat="1.0" lon="2.0">&xxe;</trkpt>
            </trkseg></trk></gpx>
        """.trimIndent()
        assertThrows(Exception::class.java) { GpxImporter().importGpx(xxe) }
    }

    @Test
    fun `oversized input is rejected before parsing`() {
        val huge = "<gpx>" + "x".repeat(GpxImporter.MAX_XML_CHARS + 1) + "</gpx>"
        val exception = assertThrows(IllegalArgumentException::class.java) {
            GpxImporter().importGpx(huge)
        }
        assertTrue(exception.message!!.contains("too large"))
    }

    @Test
    fun `malformed xml throws instead of crashing silently`() {
        assertThrows(Exception::class.java) { GpxImporter().importGpx("<gpx><trk>") }
    }

    @Test
    fun `standard iso time children are parsed`() {
        val xml = """
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg>
                <trkpt lat="47.0" lon="11.0"><ele>500.0</ele><time>2023-11-14T22:13:20Z</time></trkpt>
                <trkpt lat="47.001" lon="11.0"><ele>501.0</ele><time>2023-11-14T22:13:50Z</time></trkpt>
              </trkseg></trk>
            </gpx>
        """.trimIndent()
        val result = GpxImporter().importGpx(xml)
        assertEquals(1_700_000_000_000L, result.gpsObservations[0].timestamp)
        assertEquals(30L, result.run.elapsedDurationSeconds)
        assertEquals(111.3, result.run.distanceMeters, 1.0) // 0.001 deg lat
    }

    @Test
    fun `missing times degrade gracefully to zero timestamps`() {
        val xml = """
            <gpx version="1.1"><trk><trkseg>
              <trkpt lat="47.0" lon="11.0"/>
              <trkpt lat="47.001" lon="11.0"/>
            </trkseg></trk></gpx>
        """.trimIndent()
        val result = GpxImporter().importGpx(xml)
        assertEquals(2, result.gpsObservations.size)
        assertEquals(0L, result.gpsObservations[0].timestamp)
    }
}
