package dev.schrammel.personal_running_tracker.stats

import dev.schrammel.personal_running_tracker.model.RunRecord

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
}
