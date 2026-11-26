package dev.schrammel.personal_running_tracker.domain.stats

import dev.schrammel.personal_running_tracker.domain.split.GpsPoint
import dev.schrammel.personal_running_tracker.model.RunRecord
import dev.schrammel.personal_running_tracker.model.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsEngineTest {

    private val now = 1_800_000_000_000L // fixed clock
    private val day = 86_400_000L

    private fun run(
        id: Long,
        startMs: Long,
        distanceM: Double,
        seconds: Long,
        movingSeconds: Long = seconds,
    ) = RunRecord(
        id = id, startTimestamp = startMs, endTimestamp = startMs + seconds * 1000,
        elapsedDurationSeconds = seconds, movingDurationSeconds = movingSeconds,
        pausedDurationSeconds = seconds - movingSeconds,
        distanceMeters = distanceM, originalDistanceMeters = distanceM,
        averagePaceSecondsPerMeter = if (distanceM > 0) seconds.toDouble() / distanceM else 0.0,
        averageSpeedMetersPerSecond = if (seconds > 0) distanceM / seconds else 0.0,
        elevationGainMeters = 0.0, elevationLossMeters = 0.0, stepCount = 0,
        trackingProfile = "Balanced", trackingAlgorithmVersion = "1.0.0",
        createdAt = startMs, modifiedAt = startMs,
    )

    @Test
    fun `totals aggregate distance, time and averages`() {
        val runs = listOf(
            run(1, now - 2 * day, 5000.0, 1800),
            run(2, now - day, 10000.0, 3600),
        )
        val t = StatisticsEngine.totals(runs, now)
        assertEquals(2, t.runCount)
        assertEquals(15000.0, t.totalDistanceMeters, 0.01)
        assertEquals(5400L, t.totalElapsedSeconds)
        assertEquals(5000.0 * 1800 / 5000.0 + 10000.0 * 3600 / 10000.0, t.totalMovingSeconds.toDouble(), 0.01)
        assertEquals(7500.0, t.averageDistanceMeters, 0.01)
        assertEquals(2700L, t.averageDurationSeconds)
        // distance-weighted pace: 5400 s / 15 km = 360 s/km
        assertEquals(360.0, t.averagePaceSecondsPerKm, 0.01)
        assertEquals(15000.0 / 5400.0, t.averageSpeedMetersPerSecond, 0.001)
        assertEquals(10000.0, t.longestRunMeters, 0.01)
        assertEquals(360.0, t.fastestRunPaceSecondsPerKm!!, 0.01)
    }

    @Test
    fun `empty selection yields zeroed totals with null fastest pace`() {
        val t = StatisticsEngine.totals(emptyList(), now)
        assertEquals(0, t.runCount)
        assertNull(t.fastestRunPaceSecondsPerKm)
        assertEquals(0.0, t.averageWeeklyDistanceMeters, 0.01)
    }

    @Test
    fun `filterRuns respects last N days bounds`() {
        val runs = listOf(
            run(1, now - 3 * day, 1.0, 1),
            run(2, now - 10 * day, 1.0, 1),
            run(3, now - 20 * day, 1.0, 1),
        )
        val filtered = StatisticsEngine.filterRuns(runs, TimeRange.LAST_7_DAYS, now)
        assertEquals(listOf(1L), filtered.map { it.id })
    }

    @Test
    fun `filterRuns all time returns everything`() {
        val runs = listOf(run(1, 0, 1.0, 1), run(2, now, 1.0, 1))
        assertEquals(runs, StatisticsEngine.filterRuns(runs, TimeRange.ALL_TIME, now))
    }

    @Test
    fun `compare reports absolute and percentage deltas`() {
        val current = StatisticsEngine.totals(listOf(run(1, now, 6000.0, 1800)), now)
        val previous = StatisticsEngine.totals(listOf(run(2, now - day, 3000.0, 1800)), now)
        val c = StatisticsEngine.compare(current, previous)
        assertEquals(3000.0, c.absoluteDeltaDistanceMeters, 0.01)
        assertEquals(100.0, c.percentDeltaDistance!!, 0.01)
        assertEquals(0L, c.absoluteDeltaSeconds)
        assertEquals(0.0, c.percentDeltaSeconds!!, 0.01)
    }

    @Test
    fun `compare percentage is null when previous distance is zero`() {
        val current = StatisticsEngine.totals(listOf(run(1, now, 6000.0, 1800)), now)
        val previous = StatisticsEngine.totals(emptyList(), now)
        assertNull(StatisticsEngine.compare(current, previous).percentDeltaDistance)
    }

    @Test
    fun `rolling distance sums within sliding window`() {
        val runs = listOf(
            run(1, now - day, 5000.0, 100),
            run(2, now - 8 * day, 20000.0, 100),
            run(3, now - 9 * day, 30000.0, 100),
        )
        val rolling = StatisticsEngine.rollingDistance(runs, 7, now)
        val latest = rolling.first()
        assertEquals(5000.0, latest.second, 0.01)
    }

    @Test
    fun `bestTimeForDistance finds fastest window over synthetic route`() {
        // 5 km at exactly 4 m/s: 1 km takes 250 s
        val points = (0..1250).map { i ->
            val dist = i * 4.0
            GpsPoint(dist / 111_195.0, 0.0, 0.0, i * 1000L)
        }
        val pr = StatisticsEngine.bestTimeForDistance(points, 1000.0, runId = 42)
        assertEquals(42L, pr!!.runId)
        assertEquals(250.0, pr!!.bestSeconds.toDouble(), 2.0)
        assertEquals(250.0, pr.paceSecondsPerKm, 2.0)
    }

    @Test
    fun `bestTimeForDistance returns null for too-short route`() {
        val points = listOf(GpsPoint(0.0, 0.0, 0.0, 0), GpsPoint(0.0001, 0.0, 0.0, 1000))
        assertNull(StatisticsEngine.bestTimeForDistance(points, 1000.0, 1))
    }

    @Test
    fun `faster recent pace wins as fastest run`() {
        val slow = run(1, now - day, 5000.0, 3000)   // 360 s/km
        val fast = run(2, now, 5000.0, 1500)         // 180 s/km
        val t = StatisticsEngine.totals(listOf(slow, fast), now)
        assertEquals(300.0, t.fastestRunPaceSecondsPerKm!!, 0.01)
        assertTrue(t.averagePaceSecondsPerKm > 300.0)
    }
}
