package dev.schrammel.personal_running_tracker.stats

import dev.schrammel.personal_running_tracker.model.RunRecord
import dev.schrammel.personal_running_tracker.model.TimeRange
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.Calendar
import java.util.GregorianCalendar

class StatisticsEngineRangeTest {

    private fun runAt(startTimestampMs: Long): RunRecord = RunRecord(
        startTimestamp = startTimestampMs,
        endTimestamp = startTimestampMs + 1800_000L,
        elapsedDurationSeconds = 1800L,
        movingDurationSeconds = 1500L,
        pausedDurationSeconds = 300L,
        distanceMeters = 5000.0,
        originalDistanceMeters = 5000.0,
        averagePaceSecondsPerMeter = 36.0,
        averageSpeedMetersPerSecond = 2.78,
        elevationGainMeters = 0.0,
        elevationLossMeters = 0.0,
        stepCount = 0L,
        trackingProfile = "Balanced",
        trackingAlgorithmVersion = "1.0",
        userAdjusted = false,
        notes = "",
        createdAt = startTimestampMs,
        modifiedAt = startTimestampMs,
    )

    @Test
    fun `all time range returns all runs`() {
        val runs = listOf(runAt(1L), runAt(2L))
        val selected = StatisticsEngine.runsInRange(runs, TimeRange.ALL_TIME, System.currentTimeMillis())
        assertEquals(2, selected.size)
    }

    @Test
    fun `last seven days returns only recent runs`() {
        val now = System.currentTimeMillis()
        val runs = listOf(
            runAt(now - 2L * 24L * 3600L * 1000L), // 2 days ago -> in range
            runAt(now - 30L * 24L * 3600L * 1000L), // 30 days ago -> out of range
        )
        val selected = StatisticsEngine.runsInRange(runs, TimeRange.LAST_7_DAYS, now)
        assertEquals(1, selected.size)
    }

    @Test
    fun `custom range from and to`() {
        val runs = listOf(
            runAt(0L),
            runAt(100L * 24L * 3600L * 1000L),
            runAt(200L * 24L * 3600L * 1000L),
        )
        val selected = StatisticsEngine.runsInRange(runs, TimeRange.CUSTOM, 0L, 0L, 150L * 24L * 3600L * 1000L)
        assertEquals(2, selected.size)
    }

    private fun startOfTodayMs(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    @Test
    fun `current week returns runs within current week`() {
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance()
        val dayOfWeek = c.get(Calendar.DAY_OF_WEEK) - 1
        val startOfWeek = now - dayOfWeek.toLong() * 24L * 3600L * 1000L
        val runs = listOf(
            runAt(startOfWeek - 3L * 24L * 3600L * 1000L),
            runAt(now),
        )
        val selected = StatisticsEngine.runsInRange(runs, TimeRange.CURRENT_WEEK, now)
        assertEquals(1, selected.size)
    }
}
