package dev.schrammel.personal_running_tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_observations")
data class SensorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val runId: Long,
    val timestampMs: Long,
    val sensorType: String,
    val values: String, // comma-separated floats
    val accuracy: Int = 3,
)
