/** Tuning parameters for the tracking algorithm, configurable via Settings.
 * All values have sensible defaults; when features are removed,
 * the defaults are used and the preference entries can be cleaned up.
 * Pure Kotlin: no Android dependencies, fully unit-testable.
 */
class TrackingTuning(
    /** Minimum interval between GPS fixes (ms). */
    val gpsMinIntervalMs: Long = 30_000L,
    /** Maximum interval between GPS fixes (ms). */
    val gpsMaxIntervalMs: Long = 120_000L,
    /** Minimum distance travelled before a GPS fix is requested. */
    val gpsMinDistanceMeters: Float = 30f,
    /** GPS horizontal accuracy threshold (meters). Larger = poorer GPS quality. */
    val gpsAccuracyThresholdMeters: Float = 30f,
    /** Stride length used when step-based distance estimation is active. */
    val strideLengthMeters: Double = 0.75,
    /** Ratio: if a GPS segment exceeds pendingSensorDistance * this ratio,
     * the GPS segment is considered a likely glitch and sensor is trusted. */
    val sensorFusionMaxGpsSegmentRatio: Double = 10.0,
    /** Uncertainty added per step when no GPS fix is available. */
    val sensorFusionUncertaintyPerStepMeters: Double = 5.0,
    /** How much GPS uncertainty degrades before a fix is re-requested in STABLE mode. */
    val stableUncertaintyReevaluateMeters: Double = 50.0,
    /** Number of recent stride calibration observations to retain. */
    val strideCalibrationMaxHistory: Int = 20,
) {
    companion object {
        /** Build from a battery profile — used when the feature flag is off. */
        fun forProfile(profile: String): TrackingTuning = when (profile) {
            "SAVER" -> TrackingTuning(
                gpsMinIntervalMs = 60_000L,
                gpsMaxIntervalMs = 300_000L,
                gpsMinDistanceMeters = 100f,
                gpsAccuracyThresholdMeters = 50f,
                sensorFusionMaxGpsSegmentRatio = 15.0,
            )
            "HIGH_ACCURACY" -> TrackingTuning(
                gpsMinIntervalMs = 5_000L,
                gpsMaxIntervalMs = 30_000L,
                gpsMinDistanceMeters = 10f,
                gpsAccuracyThresholdMeters = 15f,
                sensorFusionMaxGpsSegmentRatio = 5.0,
            )
            else -> // "BALANCED" default
                TrackingTuning()
        }
    }
}