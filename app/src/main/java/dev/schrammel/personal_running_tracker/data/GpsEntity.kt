package dev.schrammel.personal_running_tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.schrammel.personal_running_tracker.model.GpsObservation

@Entity(tableName = "gps_observations")
data class GpsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
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

fun GpsEntity.toObservation(): GpsObservation = GpsObservation(
    id = id, runId = runId, timestamp = timestamp,
    latitude = latitude, longitude = longitude,
    altitudeMeters = altitudeMeters,
    horizontalAccuracyMeters = horizontalAccuracyMeters,
    verticalAccuracyMeters = verticalAccuracyMeters,
    speedMetersPerSecond = speedMetersPerSecond,
    bearingDegrees = bearingDegrees,
    provider = provider,
    measurementQuality = measurementQuality,
)
