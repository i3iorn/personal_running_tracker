package dev.schrammel.personal_running_tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SplitDao {
    @Insert
    suspend fun insertSplits(splits: List<SplitEntity>)

    @Query("SELECT * FROM run_splits WHERE runId = :runId ORDER BY splitNumber ASC")
    suspend fun getSplitsForRun(runId: Long): List<SplitEntity>
}
