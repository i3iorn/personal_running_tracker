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
}
