package dev.schrammel.personal_running_tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Insert
    suspend fun insertRun(run: RunEntity): Long

    @Update
    suspend fun updateRun(run: RunEntity)

    @Query("SELECT * FROM runs ORDER BY startTimestamp DESC")
    fun observeAllRuns(): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun getRunById(id: Long): RunEntity?

    @Query("DELETE FROM runs WHERE id = :id")
    suspend fun deleteRun(id: Long)
}
