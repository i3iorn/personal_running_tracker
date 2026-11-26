package dev.schrammel.personal_running_tracker.domain.gps

/**
 * Implausible-fix rejection (ultra review #10): a single wild fix must not
 * poison the route, distance, splits or map. A fix is rejected when the implied
 * segment speed is beyond human or vehicle plausibility AND the fix itself is
 * not high-accuracy (accurate high-speed fixes — e.g. riding in a car home —
 * are legitimate and kept). Pure Kotlin.
 */
object FixPlausibility {

    const val MAX_IMPLIED_SPEED_MPS = 15.0   // 54 km/h
    const val ACCURACY_TRUST_METERS = 10.0

    fun isImplausible(
        segmentDistanceMeters: Double,
        segmentDurationMs: Long,
        accuracyMeters: Float,
    ): Boolean {
        if (segmentDurationMs <= 0) return false
        if (accuracyMeters <= ACCURACY_TRUST_METERS) return false
        val speed = segmentDistanceMeters / (segmentDurationMs / 1000.0)
        return speed > MAX_IMPLIED_SPEED_MPS
    }
}
