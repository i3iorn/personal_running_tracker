/** Data model for tracking diagnostics (battery, accuracy, etc.). 
 * All fields are optional; the feature is gated by `batteryBenchmarkEnabled` 
 * and `batteryBenchmarkEnabled` in `TrackingPreferences`.
 *
 * Pure Kotlin: no Android dependencies, fully unit-testable.
 */
package dev.schrammel.personal_running_tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "run_diagnostics")
data class RunDiagnostic(
    @PrimaryKey val runId: Long,
    val batteryStartPercent: Int?,          // battery % at run start
    val batteryEndPercent: Int?,            // battery % at run end
    val batteryConsumedPercent: Int?,       // consumed % (end - start)
    val batteryDurationMs: Long,            // total run time (ms)
    val gpsFixCount: Int,                   // number of GPS fixes recorded
    val sensorFixCount: Int,                // number of sensor steps recorded
    val fusedVsGpsDeltaMeters: Double,      // fused distance - gps distance (m)
    val maxAccuracyMeters: Float?,          // worst-case accuracy error (m)
    val accuracyRating: Int?,               // 0-5 rating of overall accuracy
)