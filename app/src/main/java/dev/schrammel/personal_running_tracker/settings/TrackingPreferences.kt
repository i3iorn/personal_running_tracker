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
        get() = prefs.getString("tracking_profile", "BALANCED") ?: "BALANCED"
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
}
