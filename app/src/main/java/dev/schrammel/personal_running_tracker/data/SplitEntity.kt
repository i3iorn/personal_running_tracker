package dev.schrammel.personal_running_tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Per-split metrics computed from raw GPS observations at run finish. */
@Entity(tableName = "run_splits")
data class SplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val splitNumber: Int,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val averagePaceSecondsPerMeter: Double,
    val averageSpeedMetersPerSecond: Double,
    val elevationChangeMeters: Double,
)
