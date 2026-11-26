package dev.schrammel.personal_running_tracker.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dev.schrammel.personal_running_tracker.model.RunRecord

class RunRepository(
    private val db: AppDatabase,
    private val scope: CoroutineScope,
) {
    private val _allRuns = MutableStateFlow<List<RunRecord>>(emptyList())
    val allRuns: StateFlow<List<RunRecord>> = _allRuns.asStateFlow()

    init {
        scope.launch {
            db.runDao().observeAllRuns()
                .collect { list -> _allRuns.value = list.map { it.toRecord() } }
        }
    }

    fun saveRun(entity: RunEntity) {
        scope.launch { db.runDao().insertRun(entity) }
    }

    suspend fun getRun(id: Long): RunRecord? = runDao.getRunById(id)?.toRecord()

    suspend fun deleteRun(id: Long) = db.runDao().deleteRun(id)

    suspend fun getGpsForRun(runId: Long) = db.gpsDao().getGpsForRun(runId).map { it.toObservation() }

    suspend fun getSensorsForRun(runId: Long) = db.sensorDao().getSensorsForRun(runId)

    suspend fun getEstimatedPositionsForRun(runId: Long) = db.estimatedPositionDao().getEstimatedPositionsForRun(runId)

    private val runDao get() = db.runDao()
    private val gpsDao get() = db.gpsDao()
    private val sensorDao get() = db.sensorDao()
    private val estimatedPositionDao get() = db.estimatedPositionDao()
}
