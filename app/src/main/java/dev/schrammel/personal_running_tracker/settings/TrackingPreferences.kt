package dev.schrammel.personal_running_tracker.settings

import android.content.Context

class TrackingPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "personal_running_tracker_prefs", Context.MODE_PRIVATE,
    )

    var distanceUnit: String
        get() = prefs.getString("distance_unit", "KILOMETERS") ?: "KILOMETERS"
        set(value) { prefs.edit().putString("distance_unit", value).apply() }

    var paceUnit: String
        get() = prefs.getString("pace_unit", "MIN_PER_KM") ?: "MIN_PER_KM"
        set(value) { prefs.edit().putString("pace_unit", value).apply() }

    var trackingProfile: String
        get() {
            // Normalize legacy uppercase defaults to the canonical values used by the engine
            val raw = prefs.getString("tracking_profile", "Balanced") ?: "Balanced"
            return when (raw.uppercase()) {
                "SAVER" -> "Saver"
                "HIGH ACCURACY" -> "High Accuracy"
                else -> "Balanced"
            }
        }
        set(value) { prefs.edit().putString("tracking_profile", value).apply() }

    var autoPauseEnabled: Boolean
        get() = prefs.getBoolean("auto_pause_enabled", true)
        set(value) { prefs.edit().putBoolean("auto_pause_enabled", value).apply() }

    var autoPauseSensitivity: Int
        get() = prefs.getInt("auto_pause_sensitivity", 3)
        set(value) { prefs.edit().putInt("auto_pause_sensitivity", value).apply() }

    var screenOnEnabled: Boolean
        get() = prefs.getBoolean("screen_on_enabled", false)
        set(value) { prefs.edit().putBoolean("screen_on_enabled", value).apply() }

    var algorithmVersion: String
        get() = prefs.getString("tracking_algorithm_version", "1.0.0") ?: "1.0.0"
        set(value) { prefs.edit().putString("tracking_algorithm_version", value).apply() }

    // ----- Tuning knobs (tracking algorithm tuning feature, Phase 6 item 3) -----
    // All defaults mirror TrackingTuning.forProfile(profile); settings override them.
    var tuningGpsMinIntervalMs: Long
        get() = prefs.getLong("tuning_gps_min_interval_ms", TrackingTuning.forProfile(trackingProfile).gpsMinIntervalMs)
        set(value) { prefs.edit().putLong("tuning_gps_min_interval_ms", value).apply() }

    var tuningGpsMaxIntervalMs: Long
        get() = prefs.getLong("tuning_gps_max_interval_ms", TrackingTuning.forProfile(trackingProfile).gpsMaxIntervalMs)
        set(value) { prefs.edit().putLong("tuning_gps_max_interval_ms", value).apply() }

    var tuningGpsMinDistanceMeters: Float
        get() = prefs.getFloat("tuning_gps_min_distance_meters", TrackingTuning.forProfile(trackingProfile).gpsMinDistanceMeters)
        set(value) { prefs.edit().putFloat("tuning_gps_min_distance_meters", value).apply() }

    var tuningGpsAccuracyThresholdMeters: Float
        get() = prefs.getFloat("tuning_gps_accuracy_threshold_meters", TrackingTuning.forProfile(trackingProfile).gpsAccuracyThresholdMeters)
        set(value) { prefs.edit().putFloat("tuning_gps_accuracy_threshold_meters", value).apply() }

    var tuningStrideLengthMeters: Double
        get() = prefs.getFloat("tuning_stride_length_meters", TrackingTuning.forProfile(trackingProfile).strideLengthMeters.toFloat()).toDouble()
        set(value) { prefs.edit().putFloat("tuning_stride_length_meters", value.toFloat()).apply() }

    var tuningSensorFusionMaxGpsSegmentRatio: Double
        get() = prefs.getFloat("tuning_sensor_fusion_max_gps_segment_ratio", TrackingTuning.forProfile(trackingProfile).sensorFusionMaxGpsSegmentRatio.toFloat()).toDouble()
        set(value) { prefs.edit().putFloat("tuning_sensor_fusion_max_gps_segment_ratio", value.toFloat()).apply() }

    var tuningStableUncertaintyReevaluateMeters: Double
        get() = prefs.getFloat("tuning_stable_uncertainty_reevaluate_meters", TrackingTuning.forProfile(trackingProfile).stableUncertaintyReevaluateMeters.toFloat()).toDouble()
        set(value) { prefs.edit().putFloat("tuning_stable_uncertainty_reevaluate_meters", value.toFloat()).apply() }

    // ----- Battery benchmarking (Phase 6 item 1) -----
    var batteryBenchmarkEnabled: Boolean
        get() = prefs.getBoolean("battery_benchmark_enabled", false)
        set(value) { prefs.edit().putBoolean("battery_benchmark_enabled", value).apply() }

    // Crash-recovery companion for the run_state row (moving-time of the live segment)
    var runStateMovingSegmentMs: Long
        get() = prefs.getLong("run_state_moving_segment_ms", 0L)
        set(value) { prefs.edit().putLong("run_state_moving_segment_ms", value).apply() }

    var runStateRunMs: Long
        get() = prefs.getLong("run_state_run_ms", 0L)
        set(value) { prefs.edit().putLong("run_state_run_ms", value).apply() }

    var runStateWalkMs: Long
        get() = prefs.getLong("run_state_walk_ms", 0L)
        set(value) { prefs.edit().putLong("run_state_walk_ms", value).apply() }

    // ----- New settings from the security/performance pass -----
    // GPX import size cap in MB (DoS guard)
    var importMaxMb: Int
        get() = prefs.getInt("import_max_mb", 10).coerceIn(1, 100)
        set(value) { prefs.edit().putInt("import_max_mb", value).apply() }

    // Statistics charts: rolling window length and trend weeks
    var rollingWindowDays: Int
        get() = prefs.getInt("rolling_window_days", 30).coerceIn(1, 365)
        set(value) { prefs.edit().putInt("rolling_window_days", value).apply() }

    var chartWeeks: Int
        get() = prefs.getInt("chart_weeks", 8).coerceIn(1, 52)
        set(value) { prefs.edit().putInt("chart_weeks", value).apply() }

    /** Ultra mode: minimal display while running (big elapsed + distance only). */
    var ultraModeEnabled: Boolean
        get() = prefs.getBoolean("ultra_mode_enabled", false)
        set(value) { prefs.edit().putBoolean("ultra_mode_enabled", value).apply() }

    /** Auto-switch to Battery Saver at this level while a run is active. */
    var lowBatteryThresholdPercent: Int
        get() = prefs.getInt("low_battery_threshold_percent", 15).coerceIn(5, 50)
        set(value) { prefs.edit().putInt("low_battery_threshold_percent", value).apply() }

    /** Speed above which a fix is classified as running rather than walking (m/s). */
    var runningSpeedThresholdMps: Float
        get() = prefs.getFloat("running_speed_threshold_mps", 2.2f).coerceIn(0.5f, 6f)
        set(value) { prefs.edit().putFloat("running_speed_threshold_mps", value).apply() }

    /** Top speed (km/h) above which a run is auto-paused — a human cannot run faster. */
    var runSpeedPauseThresholdKmh: Float
        get() = prefs.getFloat("run_speed_pause_threshold_kmh", 30f).coerceIn(15f, 40f)
        set(value) { prefs.edit().putFloat("run_speed_pause_threshold_kmh", value).apply() }

    // ----- Validated getters: every expert setting is clamped at this single
    // choke point so a bad value in Settings cannot break the engine. -----
    var saverGpsMinIntervalMs: Long
        get() = prefs.getLong("saver_gps_min_interval_ms", 60_000L).coerceIn(1_000L, 600_000L)
        set(value) { prefs.edit().putLong("saver_gps_min_interval_ms", value).apply() }

    var saverGpsMinDistanceMeters: Float
        get() = prefs.getFloat("saver_gps_min_distance_meters", 100f).coerceIn(1f, 10_000f)
        set(value) { prefs.edit().putFloat("saver_gps_min_distance_meters", value).apply() }

    var highAccuracyGpsMinIntervalMs: Long
        get() = prefs.getLong("high_accuracy_gps_min_interval_ms", 5_000L).coerceIn(1_000L, 600_000L)
        set(value) { prefs.edit().putLong("high_accuracy_gps_min_interval_ms", value).apply() }

    var highAccuracyGpsMinDistanceMeters: Float
        get() = prefs.getFloat("high_accuracy_gps_min_distance_meters", 10f).coerceIn(1f, 10_000f)
        set(value) { prefs.edit().putFloat("high_accuracy_gps_min_distance_meters", value).apply() }

    var paceSmoothingWindowMs: Long
        get() = prefs.getLong("pace_smoothing_window_ms", 10_000L).coerceIn(1_000L, 120_000L)
        set(value) { prefs.edit().putLong("pace_smoothing_window_ms", value).apply() }

    var paceMinSpeedMps: Float
        get() = prefs.getFloat("pace_min_speed_mps", 0.3f).coerceIn(0f, 10f)
        set(value) { prefs.edit().putFloat("pace_min_speed_mps", value).apply() }

    var paceMaxAccuracyMeters: Float
        get() = prefs.getFloat("pace_max_accuracy_meters", 20f).coerceIn(1f, 1_000f)
        set(value) { prefs.edit().putFloat("pace_max_accuracy_meters", value).apply() }

    var splitLengthMeters: Double
        get() = prefs.getFloat("split_length_meters", 1000f).coerceIn(100f, 10_000f).toDouble()
        set(value) { prefs.edit().putFloat("split_length_meters", value.toFloat()).apply() }

    var degradedGpsThresholdMeters: Float
        get() = prefs.getFloat("degraded_gps_threshold_meters", 25f).coerceIn(1f, 500f)
        set(value) { prefs.edit().putFloat("degraded_gps_threshold_meters", value).apply() }

    var ratingExcellentMeters: Float
        get() = prefs.getFloat("rating_excellent_meters", 5f).coerceIn(1f, 500f)
        set(value) { prefs.edit().putFloat("rating_excellent_meters", value).apply() }

    var ratingGoodMeters: Float
        get() = prefs.getFloat("rating_good_meters", 10f).coerceIn(1f, 500f)
        set(value) { prefs.edit().putFloat("rating_good_meters", value).apply() }

    var ratingFairMeters: Float
        get() = prefs.getFloat("rating_fair_meters", 20f).coerceIn(1f, 500f)
        set(value) { prefs.edit().putFloat("rating_fair_meters", value).apply() }

    var ratingPoorMeters: Float
        get() = prefs.getFloat("rating_poor_meters", 50f).coerceIn(1f, 500f)
        set(value) { prefs.edit().putFloat("rating_poor_meters", value).apply() }

    var statePersistIntervalMs: Long
        get() = prefs.getLong("state_persist_interval_ms", 5_000L).coerceIn(1_000L, 600_000L)
        set(value) { prefs.edit().putLong("state_persist_interval_ms", value).apply() }

    var prDistanceKmList: String
        get() {
            val raw = prefs.getString("pr_distance_km_list", "1,3,5,10") ?: "1,3,5,10"
            val valid = raw.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                .filter { it > 0 && it <= 500 }.joinToString(",")
            return valid.ifEmpty { "1,3,5,10" }
        }
        set(value) { prefs.edit().putString("pr_distance_km_list", value).apply() }

    var mapReliableMeters: Float
        get() = prefs.getFloat("map_reliable_meters", 10f).coerceIn(1f, 500f)
        set(value) { prefs.edit().putFloat("map_reliable_meters", value).apply() }

    var mapAssistedMeters: Float
        get() = prefs.getFloat("map_assisted_meters", 25f).coerceIn(1f, 500f)
        set(value) { prefs.edit().putFloat("map_assisted_meters", value).apply() }
}
