package dev.schrammel.personal_running_tracker.domain.sensor

/**
 * Type of sensor producing an observation.
 */
enum class SensorType {
    ACCELEROMETER,
    GYROSCOPE,
    STEP_COUNTER,
    ROTATION_VECTOR,
    MAGNETOMETER,
}

/** Default sensor status flag: high accuracy. */
const val SENSOR_STATUS_ACCURACY_HIGH = 3

/**
 * Raw sensor observation from Android SensorEvent.
 * Kept minimal and serializable for Room storage.
 */
data class SensorObservation(
    val runId: Long,
    val timestampMs: Long,
    val sensorType: SensorType,
    val values: FloatArray,
    val accuracy: Int = SENSOR_STATUS_ACCURACY_HIGH,
)

/**
 * Estimated position from sensor fusion between GPS anchors.
 * Source indicates the primary data source for this estimate.
 */
enum class PositionSource {
    GPS,
    SENSOR_FUSED,
    STEP_ESTIMATED,
    INTERPOLATED,
}

/**
 * Position estimate with confidence metadata.
 */
data class EstimatedPosition(
    val runId: Long,
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val estimatedSpeedMps: Double,
    val estimatedHeadingDegrees: Double,
    val estimatedDistanceMeters: Double,
    val positionConfidence: Double, // 0.0 - 1.0
    val source: PositionSource,
    val uncertaintyMeters: Double,
)

/**
 * Accumulated tracking state between GPS fixes.
 */
data class TrackingState(
    val stepCount: Long = 0L,
    val estimatedDistanceMeters: Double = 0.0,
    val currentStrideLengthMeters: Double = 0.75,
    val lastAccelMagnitude: Double = 0.0,
    val lastStepTimestampMs: Long = 0L,
    val headingDegrees: Double = 0.0,
    val speedMps: Double = 0.0,
    val positionConfidence: Double = 1.0,
    val uncertaintyMeters: Double = 0.0,
    val lastGpsAnchorTimeMs: Long = 0L,
    val distanceSinceLastAnchor: Double = 0.0,
    val headingChangeSinceAnchor: Double = 0.0,
)

/**
 * GPS anchor: a reliable position fix with metadata.
 */
data class GpsAnchor(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val timestampMs: Long,
    val accuracyMeters: Float,
    val speedMps: Float,
    val bearingDegrees: Float,
)

/**
 * Adaptive GPS tracking state.
 */
enum class TrackingMode {
    INITIAL_ACQUISITION,
    STABLE_MOVEMENT,
    UNSTABLE_MOVEMENT,
    POOR_GPS_RECEPTION,
    STATIONARY,
    RECOVERY,
}

/**
 * Battery tracking profile affecting GPS/sensor behavior.
 */
enum class BatteryProfile {
    SAVER,
    BALANCED,
    HIGH_ACCURACY,
}

/**
 * Configuration for adaptive GPS acquisition based on battery profile.
 */
data class GpsAcquisitionConfig(
    val minIntervalMs: Long,
    val maxIntervalMs: Long,
    val minDistanceMeters: Float,
    val accuracyThresholdMeters: Float,
) {
    companion object {
        fun forProfile(profile: BatteryProfile): GpsAcquisitionConfig = when (profile) {
            BatteryProfile.SAVER -> GpsAcquisitionConfig(
                minIntervalMs = 60_000L,       // 1 min
                maxIntervalMs = 300_000L,      // 5 min
                minDistanceMeters = 100f,
                accuracyThresholdMeters = 50f,
            )
            BatteryProfile.BALANCED -> GpsAcquisitionConfig(
                minIntervalMs = 30_000L,       // 30 sec
                maxIntervalMs = 120_000L,      // 2 min
                minDistanceMeters = 30f,
                accuracyThresholdMeters = 30f,
            )
            BatteryProfile.HIGH_ACCURACY -> GpsAcquisitionConfig(
                minIntervalMs = 5_000L,        // 5 sec
                maxIntervalMs = 30_000L,       // 30 sec
                minDistanceMeters = 10f,
                accuracyThresholdMeters = 15f,
            )
        }
    }
}