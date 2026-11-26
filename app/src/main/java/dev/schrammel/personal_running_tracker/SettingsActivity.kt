package dev.schrammel.personal_running_tracker

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import dev.schrammel.personal_running_tracker.settings.TrackingPreferences

/**
 * Comprehensive settings page: every value that used to be hardcoded in code is
 * editable here. Deliberately exposes expert knobs that can degrade tracking
 * accuracy or battery life if set badly (per the user's request).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: TrackingPreferences
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = TrackingPreferences(this)
        container = findViewById(R.id.settingsContainer)
        buildAll()
    }

    private fun buildAll() {
        section("Units & profile")
        choice("Tracking profile", listOf("Saver", "Balanced", "High Accuracy"),
            { prefs.trackingProfile }, { prefs.trackingProfile = it })
        choice("Distance unit", listOf("KILOMETERS", "MILES"),
            { prefs.distanceUnit }, { prefs.distanceUnit = it })
        choice("Pace unit", listOf("MIN_PER_KM", "MIN_PER_MILE"),
            { prefs.paceUnit }, { prefs.paceUnit = it })

        section("Behaviour")
        switch("Automatic pause", { prefs.autoPauseEnabled }, { prefs.autoPauseEnabled = it })
        seek("Auto-pause sensitivity (0-10)", 0, 10,
            { prefs.autoPauseSensitivity }, { prefs.autoPauseSensitivity = it })
        switch("Keep screen on during run", { prefs.screenOnEnabled }, { prefs.screenOnEnabled = it })

        section("GPS scheduling (expert — bad values kill battery or accuracy)")
        number("Battery Saver: GPS min interval (ms)", { prefs.saverGpsMinIntervalMs.toDouble() },
            { prefs.saverGpsMinIntervalMs = it.toLong() })
        number("Battery Saver: GPS min distance (m)", { prefs.saverGpsMinDistanceMeters.toDouble() },
            { prefs.saverGpsMinDistanceMeters = it.toFloat() })
        number("High Accuracy: GPS min interval (ms)", { prefs.highAccuracyGpsMinIntervalMs.toDouble() },
            { prefs.highAccuracyGpsMinIntervalMs = it.toLong() })
        number("High Accuracy: GPS min distance (m)", { prefs.highAccuracyGpsMinDistanceMeters.toDouble() },
            { prefs.highAccuracyGpsMinDistanceMeters = it.toFloat() })

        section("Algorithm tuning (overrides profile defaults)")
        number("Balanced: GPS min interval (ms)", { prefs.tuningGpsMinIntervalMs.toDouble() },
            { prefs.tuningGpsMinIntervalMs = it.toLong() })
        number("Balanced: GPS max interval (ms)", { prefs.tuningGpsMaxIntervalMs.toDouble() },
            { prefs.tuningGpsMaxIntervalMs = it.toLong() })
        number("Balanced: GPS min distance (m)", { prefs.tuningGpsMinDistanceMeters.toDouble() },
            { prefs.tuningGpsMinDistanceMeters = it.toFloat() })
        number("GPS accuracy threshold (m)", { prefs.tuningGpsAccuracyThresholdMeters.toDouble() },
            { prefs.tuningGpsAccuracyThresholdMeters = it.toFloat() })
        number("Stride length (m)", { prefs.tuningStrideLengthMeters },
            { prefs.tuningStrideLengthMeters = it })
        number("Sensor fusion max GPS segment ratio", { prefs.tuningSensorFusionMaxGpsSegmentRatio },
            { prefs.tuningSensorFusionMaxGpsSegmentRatio = it })
        number("Stable-uncertainty re-evaluate (m)", { prefs.tuningStableUncertaintyReevaluateMeters },
            { prefs.tuningStableUncertaintyReevaluateMeters = it })

        section("Live pace display")
        number("Pace smoothing window (ms)", { prefs.paceSmoothingWindowMs.toDouble() },
            { prefs.paceSmoothingWindowMs = it.toLong() })
        number("Pace: min speed m/s (below = stationary)", { prefs.paceMinSpeedMps.toDouble() },
            { prefs.paceMinSpeedMps = it.toFloat() })
        number("Pace: max GPS accuracy m (above = hide pace)", { prefs.paceMaxAccuracyMeters.toDouble() },
            { prefs.paceMaxAccuracyMeters = it.toFloat() })

        section("Splits & records")
        number("Split length (m)", { prefs.splitLengthMeters }, { prefs.splitLengthMeters = it })
        text("Personal record distances (km, comma separated)",
            { prefs.prDistanceKmList }, { prefs.prDistanceKmList = it })

        section("Import & charts")
        number("GPX import max size (MB)", { prefs.importMaxMb.toDouble() },
            { prefs.importMaxMb = it.toInt() })
        number("Rolling-distance window (days)", { prefs.rollingWindowDays.toDouble() },
            { prefs.rollingWindowDays = it.toInt() })
        number("Weekly trend chart weeks", { prefs.chartWeeks.toDouble() },
            { prefs.chartWeeks = it.toInt() })

        section("Diagnostics & reliability")
        switch("Battery benchmarking", { prefs.batteryBenchmarkEnabled },
            { prefs.batteryBenchmarkEnabled = it })
        number("Degraded-GPS notification threshold (m)", { prefs.degradedGpsThresholdMeters.toDouble() },
            { prefs.degradedGpsThresholdMeters = it.toFloat() })
        number("Rating: excellent (m)", { prefs.ratingExcellentMeters.toDouble() },
            { prefs.ratingExcellentMeters = it.toFloat() })
        number("Rating: good (m)", { prefs.ratingGoodMeters.toDouble() },
            { prefs.ratingGoodMeters = it.toFloat() })
        number("Rating: fair (m)", { prefs.ratingFairMeters.toDouble() },
            { prefs.ratingFairMeters = it.toFloat() })
        number("Rating: poor (m)", { prefs.ratingPoorMeters.toDouble() },
            { prefs.ratingPoorMeters = it.toFloat() })
        number("Crash-recovery persist interval (ms)", { prefs.statePersistIntervalMs.toDouble() },
            { prefs.statePersistIntervalMs = it.toLong() })

        section("Route map colouring")
        number("Reliable segment max error (m)", { prefs.mapReliableMeters.toDouble() },
            { prefs.mapReliableMeters = it.toFloat() })
        number("Assisted segment max error (m)", { prefs.mapAssistedMeters.toDouble() },
            { prefs.mapAssistedMeters = it.toFloat() })

        section("About")
        label("Personal Running Tracker v0.1.0\nOffline-first, sensor-assisted GPS tracking.\nAll data stays on this device.")
    }

    // ---- builder helpers ----

    private fun section(title: String) {
        container.addView(TextView(this).apply {
            text = title
            textSize = 12f
            setPadding(0, 24, 0, 4)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
    }

    private fun label(text: String) {
        container.addView(TextView(this).apply {
            this.text = text
            textSize = 12f
            setPadding(0, 8, 0, 0)
        })
    }

    private fun choice(labelText: String, options: List<String>, get: () -> String, set: (String) -> Unit) {
        container.addView(labelView(labelText))
        container.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity,
                android.R.layout.simple_spinner_dropdown_item, options)
            setSelection(options.indexOf(get()).coerceAtLeast(0))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                    set(options[pos])
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
        })
    }

    private fun switch(labelText: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        container.addView(SwitchMaterial(this).apply {
            text = labelText
            isChecked = get()
            setOnCheckedChangeListener { _, checked -> set(checked) }
        })
    }

    private fun seek(labelText: String, min: Int, max: Int, get: () -> Int, set: (Int) -> Unit) {
        container.addView(labelView(labelText))
        container.addView(SeekBar(this).apply {
            this.max = max - min
            progress = (get() - min).coerceIn(0, this.max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) set(value + min)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        })
    }

    private fun number(labelText: String, get: () -> Double, set: (Double) -> Unit) {
        container.addView(labelView(labelText))
        container.addView(EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(formatForEdit(get()))
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) parseNumber(text.toString())?.let { set(it) }
            }
        })
    }

    private fun text(labelText: String, get: () -> String, set: (String) -> Unit) {
        container.addView(labelView(labelText))
        container.addView(EditText(this).apply {
            setText(get())
            setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) set(text.toString()) }
        })
    }

    private fun labelView(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setPadding(0, 12, 0, 2)
    }

    private fun formatForEdit(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else value.toString()

    private fun parseNumber(raw: String): Double? = raw.trim().toDoubleOrNull()
}
