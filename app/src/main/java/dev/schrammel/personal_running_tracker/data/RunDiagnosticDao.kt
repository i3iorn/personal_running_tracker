package dev.schrammel.personal_running_tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDiagnosticDao {
    @Insert
    suspend fun insertDiagnostic(diagnostic: RunDiagnostic): Long

    @Query("SELECT * FROM run_diagnostics WHERE runId = :runId")
    suspend fun getDiagnosticByRunId(runId: Long): RunDiagnostic?

    @Query("SELECT * FROM run_diagnostics ORDER BY runId DESC")
    fun observeAllDiagnostics(): Flow<List<RunDiagnostic>>

    @Query("SELECT * FROM run_diagnostics ORDER BY runId DESC")
    suspend fun getAllDiagnostics(): List<RunDiagnostic>
}