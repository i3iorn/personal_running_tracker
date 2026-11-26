package dev.schrammel.personal_running_tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.schrammel.personal_running_tracker.model.RunRecord

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
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
    val createdAt: Long,
    val modifiedAt: Long,
)

fun RunEntity.toRecord(): RunRecord = RunRecord(
    id = id, startTimestamp = startTimestamp, endTimestamp = endTimestamp,
    elapsedDurationSeconds = elapsedDurationSeconds,
    movingDurationSeconds = movingDurationSeconds,
    pausedDurationSeconds = pausedDurationSeconds,
    distanceMeters = distanceMeters,
    originalDistanceMeters = originalDistanceMeters,
    averagePaceSecondsPerMeter = averagePaceSecondsPerMeter,
    averageSpeedMetersPerSecond = averageSpeedMetersPerSecond,
    elevationGainMeters = elevationGainMeters,
    elevationLossMeters = elevationLossMeters,
    stepCount = stepCount,
    trackingProfile = trackingProfile,
    trackingAlgorithmVersion = trackingAlgorithmVersion,
    userAdjusted = userAdjusted,
    notes = notes,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
)

fun RunRecord.toEntity(): RunEntity = RunEntity(
    id = id, startTimestamp = startTimestamp, endTimestamp = endTimestamp,
    elapsedDurationSeconds = elapsedDurationSeconds,
    movingDurationSeconds = movingDurationSeconds,
    pausedDurationSeconds = pausedDurationSeconds,
    distanceMeters = distanceMeters,
    originalDistanceMeters = originalDistanceMeters,
    averagePaceSecondsPerMeter = averagePaceSecondsPerMeter,
    averageSpeedMetersPerSecond = averageSpeedMetersPerSecond,
    elevationGainMeters = elevationGainMeters,
    elevationLossMeters = elevationLossMeters,
    stepCount = stepCount,
    trackingProfile = trackingProfile,
    trackingAlgorithmVersion = trackingAlgorithmVersion,
    userAdjusted = userAdjusted,
    notes = notes,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
)
