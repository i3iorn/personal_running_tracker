package dev.schrammel.personal_running_tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "estimated_positions")
data class EstimatedPositionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val runId: Long,
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val estimatedSpeedMps: Double,
    val estimatedHeadingDegrees: Double,
    val estimatedDistanceMeters: Double,
    val positionConfidence: Double, // 0.0 - 1.0
    val source: String,           // GPS / SENSOR_FUSED / STEP_ESTIMATED / INTERPOLATED
    val uncertaintyMeters: Double
)
