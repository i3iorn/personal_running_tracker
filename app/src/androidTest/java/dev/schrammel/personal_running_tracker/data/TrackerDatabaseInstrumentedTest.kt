package dev.schrammel.personal_running_tracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented tests for DAO queries and crash-recovery state restore (spec §15.4). */
@RunWith(AndroidJUnit4::class)
class TrackerDatabaseInstrumentedTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun run(id: Long) = RunEntity(
        id = id, startTimestamp = id * 1000, endTimestamp = id * 1000 + 60_000,
        elapsedDurationSeconds = 60, movingDurationSeconds = 55, pausedDurationSeconds = 5,
        distanceMeters = 200.0, originalDistanceMeters = 200.0,
        averagePaceSecondsPerMeter = 0.3, averageSpeedMetersPerSecond = 3.33,
        elevationGainMeters = 0.0, elevationLossMeters = 0.0, stepCount = 100,
        trackingProfile = "Balanced", trackingAlgorithmVersion = "1.0.0",
        createdAt = id * 1000, modifiedAt = id * 1000,
    )

    @Test
    fun insertAndQueryRun_roundTrips() = runBlocking {
        db.runDao().insertRun(run(1))
        db.runDao().insertRun(run(2))
        val all = db.runDao().observeAllRuns()
        // Flow: take the first emission
        val list = all.first()
        assertEquals(2, list.size)
        // Ordered newest-first by startTimestamp
        assertEquals(2L, list[0].id)
    }

    @Test
    fun updateRun_preservesOriginalDistance() = runBlocking {
        db.runDao().insertRun(run(1))
        val loaded = db.runDao().getRunById(1)!!
        db.runDao().updateRun(loaded.copy(distanceMeters = 250.0, userAdjusted = true))
        val updated = db.runDao().getRunById(1)!!
        assertEquals(250.0, updated.distanceMeters, 0.01)
        assertEquals(200.0, updated.originalDistanceMeters, 0.01) // original preserved
        assertEquals(true, updated.userAdjusted)
    }

    @Test
    fun gpsQuery_returnsOnlyRunObservations_orderedByTimestamp() = runBlocking {
        db.runDao().insertRun(run(1))
        db.runDao().insertRun(run(2))
        val points = listOf(3000L, 1000L, 2000L)
        points.forEach { ts ->
            db.gpsDao().insertGps(
                GpsEntity(runId = 1, timestamp = ts, latitude = 47.0, longitude = 11.0,
                    altitudeMeters = 500.0, horizontalAccuracyMeters = 5f,
                    verticalAccuracyMeters = 0f, speedMetersPerSecond = 3f,
                    bearingDegrees = 0f, provider = "gps", measurementQuality = 1)
            )
        }
        val forRun1 = db.gpsDao().getGpsForRun(1)
        assertEquals(3, forRun1.size)
        assertEquals(listOf(1000L, 2000L, 3000L), forRun1.map { it.timestamp })
        assertEquals(0, db.gpsDao().getGpsForRun(2).size)
    }

    @Test
    fun runState_upsertAndClear_supportsCrashRecovery() = runBlocking {
        val state = RunStateEntity(
            runId = 42, isRunning = true, isPaused = false,
            accumElapsedMs = 30_000, accumMovingMs = 28_000, segmentStartMs = 1,
            distanceMeters = 150.0, stepCount = 200, trackingProfile = "Balanced",
            batteryStartPct = 88,
        )
        db.runStateDao().upsert(state)
        val restored = db.runStateDao().get()
        assertEquals(42L, restored!!.runId)
        assertEquals(150.0, restored.distanceMeters, 0.01)
        assertEquals(true, restored.isRunning)
        // Repeated upsert replaces the single row
        db.runStateDao().upsert(state.copy(distanceMeters = 175.0))
        assertEquals(175.0, db.runStateDao().get()!!.distanceMeters, 0.01)
        // Finishing clears the state so no stale run resumes
        db.runStateDao().clear()
        assertNull(db.runStateDao().get())
    }

    @Test
    fun splits_roundTrip() = runBlocking {
        db.runDao().insertRun(run(1))
        db.splitDao().insertSplits(listOf(
            SplitEntity(runId = 1, splitNumber = 1, distanceMeters = 1000.0,
                durationSeconds = 300, averagePaceSecondsPerMeter = 0.3,
                averageSpeedMetersPerSecond = 3.33, elevationChangeMeters = 12.0),
            SplitEntity(runId = 1, splitNumber = 2, distanceMeters = 1000.0,
                durationSeconds = 310, averagePaceSecondsPerMeter = 0.31,
                averageSpeedMetersPerSecond = 3.22, elevationChangeMeters = -3.0),
        ))
        val splits = db.splitDao().getSplitsForRun(1)
        assertEquals(2, splits.size)
        assertEquals(1, splits[0].splitNumber) // ordered by splitNumber
        assertEquals(-3.0, splits[1].elevationChangeMeters, 0.01)
    }
}
