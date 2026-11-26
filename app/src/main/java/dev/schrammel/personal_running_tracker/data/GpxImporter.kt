package dev.schrammel.personal_running_tracker.data

import dev.schrammel.personal_running_tracker.model.GpsObservation
import dev.schrammel.personal_running_tracker.model.RunRecord
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.StringReader
import org.xml.sax.InputSource
import java.time.Instant
import java.time.format.DateTimeFormatter

class GpxImporter {

    companion object {
        /** Refuse absurd GPX inputs before parsing (DoS guard). */
        const val MAX_XML_CHARS = 10_000_000
    }

    data class ImportResult(
        val run: RunRecord,
        val gpsObservations: List<GpsObservation>,
    )

    fun importGpx(xmlContent: String, maxPoints: Int = 100_000, maxXmlChars: Long = MAX_XML_CHARS.toLong()): ImportResult {
        if (xmlContent.length > maxXmlChars) {
            throw IllegalArgumentException("GPX file too large (limit ${maxXmlChars / 1_000_000} MB)")
        }
        val factory = DocumentBuilderFactory.newInstance()
        // Hardened against XXE / external entity attacks on untrusted input
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.isNamespaceAware = true
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(InputSource(StringReader(xmlContent)))

        val observations = mutableListOf<GpsObservation>()
        val trkpts = doc.getElementsByTagName("trkpt")

        var runId = System.currentTimeMillis()
        var runName = "Imported Run"
        var startTimestamp = 0L
        var endTimestamp = 0L
        var totalDistance = 0.0

        for (i in 0 until trkpts.getLength()) {
            val node = trkpts.item(i) as Element
            val lat = (node.attributes.getNamedItem("lat")?.nodeValue)?.toDouble() ?: 0.0
            val lon = (node.attributes.getNamedItem("lon")?.nodeValue)?.toDouble() ?: 0.0
            val ele = (node.attributes.getNamedItem("ele")?.nodeValue)?.toDouble() ?: 0.0

            // Support time as attribute (our exporter format) or child element (standard GPX)
            val timeAttr = node.attributes.getNamedItem("time")?.nodeValue
            val timeChild = node.getElementsByTagName("time").item(0)
            val timeStr = timeAttr ?: timeChild?.textContent?.trim() ?: ""
            val timestamp = parseGpxTime(timeStr)

            if (startTimestamp == 0L && timestamp != null) {
                startTimestamp = timestamp
            }
            if (timestamp != null) {
                endTimestamp = timestamp
            }

            val obs = GpsObservation(
                runId = runId,
                timestamp = timestamp ?: System.currentTimeMillis(),
                latitude = lat,
                longitude = lon,
                altitudeMeters = ele,
                horizontalAccuracyMeters = 5.0f,
                verticalAccuracyMeters = 10.0f,
                speedMetersPerSecond = 0.0f,
                bearingDegrees = 0.0f,
                provider = "GPX_IMPORT",
                measurementQuality = 1,
            )
            observations.add(obs)
        }

        totalDistance = calculateDistance(observations)

        val run = RunRecord(
            id = runId,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            elapsedDurationSeconds = (endTimestamp - startTimestamp) / 1000,
            movingDurationSeconds = (endTimestamp - startTimestamp) / 1000,
            pausedDurationSeconds = 0L,
            distanceMeters = totalDistance,
            originalDistanceMeters = totalDistance,
            averagePaceSecondsPerMeter = if (totalDistance > 0) (endTimestamp - startTimestamp).toDouble() / totalDistance else 0.0,
            averageSpeedMetersPerSecond = if ((endTimestamp - startTimestamp) > 0) totalDistance / ((endTimestamp - startTimestamp).toDouble() / 1000.0) else 0.0,
            elevationGainMeters = 0.0,
            elevationLossMeters = 0.0,
            stepCount = 0L,
            trackingProfile = "balanced",
            trackingAlgorithmVersion = "gpx_import_1.0",
            userAdjusted = false,
            notes = "Imported from GPX: $runName",
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
        )

        return ImportResult(run = run, gpsObservations = observations)
    }

    private fun parseGpxTime(timeStr: String): Long? {
        return try {
            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(timeStr)).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    private fun calculateDistance(observations: List<GpsObservation>): Double {
        if (observations.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until observations.size) {
            total += haversineDistance(
                observations[i - 1].latitude, observations[i - 1].longitude,
                observations[i].latitude, observations[i].longitude,
            )
        }
        return total
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) *
                Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}