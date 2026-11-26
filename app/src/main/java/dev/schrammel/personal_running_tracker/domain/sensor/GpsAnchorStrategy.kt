package dev.schrammel.personal_running_tracker.domain.sensor

/**
 * Decides when to request a new GPS fix based on movement, uncertainty,
 * time since the last anchor, and GPS quality. This is the heart of the
 * battery-efficient hybrid positioning approach: GPS is only re-acquired
 * when it is actually needed.
 *
 * Pure Kotlin: no Android dependencies, deterministic, unit-testable.
 */
class GpsAnchorStrategy(
    private val config: GpsAcquisitionConfig,
) {
    private var hasInitialFix = false
    private var lastRequestTimeMs: Long = 0L

    /**
     * Evaluate the current tracking tick and decide whether to request GPS.
     *
     * @param nowMs current wall-clock time in ms.
     * @param state accumulated sensor tracking state.
     * @param movementDetected whether motion was detected since the last tick.
     * @param gpsAccuracyMeters accuracy of the most recent fix (large = poor).
     * @return a decision containing the current mode and whether to request GPS.
     */
    fun onTick(
        nowMs: Long,
        state: TrackingState,
        movementDetected: Boolean,
        gpsAccuracyMeters: Float,
    ): Decision {
        if (!hasInitialFix) {
            hasInitialFix = true
            lastRequestTimeMs = nowMs
            return Decision(TrackingMode.INITIAL_ACQUISITION, requestGps = true)
        }
        val mode = classify(state, movementDetected, gpsAccuracyMeters)
        val requestGps = shouldRequestFix(mode, state, nowMs)
        if (requestGps) lastRequestTimeMs = nowMs
        return Decision(mode, requestGps)
    }

    private fun classify(
        state: TrackingState,
        movementDetected: Boolean,
        gpsAccuracyMeters: Float,
    ): TrackingMode {
        if (!movementDetected && state.speedMps < STATIONARY_SPEED_THRESHOLD) {
            return TrackingMode.STATIONARY
        }
        if (gpsAccuracyMeters > config.accuracyThresholdMeters) {
            return TrackingMode.POOR_GPS_RECEPTION
        }
        if (state.uncertaintyMeters > UNSTABLE_UNCERTAINTY) {
            return TrackingMode.UNSTABLE_MOVEMENT
        }
        return TrackingMode.STABLE_MOVEMENT
    }

    private fun shouldRequestFix(
        mode: TrackingMode,
        state: TrackingState,
        nowMs: Long,
    ): Boolean = when (mode) {
        TrackingMode.INITIAL_ACQUISITION -> true
        TrackingMode.STATIONARY -> false
        TrackingMode.POOR_GPS_RECEPTION -> true
        TrackingMode.UNSTABLE_MOVEMENT -> true
        TrackingMode.RECOVERY -> true
        TrackingMode.STABLE_MOVEMENT -> {
            val timeElapsedMs = nowMs - lastRequestTimeMs
            timeElapsedMs >= config.minIntervalMs ||
                state.distanceSinceLastAnchor >= config.minDistanceMeters ||
                state.uncertaintyMeters >= config.accuracyThresholdMeters
        }
    }

    data class Decision(
        val mode: TrackingMode,
        val requestGps: Boolean,
    )

    companion object {
        const val STATIONARY_SPEED_THRESHOLD = 0.3
        const val UNSTABLE_UNCERTAINTY = 50.0
    }
}