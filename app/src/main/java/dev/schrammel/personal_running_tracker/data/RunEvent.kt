package dev.schrammel.personal_running_tracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/** Mid-run markers (aid stations, crew stops) recorded without stopping the run. */
@Entity(tableName = "run_events")
data class RunEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val timestamp: Long,
    val label: String,
    val type: String,
)

@Dao
interface RunEventDao {
    @Insert
    suspend fun insert(event: RunEvent): Long

    @Query("SELECT * FROM run_events WHERE runId = :runId ORDER BY timestamp ASC")
    suspend fun getEventsForRun(runId: Long): List<RunEvent>
}

/** Cached personal records per distance (ultra review #12): avoids rescanning
 *  every GPS trail on each Statistics open. Updated incrementally at run finish. */
@Entity(tableName = "pr_cache")
data class PersonalRecordCache(
    @PrimaryKey val distanceMeters: Double,
    val bestSeconds: Long,
    val runId: Long,
    val updatedAt: Long,
)

@Dao
interface PersonalRecordCacheDao {
    @Query("SELECT * FROM pr_cache ORDER BY distanceMeters ASC")
    suspend fun getAll(): List<PersonalRecordCache>

    @Query("SELECT * FROM pr_cache WHERE distanceMeters = :distanceMeters")
    suspend fun get(distanceMeters: Double): PersonalRecordCache?

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(record: PersonalRecordCache)

    @Query("DELETE FROM pr_cache")
    suspend fun clear()
}
