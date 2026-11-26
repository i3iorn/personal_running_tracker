package dev.schrammel.personal_running_tracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** Persisted tracking state so an interrupted run survives process death (spec §14). */
@Entity(tableName = "run_state")
data class RunStateEntity(
    @PrimaryKey val id: Int = 1,
    val runId: Long,
    val isRunning: Boolean,
    val isPaused: Boolean,
    val accumElapsedMs: Long,
    val accumMovingMs: Long,
    val segmentStartMs: Long,
    val distanceMeters: Double,
    val stepCount: Long,
    val trackingProfile: String,
    val batteryStartPct: Int?,
)

@Dao
interface RunStateDao {
    @Query("SELECT * FROM run_state WHERE id = 1")
    suspend fun get(): RunStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: RunStateEntity)

    @Query("DELETE FROM run_state")
    suspend fun clear()
}
