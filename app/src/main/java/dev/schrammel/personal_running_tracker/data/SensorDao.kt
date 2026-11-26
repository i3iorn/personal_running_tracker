package dev.schrammel.personal_running_tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SensorDao {
    @Insert
    suspend fun insertSensor(obs: SensorEntity): Long

    @Query("SELECT * FROM sensor_observations WHERE runId = :runId ORDER BY timestampMs ASC")
    suspend fun getSensorsForRun(runId: Long): List<SensorEntity>
}

@Dao
interface EstimatedPositionDao {
    @Insert
    suspend fun insertEstimatedPosition(pos: EstimatedPositionEntity): Long

    @Query("SELECT * FROM estimated_positions WHERE runId = :runId ORDER BY timestampMs ASC")
    suspend fun getEstimatedPositionsForRun(runId: Long): List<EstimatedPositionEntity>
}