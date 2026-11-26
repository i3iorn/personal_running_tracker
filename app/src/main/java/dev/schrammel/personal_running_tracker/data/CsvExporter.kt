package dev.schrammel.personal_running_tracker.data

import dev.schrammel.personal_running_tracker.model.RunRecord
import dev.schrammel.personal_running_tracker.settings.TrackingPreferences

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

    fun exportDiagnostics(diagnostics: List<RunDiagnostic>): String {
        val header = listOf(
            "run_id",
            "battery_start_percent",
            "battery_end_percent",
            "battery_consumed_percent",
            "battery_duration_ms",
            "gps_fix_count",
            "sensor_fix_count",
            "fused_vs_gps_delta_meters",
            "max_accuracy_meters",
            "accuracy_rating",
        ).joinToString(",")
        val rows = diagnostics.joinToString("\n") { d ->
            listOf(
                d.runId.toString(),
                d.batteryStartPercent?.toString() ?: "",
                d.batteryEndPercent?.toString() ?: "",
                d.batteryConsumedPercent?.toString() ?: "",
                d.batteryDurationMs.toString(),
                d.gpsFixCount.toString(),
                d.sensorFixCount.toString(),
                d.fusedVsGpsDeltaMeters.toString(),
                d.maxAccuracyMeters?.toString() ?: "",
                d.accuracyRating?.toString() ?: "",
            ).joinToString(",")
        }
        return listOf(header, rows).filter { it.isNotEmpty() }.joinToString("\n")
    }

    fun exportTuning(prefs: TrackingPreferences): String {
        val header = "key,value"
        val rows = mapOf(
            "distance_unit" to prefs.distanceUnit,
            "pace_unit" to prefs.paceUnit,
            "tracking_profile" to prefs.trackingProfile,
            "auto_pause_enabled" to prefs.autoPauseEnabled.toString(),
            "auto_pause_sensitivity" to prefs.autoPauseSensitivity.toString(),
            "screen_on_enabled" to prefs.screenOnEnabled.toString(),
            "algorithm_version" to prefs.algorithmVersion,
            "tuning_gps_min_interval_ms" to prefs.tuningGpsMinIntervalMs.toString(),
            "tuning_gps_max_interval_ms" to prefs.tuningGpsMaxIntervalMs.toString(),
            "tuning_gps_min_distance_meters" to prefs.tuningGpsMinDistanceMeters.toString(),
            "tuning_gps_accuracy_threshold_meters" to prefs.tuningGpsAccuracyThresholdMeters.toString(),
            "tuning_stride_length_meters" to prefs.tuningStrideLengthMeters.toString(),
            "tuning_sensor_fusion_max_gps_segment_ratio" to prefs.tuningSensorFusionMaxGpsSegmentRatio.toString(),
            "tuning_stable_uncertainty_reevaluate_meters" to prefs.tuningStableUncertaintyReevaluateMeters.toString(),
            "battery_benchmark_enabled" to prefs.batteryBenchmarkEnabled.toString(),
        ).map { (k, v) -> "$k,$v" }
        return (listOf(header) + rows).joinToString("\n")
    }

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