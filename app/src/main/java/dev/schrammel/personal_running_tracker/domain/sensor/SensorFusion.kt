package dev.schrammel.personal_running_tracker.domain.sensor

/**
 * Reconciles GPS anchors with sensor-estimated distance.
 *
 * Core guarantees (from the spec):
 *   * Never counts the same movement twice: when a GPS anchor arrives, the
 *     pending sensor estimate for that segment is replaced (not added on top).
 *   * GPS glitches are filtered against the sensor guidance.
 *   * Position uncertainty and confidence are tracked explicitly.
 *
 * Pure Kotlin: deterministic, fully unit-testable.
 */
class SensorFusion(
    private val maxGpsSegmentRatio: Double = 10.0,
    private val uncertaintyPerStepMeters: Double = 5.0,
) {
    private var confirmedDistance: Double = 0.0
    private var pendingSensorDistance: Double = 0.0
    private var lastAnchor: GpsAnchor? = null
    private var uncertaintyValue: Double = 0.0
    private var confidenceValue: Double = 1.0
    private var lastBearingDegrees: Double = 0.0

    /** Total accepted distance: confirmed GPS-anchored segments plus unverified sensor estimate. */
    val totalDistanceMeters: Double
        get() = confirmedDistance + pendingSensorDistance

    /** Distance accumulated since the last GPS anchor (unverified sensor estimate). */
    val pendingSensorDistanceMeters: Double
        get() = pendingSensorDistance

    /** Current position confidence in [0, 1]. */
    val confidence: Double
        get() = confidenceValue

    /** Current position uncertainty in meters. */
    val uncertaintyMeters: Double
        get() = uncertaintyValue

    /** Feed one step's distance; grows uncertainty since verification is aging. */
    fun onSensorStep(strideLengthMeters: Double) {
        pendingSensorDistance += strideLengthMeters.coerceAtLeast(0.0)
        uncertaintyValue += uncertaintyPerStepMeters
        confidenceValue = (confidenceValue * CONFIDENCE_DECAY_PER_STEP).coerceAtLeast(MIN_CONFIDENCE)
    }

    /** Reconcile a new GPS anchor against the sensor estimate. */
    fun onGpsAnchor(anchor: GpsAnchor): FusionUpdate {
        if (lastAnchor == null) {
            // First anchor: establish baseline, nothing to confirm yet.
            lastAnchor = anchor
            uncertaintyValue = anchor.accuracyMeters.toDouble().coerceAtLeast(0.0)
            confidenceValue = MAX_CONFIDENCE
            return FusionUpdate(totalDistanceMeters = 0.0, confirmedSegmentDistanceMeters = 0.0,
                sensorDistanceAppliedMeters = pendingSensorDistance, uncertaintyMeters = uncertaintyValue,
                confidence = confidenceValue, usedGps = false)
        }

        val gpsSegment = GeoMath.haversineMeters(
            lastAnchor!!.latitude, lastAnchor!!.longitude, anchor.latitude, anchor.longitude,
        )
        var segmentDistance = 0.0
        var sensorApplied = pendingSensorDistance
        var usedGps = false

        when {
            gpsSegment > MAX_JUMP_METERS || gpsSegment < 0.0 -> {
                // Absurd GPS segment (discontinuity / freeze): ignore GPS entirely.
                segmentDistance = pendingSensorDistance // keep sensor estimate
                sensorApplied = pendingSensorDistance
                usedGps = false
                confidenceValue = (confidenceValue * UNTRUSTED_GPS_PENALTY).coerceAtLeast(MIN_CONFIDENCE)
                uncertaintyValue += UNTRUSTED_GPS_UNCERTAINTY_PENALTY
            }
            pendingSensorDistance > 0.0 && gpsSegment > pendingSensorDistance * maxGpsSegmentRatio -> {
                // GPS jump far beyond sensor guidance: likely glitch; trust sensor.
                segmentDistance = pendingSensorDistance
                sensorApplied = pendingSensorDistance
                usedGps = false
            }
            else -> {
                segmentDistance = gpsSegment
                sensorApplied = gpsSegment
                usedGps = true
                uncertaintyValue = anchor.accuracyMeters.toDouble().coerceAtLeast(0.0)
                confidenceValue = MAX_CONFIDENCE
                lastBearingDegrees = anchor.bearingDegrees.toDouble().coerceIn(0.0, 360.0)
            }
        }

        confirmedDistance += segmentDistance
        pendingSensorDistance = 0.0
        lastAnchor = anchor

        return FusionUpdate(
            totalDistanceMeters = totalDistanceMeters,
            confirmedSegmentDistanceMeters = segmentDistance,
            sensorDistanceAppliedMeters = sensorApplied,
            uncertaintyMeters = uncertaintyValue,
            confidence = confidenceValue,
            usedGps = usedGps,
        )
    }

    companion object {
        const val MAX_JUMP_METERS = 5_000.0
        const val MAX_CONFIDENCE = 1.0
        const val MIN_CONFIDENCE = 0.05
        const val CONFIDENCE_DECAY_PER_STEP = 0.02
        const val UNTRUSTED_GPS_PENALTY = 0.9
        const val UNTRUSTED_GPS_UNCERTAINTY_PENALTY = 20.0
    }
}

/**
 * Result of reconciling a GPS anchor.
 */
data class FusionUpdate(
    val totalDistanceMeters: Double,
    val confirmedSegmentDistanceMeters: Double,
    val sensorDistanceAppliedMeters: Double,
    val uncertaintyMeters: Double,
    val confidence: Double,
    val usedGps: Boolean,
)
