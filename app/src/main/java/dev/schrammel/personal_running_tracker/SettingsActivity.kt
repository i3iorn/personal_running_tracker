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
        switch("Ultra mode (minimal display while running)", { prefs.ultraModeEnabled },
            { prefs.ultraModeEnabled = it })

        section("Run / walk & long efforts")
        slider("Running speed threshold (above = RUN)", 1.0, 4.0, 0.1, { prefs.runningSpeedThresholdMps.toDouble() },
            { prefs.runningSpeedThresholdMps = it.toFloat() }, "m/s", 2.2)
        slider("Low battery auto-Saver threshold", 5.0, 30.0, 1.0, { prefs.lowBatteryThresholdPercent.toDouble() },
            { prefs.lowBatteryThresholdPercent = it.toInt() }, "%", 15.0)

        section("GPS scheduling (expert — bad values kill battery or accuracy)")
        slider("Battery Saver: GPS min interval", 1.0, 120.0, 1.0, { prefs.saverGpsMinIntervalMs / 1000.0 },
            { prefs.saverGpsMinIntervalMs = (it * 1000).toLong() }, "s", 60.0)
        slider("Battery Saver: GPS min distance", 10.0, 190.0, 5.0, { prefs.saverGpsMinDistanceMeters.toDouble() },
            { prefs.saverGpsMinDistanceMeters = it.toFloat() }, "m", 100.0)
        slider("High Accuracy: GPS min interval", 1.0, 10.0, 1.0, { prefs.highAccuracyGpsMinIntervalMs / 1000.0 },
            { prefs.highAccuracyGpsMinIntervalMs = (it * 1000).toLong() }, "s", 5.0)
        slider("High Accuracy: GPS min distance", 1.0, 19.0, 1.0, { prefs.highAccuracyGpsMinDistanceMeters.toDouble() },
            { prefs.highAccuracyGpsMinDistanceMeters = it.toFloat() }, "m", 10.0)

        section("Algorithm tuning (overrides profile defaults)")
        slider("Balanced: GPS min interval", 1.0, 60.0, 1.0, { prefs.tuningGpsMinIntervalMs / 1000.0 },
            { prefs.tuningGpsMinIntervalMs = (it * 1000).toLong() }, "s", 30.0)
        slider("Balanced: GPS max interval", 60.0, 180.0, 5.0, { prefs.tuningGpsMaxIntervalMs / 1000.0 },
            { prefs.tuningGpsMaxIntervalMs = (it * 1000).toLong() }, "s", 120.0)
        slider("Balanced: GPS min distance", 5.0, 55.0, 5.0, { prefs.tuningGpsMinDistanceMeters.toDouble() },
            { prefs.tuningGpsMinDistanceMeters = it.toFloat() }, "m", 30.0)
        slider("GPS accuracy threshold", 5.0, 55.0, 5.0, { prefs.tuningGpsAccuracyThresholdMeters.toDouble() },
            { prefs.tuningGpsAccuracyThresholdMeters = it.toFloat() }, "m", 30.0)
        slider("Stride length", 0.3, 1.2, 0.05, { prefs.tuningStrideLengthMeters },
            { prefs.tuningStrideLengthMeters = it }, "m", 0.75)
        slider("Sensor fusion max GPS segment ratio", 3.0, 17.0, 1.0, { prefs.tuningSensorFusionMaxGpsSegmentRatio },
            { prefs.tuningSensorFusionMaxGpsSegmentRatio = it }, "x", 10.0)
        slider("Stable-uncertainty re-evaluate", 10.0, 90.0, 5.0, { prefs.tuningStableUncertaintyReevaluateMeters },
            { prefs.tuningStableUncertaintyReevaluateMeters = it }, "m", 50.0)

        section("Live pace display")
        slider("Pace smoothing window", 2.0, 18.0, 1.0, { prefs.paceSmoothingWindowMs / 1000.0 },
            { prefs.paceSmoothingWindowMs = (it * 1000).toLong() }, "s", 10.0)
        slider("Pace: min speed (below = stationary)", 0.0, 0.6, 0.05, { prefs.paceMinSpeedMps.toDouble() },
            { prefs.paceMinSpeedMps = it.toFloat() }, "m/s", 0.3)
        slider("Pace: max GPS error (above = hide pace)", 5.0, 35.0, 5.0, { prefs.paceMaxAccuracyMeters.toDouble() },
            { prefs.paceMaxAccuracyMeters = it.toFloat() }, "m", 20.0)

        section("Splits & records")
        slider("Split length", 200.0, 1800.0, 100.0, { prefs.splitLengthMeters },
            { prefs.splitLengthMeters = it }, "m", 1000.0)
        text("Personal record distances (km, comma separated)",
            { prefs.prDistanceKmList }, { prefs.prDistanceKmList = it })

        section("Import & charts")
        slider("GPX import max size", 1.0, 20.0, 1.0, { prefs.importMaxMb.toDouble() },
            { prefs.importMaxMb = it.toInt() }, "MB", 10.0)
        slider("Rolling-distance window", 7.0, 53.0, 1.0, { prefs.rollingWindowDays.toDouble() },
            { prefs.rollingWindowDays = it.toInt() }, "d", 30.0)
        slider("Weekly trend chart weeks", 2.0, 14.0, 1.0, { prefs.chartWeeks.toDouble() },
            { prefs.chartWeeks = it.toInt() }, "wk", 8.0)

        section("Diagnostics & reliability")
        switch("Battery benchmarking", { prefs.batteryBenchmarkEnabled },
            { prefs.batteryBenchmarkEnabled = it })
        slider("Degraded-GPS notification threshold", 5.0, 45.0, 5.0, { prefs.degradedGpsThresholdMeters.toDouble() },
            { prefs.degradedGpsThresholdMeters = it.toFloat() }, "m", 25.0)
        slider("Rating: excellent", 1.0, 9.0, 1.0, { prefs.ratingExcellentMeters.toDouble() },
            { prefs.ratingExcellentMeters = it.toFloat() }, "m", 5.0)
        slider("Rating: good", 2.0, 18.0, 2.0, { prefs.ratingGoodMeters.toDouble() },
            { prefs.ratingGoodMeters = it.toFloat() }, "m", 10.0)
        slider("Rating: fair", 4.0, 36.0, 2.0, { prefs.ratingFairMeters.toDouble() },
            { prefs.ratingFairMeters = it.toFloat() }, "m", 20.0)
        slider("Rating: poor", 10.0, 90.0, 5.0, { prefs.ratingPoorMeters.toDouble() },
            { prefs.ratingPoorMeters = it.toFloat() }, "m", 50.0)
        slider("Crash-recovery persist interval", 1.0, 10.0, 1.0, { prefs.statePersistIntervalMs / 1000.0 },
            { prefs.statePersistIntervalMs = (it * 1000).toLong() }, "s", 5.0)

        section("Route map colouring")
        slider("Reliable segment max error", 2.0, 18.0, 1.0, { prefs.mapReliableMeters.toDouble() },
            { prefs.mapReliableMeters = it.toFloat() }, "m", 10.0)
        slider("Assisted segment max error", 5.0, 45.0, 5.0, { prefs.mapAssistedMeters.toDouble() },
            { prefs.mapAssistedMeters = it.toFloat() }, "m", 25.0)

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

    private fun slider(
        labelText: String,
        minVal: Double,
        maxVal: Double,
        stepVal: Double,
        get: () -> Double,
        set: (Double) -> Unit,
        unit: String,
        defaultValue: Double,
    ) {
        val valueView = TextView(this).apply {
            textSize = 13f
            setPadding(0, 12, 0, 2)
        }
        val hintView = TextView(this).apply {
            text = "range %.4g–%.4g %s · default %.4g %s".format(minVal, maxVal, unit, defaultValue, unit)
            textSize = 11f
            setTextColor(resources.getColor(R.color.rt_on_surface_variant, theme))
        }
        val bar = SeekBar(this).apply {
            val steps = ((maxVal - minVal) / stepVal).toInt().coerceAtLeast(1)
            max = steps
            progress = (((get() - minVal) / stepVal).toInt().coerceIn(0, steps))
            valueView.text = formatValue(get(), stepVal) + " $unit"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar?, p: Int, fromUser: Boolean) {
                    val value = (minVal + p * stepVal).snap()
                    if (fromUser) {
                        set(value)
                        valueView.text = formatValue(value, stepVal) + " $unit"
                    }
                }
                override fun onStartTrackingTouch(b: SeekBar?) {}
                override fun onStopTrackingTouch(b: SeekBar?) {}
            })
        }
        container.addView(valueView)
        container.addView(bar)
        container.addView(hintView)
    }

    private fun Double.snap(): Double {
        val decimals = if (this < 1.0) 2 else if (this == this.toLong().toDouble()) 0 else 1
        return "%.${decimals}f".format(this).toDouble()
    }

    private fun formatValue(value: Double, step: Double): String {
        val decimals = if (step < 0.1) 2 else if (step == step.toLong().toDouble()) 0 else 1
        return "%.${decimals}f".format(value)
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
