package dev.schrammel.personal_running_tracker.stats

import dev.schrammel.personal_running_tracker.model.RunRecord
import dev.schrammel.personal_running_tracker.model.TimeRange

object StatisticsEngine {
    data class Summary(
        val totalDistanceMeters: Double,
        val totalDurationSeconds: Long,
        val totalMovingSeconds: Long,
        val runCount: Int,
        val averageDistanceMeters: Double,
        val averageDurationSeconds: Double,
        val averagePaceSecondsPerMeter: Double,
        val averageSpeedMps: Double,
        val longestRunMeters: Double,
    )

    fun summarize(runs: List<RunRecord>): Summary {
        if (runs.isEmpty()) {
            return Summary(0.0, 0L, 0L, 0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
        val totalDistance = runs.sumOf { it.distanceMeters }
        val totalDuration = runs.sumOf { it.elapsedDurationSeconds }
        val totalMoving = runs.sumOf { it.movingDurationSeconds }
        val count = runs.size
        val avgPace = if (totalDistance > 0.0) totalDuration.toDouble() / totalDistance else 0.0
        val avgSpeed = if (totalDuration > 0) totalDistance.toDouble() / totalDuration else 0.0
        val longest = runs.maxOf { it.distanceMeters }
        return Summary(
            totalDistance, totalDuration, totalMoving, count,
            totalDistance / count, totalDuration.toDouble() / count,
            avgPace, avgSpeed, longest,
        )
    }

    fun runsInRange(
        runs: List<RunRecord>,
        range: TimeRange,
        now: Long = System.currentTimeMillis(),
        customStartMs: Long = 0L,
        customEndMs: Long = Long.MAX_VALUE,
    ): List<RunRecord> {
        return when (range) {
            TimeRange.ALL_TIME -> runs
            TimeRange.LAST_7_DAYS -> runs.filter { it.startTimestamp >= now - 7L * 24L * 3600L * 1000L }
            TimeRange.LAST_30_DAYS -> runs.filter { it.startTimestamp >= now - 30L * 24L * 3600L * 1000L }
            TimeRange.LAST_90_DAYS -> runs.filter { it.startTimestamp >= now - 90L * 24L * 3600L * 1000L }
            TimeRange.LAST_365_DAYS -> runs.filter { it.startTimestamp >= now - 365L * 24L * 3600L * 1000L }
            TimeRange.CURRENT_WEEK -> {
                val c = java.util.Calendar.getInstance()
                c.timeInMillis = now
                val dayOfWeek = c.get(java.util.Calendar.DAY_OF_WEEK) - 1 // Sunday=0
                val startOfWeek = now - dayOfWeek.toLong() * 24L * 3600L * 1000L
                runs.filter { it.startTimestamp >= startOfWeek }
            }
            TimeRange.CURRENT_MONTH -> {
                val c = java.util.Calendar.getInstance()
                c.timeInMillis = now
                c.set(java.util.Calendar.DAY_OF_MONTH, 1)
                c.set(java.util.Calendar.HOUR_OF_DAY, 0)
                c.set(java.util.Calendar.MINUTE, 0)
                c.set(java.util.Calendar.SECOND, 0)
                c.set(java.util.Calendar.MILLISECOND, 0)
                val startOfMonth = c.timeInMillis
                runs.filter { it.startTimestamp >= startOfMonth }
            }
            TimeRange.CURRENT_YEAR -> {
                val c = java.util.Calendar.getInstance()
                c.timeInMillis = now
                c.set(java.util.Calendar.DAY_OF_YEAR, 1)
                c.set(java.util.Calendar.HOUR_OF_DAY, 0)
                c.set(java.util.Calendar.MINUTE, 0)
                c.set(java.util.Calendar.SECOND, 0)
                c.set(java.util.Calendar.MILLISECOND, 0)
                val startOfYear = c.timeInMillis
                runs.filter { it.startTimestamp >= startOfYear }
            }
            TimeRange.PREVIOUS_YEAR -> {
                val c = java.util.Calendar.getInstance()
                c.timeInMillis = now
                val thisYear = c.get(java.util.Calendar.YEAR)
                c.set(java.util.Calendar.YEAR, thisYear - 1)
                c.set(java.util.Calendar.DAY_OF_YEAR, 1)
                c.set(java.util.Calendar.HOUR_OF_DAY, 0)
                c.set(java.util.Calendar.MINUTE, 0)
                c.set(java.util.Calendar.SECOND, 0)
                c.set(java.util.Calendar.MILLISECOND, 0)
                val startOfPrevYear = c.timeInMillis
                val c2 = java.util.Calendar.getInstance()
                c2.timeInMillis = now
                c2.set(java.util.Calendar.YEAR, thisYear)
                c2.set(java.util.Calendar.DAY_OF_YEAR, 1)
                c2.set(java.util.Calendar.HOUR_OF_DAY, 0)
                c2.set(java.util.Calendar.MINUTE, 0)
                c2.set(java.util.Calendar.SECOND, 0)
                c2.set(java.util.Calendar.MILLISECOND, 0)
                val startOfThisYear = c2.timeInMillis
                runs.filter { it.startTimestamp >= startOfPrevYear && it.startTimestamp < startOfThisYear }
            }
            TimeRange.CUSTOM -> runs.filter { it.startTimestamp >= customStartMs && it.startTimestamp <= customEndMs }
        }
    }
}
