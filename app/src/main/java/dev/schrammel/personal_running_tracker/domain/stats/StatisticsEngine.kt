package dev.schrammel.personal_running_tracker.domain.stats

import dev.schrammel.personal_running_tracker.model.TimeRange
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.max

/** Aggregated statistics over a selection of runs, per spec §8. */
data class Totals(
    val runCount: Int,
    val totalDistanceMeters: Double,
    val totalElapsedSeconds: Long,
    val totalMovingSeconds: Long,
    val averageDistanceMeters: Double,
    val averageDurationSeconds: Long,
    val averagePaceSecondsPerKm: Double,   // distance-weighted
    val averageSpeedMetersPerSecond: Double,
    val longestRunMeters: Double,
    val fastestRunPaceSecondsPerKm: Double?, // lowest pace; null when no runs
    val averageWeeklyDistanceMeters: Double,
    val averageMonthlyDistanceMeters: Double,
)

data class Comparison(
    val absoluteDeltaDistanceMeters: Double,
    val percentDeltaDistance: Double?,      // null when base is zero
    val absoluteDeltaRuns: Int,
    val percentDeltaRuns: Double?,
    val absoluteDeltaSeconds: Long,
    val percentDeltaSeconds: Double?,
    val percentDeltaPace: Double?,          // negative = faster; null when base pace zero
    val percentDeltaSpeed: Double?,
)

data class PersonalRecord(
    val distanceMeters: Double,
    val bestSeconds: Long,
    val paceSecondsPerKm: Double,
    val runId: Long,
)

object StatisticsEngine {

    /** Inclusive start / exclusive end timestamps (ms) for a named range, or null for all time. */
    fun rangeBounds(range: TimeRange, nowMs: Long, customStartMs: Long = 0, customEndMs: Long = Long.MAX_VALUE): Pair<Long, Long>? {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nowMs }
        return when (range) {
            TimeRange.ALL_TIME -> null
            TimeRange.CUSTOM -> customStartMs to customEndMs
            TimeRange.LAST_7_DAYS -> nowMs - 7L * DAY_MS to nowMs
            TimeRange.LAST_30_DAYS -> nowMs - 30L * DAY_MS to nowMs
            TimeRange.LAST_90_DAYS -> nowMs - 90L * DAY_MS to nowMs
            TimeRange.LAST_365_DAYS -> nowMs - 365L * DAY_MS to nowMs
            TimeRange.CURRENT_WEEK -> {
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.timeInMillis to nowMs
            }
            TimeRange.CURRENT_MONTH -> {
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.timeInMillis to nowMs
            }
            TimeRange.CURRENT_YEAR -> {
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.timeInMillis to nowMs
            }
            TimeRange.PREVIOUS_YEAR -> {
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.set(Calendar.DAY_OF_YEAR, 1)
                val start = cal.timeInMillis
                cal.add(Calendar.YEAR, -1)
                cal.timeInMillis to start
            }
        }
    }

    fun filterRuns(
        runs: List<dev.schrammel.personal_running_tracker.model.RunRecord>,
        range: TimeRange,
        nowMs: Long,
        customStartMs: Long = 0,
        customEndMs: Long = Long.MAX_VALUE,
    ): List<dev.schrammel.personal_running_tracker.model.RunRecord> {
        val bounds = rangeBounds(range, nowMs, customStartMs, customEndMs) ?: return runs
        return runs.filter { it.startTimestamp in bounds.first until bounds.second }
    }

    fun totals(runs: List<dev.schrammel.personal_running_tracker.model.RunRecord>, nowMs: Long): Totals {
        if (runs.isEmpty()) return Totals(
            runCount = 0, totalDistanceMeters = 0.0, totalElapsedSeconds = 0, totalMovingSeconds = 0,
            averageDistanceMeters = 0.0, averageDurationSeconds = 0, averagePaceSecondsPerKm = 0.0,
            averageSpeedMetersPerSecond = 0.0, longestRunMeters = 0.0, fastestRunPaceSecondsPerKm = null,
            averageWeeklyDistanceMeters = 0.0, averageMonthlyDistanceMeters = 0.0,
        )
        val totalDistance = runs.sumOf { it.distanceMeters }
        val totalSeconds = runs.sumOf { it.elapsedDurationSeconds }
        val movingSeconds = runs.sumOf { it.movingDurationSeconds }
        // Distance-weighted average pace: total time / total distance
        val avgPacePerKm = if (totalDistance > 0.0) totalSeconds / totalDistance * 1000.0 else 0.0
        val fastest = runs.filter { it.distanceMeters > 0.0 }
            .minByOrNull { it.averagePaceSecondsPerMeter }
            ?.let { it.averagePaceSecondsPerMeter * 1000.0 }
        val spanMs = max(nowMs - runs.minOf { it.startTimestamp }, DAY_MS)
        return Totals(
            runCount = runs.size,
            totalDistanceMeters = totalDistance,
            totalElapsedSeconds = totalSeconds,
            totalMovingSeconds = movingSeconds,
            averageDistanceMeters = totalDistance / runs.size,
            averageDurationSeconds = totalSeconds / runs.size,
            averagePaceSecondsPerKm = avgPacePerKm,
            averageSpeedMetersPerSecond = if (totalSeconds > 0) totalDistance / totalSeconds else 0.0,
            longestRunMeters = runs.maxOf { it.distanceMeters },
            fastestRunPaceSecondsPerKm = fastest,
            averageWeeklyDistanceMeters = totalDistance / (spanMs / WEEK_MS.toDouble()),
            averageMonthlyDistanceMeters = totalDistance / (spanMs / (30.44 * DAY_MS)),
        )
    }

    /** Absolute + percentage deltas; percentage null when the base value is zero. */
    fun compare(
        current: Totals,
        previous: Totals,
    ): Comparison = Comparison(
        absoluteDeltaDistanceMeters = current.totalDistanceMeters - previous.totalDistanceMeters,
        percentDeltaDistance = percent(current.totalDistanceMeters, previous.totalDistanceMeters),
        absoluteDeltaRuns = current.runCount - previous.runCount,
        percentDeltaRuns = percent(current.runCount.toDouble(), previous.runCount.toDouble()),
        absoluteDeltaSeconds = current.totalElapsedSeconds - previous.totalElapsedSeconds,
        percentDeltaSeconds = percent(current.totalElapsedSeconds.toDouble(), previous.totalElapsedSeconds.toDouble()),
        percentDeltaPace = if (current.averagePaceSecondsPerKm > 0 && previous.averagePaceSecondsPerKm > 0) {
            (current.averagePaceSecondsPerKm / previous.averagePaceSecondsPerKm - 1) * 100
        } else null,
        percentDeltaSpeed = if (current.averageSpeedMetersPerSecond > 0 && previous.averageSpeedMetersPerSecond > 0) {
            (current.averageSpeedMetersPerSecond / previous.averageSpeedMetersPerSecond - 1) * 100
        } else null,
    )

    /** Rolling distance over the `windowDays` preceding each day boundary. */
    fun rollingDistance(
        runs: List<dev.schrammel.personal_running_tracker.model.RunRecord>,
        windowDays: Int,
        nowMs: Long,
    ): List<Pair<Long, Double>> {
        require(windowDays > 0) { "windowDays must be positive" }
        val dayMs = DAY_MS
        val result = mutableListOf<Pair<Long, Double>>()
        for (dayEnd in (nowMs downTo nowMs - windowDays * dayMs * 3L) step dayMs) {
            val windowStart = dayEnd - windowDays * dayMs
            val d = runs.filter { it.startTimestamp in windowStart until dayEnd }
                .sumOf { it.distanceMeters }
            result.add(dayEnd to d)
        }
        return result
    }

    /**
     * Personal records over arbitrary distances, computed from raw GPS points
     * (sliding time window over the route), per spec §8.3.
     */
    fun bestTimeForDistance(
        points: List<dev.schrammel.personal_running_tracker.domain.split.GpsPoint>,
        targetMeters: Double,
        runId: Long,
    ): PersonalRecord? {
        if (points.size < 2 || targetMeters <= 0.0) return null
        val ordered = points.sortedBy { it.timestampMs }
        var head = 0
        var distance = 0.0
        var best: PersonalRecord? = null
        for (tail in 1 until ordered.size) {
            distance += metersBetween(ordered[tail - 1], ordered[tail])
            // Record the window [head..tail] BEFORE trimming it past the target,
            // otherwise the head can jump over the boundary without ever qualifying.
            if (distance >= targetMeters - 0.01) {
                val seconds = (ordered[tail].timestampMs - ordered[head].timestampMs) / 1000L
                if (seconds > 0 && (best == null || seconds < best.bestSeconds)) {
                    best = PersonalRecord(
                        distanceMeters = targetMeters,
                        bestSeconds = seconds,
                        paceSecondsPerKm = seconds / (targetMeters / 1000.0),
                        runId = runId,
                    )
                }
            }
            while (distance > targetMeters + 0.5 && head < tail) {
                distance -= metersBetween(ordered[head], ordered[head + 1])
                head++
            }
        }
        return best
    }

    /** Weekly buckets (ISO Monday start) for trend charts: distance and distance-weighted avg pace. */
    fun weeklyTrends(
        runs: List<dev.schrammel.personal_running_tracker.model.RunRecord>,
        weeks: Int,
        nowMs: Long,
    ): List<Triple<Long, Double, Double>> {
        require(weeks > 0)
        val weekMs = 7L * 86_400_000L
        // Align week boundaries to Monday 00:00 UTC
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nowMs }
        val dayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0..Sun=6
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val currentWeekStart = cal.timeInMillis - dayOfWeek * 86_400_000L
        return (weeks - 1 downTo 0).map { weeksAgo ->
            val start = currentWeekStart - weeksAgo * weekMs
            val end = start + weekMs
            val inWeek = runs.filter { it.startTimestamp in start until end }
            val distance = inWeek.sumOf { it.distanceMeters }
            val totalSeconds = inWeek.sumOf { it.elapsedDurationSeconds }
            val avgPacePerKm = if (distance > 0) totalSeconds / distance * 1000.0 else 0.0
            Triple(start, distance, avgPacePerKm)
        }
    }

    /** Histogram of run lengths over fixed km buckets, for distance-distribution charts. */
    fun distanceDistribution(
        runs: List<dev.schrammel.personal_running_tracker.model.RunRecord>,
        bucketEdgesKm: List<Double> = listOf(3.0, 5.0, 10.0, 21.1),
    ): List<Pair<String, Int>> {
        val labels = mutableListOf<String>()
        var lower = 0.0
        for (edge in bucketEdgesKm) {
            labels.add("%.0f-%.0f km".format(lower, edge))
            lower = edge
        }
        labels.add("> %.0f km".format(lower))
        val counts = MutableList(labels.size) { 0 }
        for (run in runs) {
            val km = run.distanceMeters / 1000.0
            val idx = bucketEdgesKm.indexOfFirst { km < it }.let { if (it == -1) labels.size - 1 else it }
            counts[idx]++
        }
        return labels.zip(counts)
    }

    private fun percent(current: Double, base: Double): Double? =
        if (base != 0.0) (current / base - 1) * 100 else null

    private fun metersBetween(a: dev.schrammel.personal_running_tracker.domain.split.GpsPoint,
                              b: dev.schrammel.personal_running_tracker.domain.split.GpsPoint): Double {
        val r = 6378137.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(a.latitude)) * Math.cos(Math.toRadians(b.latitude)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return (2 * r * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))).coerceAtLeast(0.0)
    }

    private const val DAY_MS = 86_400_000L
    private const val WEEK_MS = 7L * 86_400_000L
}
