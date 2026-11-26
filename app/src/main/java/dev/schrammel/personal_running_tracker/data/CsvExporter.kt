package dev.schrammel.personal_running_tracker.data

import dev.schrammel.personal_running_tracker.model.RunRecord

class CsvExporter {

    fun exportRuns(runs: List<RunRecord>): String {
        val lines = mutableListOf<String>()
        lines.add(headerRow())
        runs.forEach { run ->
            lines.add(runRow(run))
        }
        return lines.joinToString("\n")
    }

    private fun headerRow(): String =
        listOf(
            "id",
            "start_timestamp",
            "end_timestamp",
            "elapsed_duration_seconds",
            "moving_duration_seconds",
            "paused_duration_seconds",
            "distance_meters",
            "original_distance_meters",
            "average_pace_seconds_per_meter",
            "average_speed_meters_per_second",
            "elevation_gain_meters",
            "elevation_loss_meters",
            "step_count",
            "tracking_profile",
            "tracking_algorithm_version",
            "user_adjusted",
            "notes",
        ).joinToString(",")

    private fun runRow(run: RunRecord): String =
        listOf(
            run.id.toString(),
            run.startTimestamp.toString(),
            run.endTimestamp.toString(),
            run.elapsedDurationSeconds.toString(),
            run.movingDurationSeconds.toString(),
            run.pausedDurationSeconds.toString(),
            formatDistance(run.distanceMeters),
            formatDistance(run.originalDistanceMeters),
            formatPace(run.averagePaceSecondsPerMeter),
            formatSpeed(run.averageSpeedMetersPerSecond),
            formatDistance(run.elevationGainMeters),
            formatDistance(run.elevationLossMeters),
            run.stepCount.toString(),
            run.trackingProfile,
            run.trackingAlgorithmVersion,
            run.userAdjusted.toString(),
            quoteIfNeeded(run.notes),
        ).joinToString(",")

    private fun formatDistance(value: Double): String = value.toString()

    private fun formatPace(value: Double): String = value.toString()

    private fun formatSpeed(value: Double): String = value.toString()

    private fun quoteIfNeeded(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }
}