package dev.schrammel.personal_running_tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GpsDao {
    @Insert
    suspend fun insertGps(obs: GpsEntity): Long

    @Query("SELECT * FROM gps_observations WHERE runId = :runId ORDER BY timestamp ASC")
    suspend fun getGpsForRun(runId: Long): List<GpsEntity>
}
