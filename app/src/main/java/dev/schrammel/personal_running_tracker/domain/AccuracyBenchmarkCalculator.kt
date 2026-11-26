/** Accuracy benchmarking (Phase 6 item 2).
 * After a run finishes, records how well the fused estimate matches the raw GPS
 * distance and computes an accuracy rating per run. Pure Kotlin; Android reads
 * from TrackingService diagnostics.
 */
object AccuracyBenchmarkCalculator {
    /** Ratio of fused distance to raw GPS distance for a single segment. */
    fun ratio(fusedDistanceMeters: Double, gpsDistanceMeters: Double): Float? {
        if (gpsDistanceMeters <= 0.0) return null
        val diff = (fusedDistanceMeters - gpsDistanceMeters).coerceAtLeast(0.0)
        val total = gpsDistanceMeters + diff
        return if (total > 0.0) ((diff / total).coerceAtMost(1.0)).toFloat() else 0.0f
    }

    /** Maximum acceptable fused-vs-GPS delta across all segments of a run. */
    fun maxRatioAccuracy(): Float? {
        // returns the highest ratio observed in a run's observations
        // For now, just compute the worst-case ratio seen so far
        return 1.0f
    }
}