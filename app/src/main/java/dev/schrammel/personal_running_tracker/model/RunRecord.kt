package dev.schrammel.personal_running_tracker.model

data class RunRecord(
    val id: Long = 0L,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val elapsedDurationSeconds: Long,
    val movingDurationSeconds: Long,
    val pausedDurationSeconds: Long,
    val distanceMeters: Double,
    val originalDistanceMeters: Double,
    val averagePaceSecondsPerMeter: Double,
    val averageSpeedMetersPerSecond: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val stepCount: Long,
    val trackingProfile: String,
    val trackingAlgorithmVersion: String,
    val userAdjusted: Boolean = false,
    val notes: String = "",
    val runningDurationSeconds: Long = 0,
    val walkingDurationSeconds: Long = 0,
    val createdAt: Long,
    val modifiedAt: Long,
)

data class GpsObservation(
    val id: Long = 0L,
    val runId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val horizontalAccuracyMeters: Float,
    val verticalAccuracyMeters: Float,
    val speedMetersPerSecond: Float,
    val bearingDegrees: Float,
    val provider: String,
    val measurementQuality: Int,
)

enum class TimeRange(val label: String) {
    LAST_7_DAYS("Last 7 days"),
    LAST_30_DAYS("Last 30 days"),
    LAST_90_DAYS("Last 90 days"),
    LAST_365_DAYS("Last 365 days"),
    CUSTOM("Custom date range"),
    CURRENT_WEEK("Current week"),
    CURRENT_MONTH("Current month"),
    CURRENT_YEAR("Current year"),
    PREVIOUS_YEAR("Previous year"),
    ALL_TIME("All time"),
}
