package dev.schrammel.personal_running_tracker.stats

import dev.schrammel.personal_running_tracker.model.RunRecord
import org.junit.Test
import org.junit.Assert.assertEquals

class StatisticsEngineTest {
    private fun makeRun(
        distanceMeters: Double,
        elapsedDurationSeconds: Long,
        movingDurationSeconds: Long
    ): RunRecord = RunRecord(
        startTimestamp = System.currentTimeMillis(),
        endTimestamp = System.currentTimeMillis() + elapsedDurationSeconds * 1000,
        elapsedDurationSeconds = elapsedDurationSeconds,
        movingDurationSeconds = movingDurationSeconds,
        pausedDurationSeconds = elapsedDurationSeconds - movingDurationSeconds,
        distanceMeters = distanceMeters,
        originalDistanceMeters = distanceMeters,
        averagePaceSecondsPerMeter = if (distanceMeters > 0) elapsedDurationSeconds.toDouble() / distanceMeters else 0.0,
        averageSpeedMetersPerSecond = if (elapsedDurationSeconds > 0) distanceMeters / elapsedDurationSeconds else 0.0,
        elevationGainMeters = 0.0,
        elevationLossMeters = 0.0,
        stepCount = 0L,
        trackingProfile = "Balanced",
        trackingAlgorithmVersion = "1.0",
        userAdjusted = false,
        notes = "",
        createdAt = System.currentTimeMillis(),
        modifiedAt = System.currentTimeMillis()
    )

    @Test
    fun `empty runs returns zero summary`() {
        val summary = StatisticsEngine.summarize(emptyList())
        // two-arg assertEquals works for all types via JUnit 4 Object overload
        assertEquals(0.0, summary.totalDistanceMeters, 0.0)
        assertEquals(0, summary.runCount)
        assertEquals(0.0, summary.averageDistanceMeters, 0.0)
    }

    @Test
    fun `single run summary`() {
        val run = makeRun(5000.0, 1800L, 1500L)
        val summary = StatisticsEngine.summarize(listOf(run))
        assertEquals(5000.0, summary.totalDistanceMeters, 0.0)
        assertEquals(1800L, summary.totalDurationSeconds)
        assertEquals(1500L, summary.totalMovingSeconds)
        assertEquals(1, summary.runCount)
        assertEquals(5000.0, summary.averageDistanceMeters, 0.0)
        assertEquals(1800.0, summary.averageDurationSeconds, 0.0)
        assertEquals(5000.0, summary.longestRunMeters, 0.0)
    }

    @Test
    fun `multiple runs aggregates correctly`() {
        val runs = listOf(
            makeRun(5000.0, 1800L, 1500L),
            makeRun(10000.0, 3600L, 3000L),
            makeRun(3000.0, 1200L, 1000L)
        )
        val summary = StatisticsEngine.summarize(runs)
        assertEquals(18000.0, summary.totalDistanceMeters, 0.0)
        assertEquals(6600L, summary.totalDurationSeconds)
        assertEquals(5500L, summary.totalMovingSeconds)
        assertEquals(3, summary.runCount)
        assertEquals(6000.0, summary.averageDistanceMeters, 0.0)
        assertEquals(2200.0, summary.averageDurationSeconds, 0.0)
        assertEquals(10000.0, summary.longestRunMeters, 0.0)
    }
}