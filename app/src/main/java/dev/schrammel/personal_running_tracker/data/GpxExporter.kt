package dev.schrammel.personal_running_tracker.data

import dev.schrammel.personal_running_tracker.model.GpsObservation
import java.time.Instant

class GpxExporter {

    fun toGpx(runId: Long = 1L, gpsObservations: List<GpsObservation>): String {
        val nowMs = System.currentTimeMillis()
        val dateTime = Instant.ofEpochMilli(nowMs).toString()

        val lines = mutableListOf<String>()
        lines.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        lines.add("<gpx version=\"1.1\" creator=\"Personal Running Tracker\" name=\"Run\" date=\"$dateTime\">")
        lines.add("  <metadata><name>Run $runId</name></metadata>")

        lines.add("  <trk>")
        lines.add("    <name>Run $runId</name>")
        lines.add("    <trkseg>")

        for (obs in gpsObservations) {
            lines.add("      <trkpt lat=\"${obs.latitude}\" lon=\"${obs.longitude}\" elevation=\"${obs.altitudeMeters}\" time=\"${obs.timestamp}\" />")
        }

        lines.add("    </trkseg>")
        lines.add("  </trk>")

        lines.add("</gpx>")
        return lines.joinToString("\n")
    }
}
