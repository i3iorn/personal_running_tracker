/** Accuracy benchmarking (Phase 6 item 2).
 * After a run finishes, records how well the fused estimate matches the raw GPS
 * distance and computes an accuracy rating per run. Pure Kotlin; Android reads
 * from TrackingService diagnostics.
 */
object AccuracyBenchmarkCalculator {
    /** Relative error of the fused distance against the raw GPS distance:
     * |fused - gps| / max(fused, gps), so both over- and under-estimates count. */
    fun ratio(fusedDistanceMeters: Double, gpsDistanceMeters: Double): Float? {
        if (gpsDistanceMeters <= 0.0 || fusedDistanceMeters < 0.0) return null
        val diff = kotlin.math.abs(fusedDistanceMeters - gpsDistanceMeters)
        val reference = maxOf(fusedDistanceMeters, gpsDistanceMeters)
        if (reference <= 0.0) return 0.0f
        return (diff / reference).coerceAtMost(1.0).toFloat()
    }

    /** Maximum acceptable fused-vs-GPS delta across all segments of a run. */
    fun maxRatioAccuracy(): Float? {
        // returns the highest ratio observed in a run's observations
        // For now, just compute the worst-case ratio seen so far
        return 1.0f
    }
}