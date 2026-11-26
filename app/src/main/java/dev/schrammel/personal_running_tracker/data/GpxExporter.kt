package dev.schrammel.personal_running_tracker.data

import dev.schrammel.personal_running_tracker.model.GpsObservation
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** GPX 1.1 export: track points with standard <ele> and ISO-8601 UTC <time> children. */
class GpxExporter {

    private val utc = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

    fun toGpx(runId: Long = 1L, gpsObservations: List<GpsObservation>): String {
        val lines = mutableListOf<String>()
        lines.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        lines.add("<gpx version=\"1.1\" creator=\"Personal Running Tracker\" " +
            "xmlns=\"http://www.topografix.com/GPX/1/1\">")
        lines.add("  <metadata><name>Run $runId</name></metadata>")
        lines.add("  <trk>")
        lines.add("    <name>Run $runId</name>")
        lines.add("    <trkseg>")

        for (obs in gpsObservations.sortedBy { it.timestamp }) {
            lines.add(
                "      <trkpt lat=\"%.7f\" lon=\"%.7f\"><ele>%.2f</ele><time>%s</time></trkpt>".format(
                    obs.latitude, obs.longitude, obs.altitudeMeters,
                    utc.format(Instant.ofEpochMilli(obs.timestamp)),
                )
            )
        }

        lines.add("    </trkseg>")
        lines.add("  </trk>")
        lines.add("</gpx>")
        return lines.joinToString("\n")
    }
}
