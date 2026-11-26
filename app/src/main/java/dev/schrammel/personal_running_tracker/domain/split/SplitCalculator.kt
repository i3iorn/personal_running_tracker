package dev.schrammel.personal_running_tracker.domain.split

/**
 * Pure-Kotlin split computation from GPS observations.
 * Splits are interpolated at exact split boundaries so a km boundary falling
 * between two fixes gets its proportional time — no double counting, no gaps.
 */
data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val timestampMs: Long,
)

data class ComputedSplit(
    val splitNumber: Int,
    val distanceMeters: Double,
    val durationMs: Long,
    val averagePaceSecondsPerMeter: Double,
    val averageSpeedMetersPerSecond: Double,
    val elevationChangeMeters: Double,
)

object SplitCalculator {

    fun computeSplits(
        points: List<GpsPoint>,
        splitLengthMeters: Double = 1000.0,
    ): List<ComputedSplit> {
        if (points.size < 2 || splitLengthMeters <= 0.0) return emptyList()
        val ordered = points.sortedBy { it.timestampMs }
        val splits = mutableListOf<ComputedSplit>()
        var accumulated = 0.0
        var splitStartTimeMs = ordered.first().timestampMs
        var splitStartAltitude = ordered.first().altitudeMeters
        var splitNumber = 1

        for (i in 1 until ordered.size) {
            val prev = ordered[i - 1]
            val curr = ordered[i]
            val dtMs = (curr.timestampMs - prev.timestampMs).toDouble()
            if (dtMs <= 0.0) continue
            val segment = haversineMeters(prev, curr)
            var remaining = segment
            var cursorTimeMs = prev.timestampMs

            while (accumulated + remaining >= splitLengthMeters && remaining > 0.0) {
                val needed = splitLengthMeters - accumulated
                val fraction = needed / remaining
                val boundaryTimeMs = cursorTimeMs + (dtMs * fraction).toLong().coerceAtLeast(0)
                val durationMs = boundaryTimeMs - splitStartTimeMs
                splits.add(
                    ComputedSplit(
                        splitNumber = splitNumber,
                        distanceMeters = splitLengthMeters,
                        durationMs = durationMs,
                        averagePaceSecondsPerMeter = if (splitLengthMeters > 0) durationMs / 1000.0 / splitLengthMeters else 0.0,
                        averageSpeedMetersPerSecond = if (durationMs > 0) splitLengthMeters / (durationMs / 1000.0) else 0.0,
                        elevationChangeMeters = curr.altitudeMeters - splitStartAltitude,
                    )
                )
                splitNumber++
                accumulated = 0.0
                splitStartTimeMs = boundaryTimeMs
                splitStartAltitude = curr.altitudeMeters
                remaining -= needed
                cursorTimeMs = boundaryTimeMs
            }
            accumulated += remaining
        }
        return splits
    }

    private fun haversineMeters(a: GpsPoint, b: GpsPoint): Double {
        val r = 6378137.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(a.latitude)) * Math.cos(Math.toRadians(b.latitude)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return (2 * r * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))).coerceAtLeast(0.0)
    }
}
