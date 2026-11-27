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
import java.util.Locale

/**
 * Comprehensive settings page: every value that used to be hardcoded in code is
 * editable here. Each control carries an ⓘ info icon; tapping it explains how
 * the setting is used. Deliberately exposes expert knobs that can degrade
 * tracking accuracy or battery life if set badly (per the user's request).
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
            { prefs.trackingProfile }, { prefs.trackingProfile = it },
            "Selects the GPS scheduling and sensor-reliance defaults. " +
                "Saver = fewest fixes and most battery saving, High Accuracy = most fixes. " +
                "Adaptive behaviour is active in all profiles.")
        choice("Distance unit", listOf("KILOMETERS", "MILES"),
            { prefs.distanceUnit }, { prefs.distanceUnit = it },
            "Used for every distance and speed shown: live display, run summary, " +
                "statistics, splits and history rows.")
        choice("Pace unit", listOf("MIN_PER_KM", "MIN_PER_MILE"),
            { prefs.paceUnit }, { prefs.paceUnit = it },
            "Converts the pace display between min/km and min/mi.")

        section("Behaviour")
        switch("Automatic pause", { prefs.autoPauseEnabled }, { prefs.autoPauseEnabled = it },
            "When enabled, stationary periods (waiting at lights, aid stations) do not " +
                "accumulate moving time. Total elapsed time always keeps running.")
        seek("Auto-pause sensitivity (0-10)", 0, 10,
            { prefs.autoPauseSensitivity }, { prefs.autoPauseSensitivity = it },
            "How easily the run is considered stationary: 0 = pauses at the slightest " +
                "slow-down, 10 = only real standing still counts. Feeds the moving-time " +
                "threshold and the wake-lock.")
        switch("Keep screen on during run", { prefs.screenOnEnabled }, { prefs.screenOnEnabled = it },
            "Prevents the screen from turning off while a run is active. Costs battery; " +
                "useful when you glance at the display constantly.")
        switch("Ultra mode (minimal display while running)", { prefs.ultraModeEnabled },
            { prefs.ultraModeEnabled = it },
            "Hides pace, speed and accuracy during a run so only the giant elapsed clock " +
                "and distance remain. Made for multi-hour efforts where every glance matters.")

        section("Run / walk & long efforts")
        slider("Running speed threshold (above = RUN)", 1.0, 4.0, 0.1, { prefs.runningSpeedThresholdMps.toDouble() },
            { prefs.runningSpeedThresholdMps = it.toFloat() }, "m/s", 2.2,
            "Speeds above this are classified as RUNNING, below as WALKING in the live " +
                "display and the run/walk time breakdown. 2.2 m/s is a brisk shuffle.")
        slider("Low battery auto-Saver threshold", 5.0, 30.0, 1.0, { prefs.lowBatteryThresholdPercent.toDouble() },
            { prefs.lowBatteryThresholdPercent = it.toInt() }, "%", 15.0,
            "When the battery drops to this level during a run, the profile automatically " +
                "switches to Battery Saver and a notification tells you.")

        section("GPS scheduling (expert — bad values kill battery or accuracy)")
        slider("Battery Saver: GPS min interval", 1.0, 120.0, 1.0, { prefs.saverGpsMinIntervalMs / 1000.0 },
            { prefs.saverGpsMinIntervalMs = (it * 1000).toLong() }, "s", 60.0,
            "Minimum time between GPS fixes in Battery Saver. Longer = less battery, " +
                "coarser distance between fixes.")
        slider("Battery Saver: GPS min distance", 10.0, 190.0, 5.0, { prefs.saverGpsMinDistanceMeters.toDouble() },
            { prefs.saverGpsMinDistanceMeters = it.toFloat() }, "m", 100.0,
            "The GPS only reports when you moved this far — walking pace may produce " +
                "few fixes in Saver mode.")
        slider("High Accuracy: GPS min interval", 1.0, 10.0, 1.0, { prefs.highAccuracyGpsMinIntervalMs / 1000.0 },
            { prefs.highAccuracyGpsMinIntervalMs = (it * 1000).toLong() }, "s", 5.0,
            "Minimum time between fixes in High Accuracy. Small values drain the " +
                "battery quickly — the slider is deliberately capped low.")
        slider("High Accuracy: GPS min distance", 1.0, 19.0, 1.0, { prefs.highAccuracyGpsMinDistanceMeters.toDouble() },
            { prefs.highAccuracyGpsMinDistanceMeters = it.toFloat() }, "m", 10.0,
            "Distance that must be covered before the next fix is reported in " +
                "High Accuracy mode.")

        section("Algorithm tuning (overrides profile defaults)")
        slider("Balanced: GPS min interval", 1.0, 60.0, 1.0, { prefs.tuningGpsMinIntervalMs / 1000.0 },
            { prefs.tuningGpsMinIntervalMs = (it * 1000).toLong() }, "s", 30.0,
            "Baseline fix interval for the Balanced profile's adaptive scheduler.")
        slider("Balanced: GPS max interval", 60.0, 180.0, 5.0, { prefs.tuningGpsMaxIntervalMs / 1000.0 },
            { prefs.tuningGpsMaxIntervalMs = (it * 1000).toLong() }, "s", 120.0,
            "The scheduler never waits longer than this between fixes, even when " +
                "movement is stable and confidence is high.")
        slider("Balanced: GPS min distance", 5.0, 55.0, 5.0, { prefs.tuningGpsMinDistanceMeters.toDouble() },
            { prefs.tuningGpsMinDistanceMeters = it.toFloat() }, "m", 30.0,
            "Distance that must be covered before the next fix in Balanced mode.")
        slider("GPS accuracy threshold", 5.0, 55.0, 5.0, { prefs.tuningGpsAccuracyThresholdMeters.toDouble() },
            { prefs.tuningGpsAccuracyThresholdMeters = it.toFloat() }, "m", 30.0,
            "Fixes reporting worse accuracy than this are treated as low-quality " +
                "by the adaptive scheduler.")
        slider("Stride length", 0.3, 1.2, 0.05, { prefs.tuningStrideLengthMeters },
            { prefs.tuningStrideLengthMeters = it }, "m", 0.75,
            "Your step length; each step-count event adds this much to the " +
                "sensor-estimated distance until the next GPS anchor verifies it.")
        slider("Sensor fusion max GPS segment ratio", 3.0, 17.0, 1.0, { prefs.tuningSensorFusionMaxGpsSegmentRatio },
            { prefs.tuningSensorFusionMaxGpsSegmentRatio = it }, "x", 10.0,
            "If a GPS segment is longer than this multiple of the sensor estimate, it " +
                "is treated as a glitch and the sensor distance is trusted instead.")
        slider("Stable-uncertainty re-evaluate", 10.0, 90.0, 5.0, { prefs.tuningStableUncertaintyReevaluateMeters },
            { prefs.tuningStableUncertaintyReevaluateMeters = it }, "m", 50.0,
            "How far you may travel before accumulated uncertainty forces a fresh " +
                "GPS anchor in stable movement.")

        section("Live pace display")
        slider("Pace smoothing window", 2.0, 18.0, 1.0, { prefs.paceSmoothingWindowMs / 1000.0 },
            { prefs.paceSmoothingWindowMs = (it * 1000).toLong() }, "s", 10.0,
            "Current pace is the average GPS speed over this window — larger = steadier " +
                "but slower to react. The window is extended automatically at slow fix rates.")
        slider("Pace: min speed (below = stationary)", 0.0, 0.6, 0.05, { prefs.paceMinSpeedMps.toDouble() },
            { prefs.paceMinSpeedMps = it.toFloat() }, "m/s", 0.3,
            "Below this smoothed speed the pace shows --:-- instead of a huge number.")
        slider("Pace: max GPS error (above = hide pace)", 5.0, 35.0, 5.0, { prefs.paceMaxAccuracyMeters.toDouble() },
            { prefs.paceMaxAccuracyMeters = it.toFloat() }, "m", 20.0,
            "When the latest fix's accuracy is worse than this, the pace display is " +
                "hidden rather than showing a garbage number.")

        section("Splits & records")
        slider("Split length", 200.0, 1800.0, 100.0, { prefs.splitLengthMeters },
            { prefs.splitLengthMeters = it }, "m", 1000.0,
            "Length of each recorded split (every 1000 m = per kilometre). " +
                "New value applies to runs finished from now on.")
        text("Personal record distances (km, comma separated)",
            { prefs.prDistanceKmList }, { prefs.prDistanceKmList = it },
            "Distances the PR engine scans for (1,3,5,10 = 1 km, 3 km, 5 km, 10 km). " +
                "Arbitrary values allowed, e.g. 1,2.5,50.")

        section("Import & charts")
        slider("GPX import max size", 1.0, 20.0, 1.0, { prefs.importMaxMb.toDouble() },
            { prefs.importMaxMb = it.toInt() }, "MB", 10.0,
            "Files larger than this are refused when importing GPX — a guard " +
                "against accidentally importing something absurd.")
        slider("Rolling-distance window", 7.0, 53.0, 1.0, { prefs.rollingWindowDays.toDouble() },
            { prefs.rollingWindowDays = it.toInt() }, "d", 30.0,
            "Length of the rolling distance window in the statistics chart.")
        slider("Weekly trend chart weeks", 2.0, 14.0, 1.0, { prefs.chartWeeks.toDouble() },
            { prefs.chartWeeks = it.toInt() }, "wk", 8.0,
            "How many weekly bars the distance and pace trend charts show.")

        section("Diagnostics & reliability")
        switch("Battery benchmarking", { prefs.batteryBenchmarkEnabled },
            { prefs.batteryBenchmarkEnabled = it },
            "Records battery start/end/used per run in the diagnostics table and CSV " +
                "export. Turn on to measure drain across tracking profiles.")
        slider("Degraded-GPS notification threshold", 5.0, 45.0, 5.0, { prefs.degradedGpsThresholdMeters.toDouble() },
            { prefs.degradedGpsThresholdMeters = it.toFloat() }, "m", 25.0,
            "When a fix's error exceeds this, the foreground notification says " +
                "'poor GPS, sensor-assisted'.")
        slider("Rating: excellent", 1.0, 9.0, 1.0, { prefs.ratingExcellentMeters.toDouble() },
            { prefs.ratingExcellentMeters = it.toFloat() }, "m", 5.0,
            "Worst GPS error at or below this earns a 5/5 accuracy rating. " +
                "The bands below define 4/3/2/1.")
        slider("Rating: good", 2.0, 18.0, 2.0, { prefs.ratingGoodMeters.toDouble() },
            { prefs.ratingGoodMeters = it.toFloat() }, "m", 10.0,
            "4/5 rating band boundary.")
        slider("Rating: fair", 4.0, 36.0, 2.0, { prefs.ratingFairMeters.toDouble() },
            { prefs.ratingFairMeters = it.toFloat() }, "m", 20.0,
            "3/5 rating band boundary.")
        slider("Rating: poor", 10.0, 90.0, 5.0, { prefs.ratingPoorMeters.toDouble() },
            { prefs.ratingPoorMeters = it.toFloat() }, "m", 50.0,
            "2/5 band boundary; anything worse gets 1/5.")
        slider("Crash-recovery persist interval", 1.0, 10.0, 1.0, { prefs.statePersistIntervalMs / 1000.0 },
            { prefs.statePersistIntervalMs = (it * 1000).toLong() }, "s", 5.0,
            "How often live tracking state and queued GPS fixes are written. " +
                "Smaller = less data lost in a crash, slightly more battery.")

        section("Route map colouring")
        slider("Reliable segment max error", 2.0, 18.0, 1.0, { prefs.mapReliableMeters.toDouble() },
            { prefs.mapReliableMeters = it.toFloat() }, "m", 10.0,
            "Route segments drawn green when their fix error is at most this.")
        slider("Assisted segment max error", 5.0, 45.0, 5.0, { prefs.mapAssistedMeters.toDouble() },
            { prefs.mapAssistedMeters = it.toFloat() }, "m", 25.0,
            "Amber band boundary; worse errors draw red.")

        section("About")
        label("Personal Running Tracker v0.2.0\nOffline-first, sensor-assisted GPS tracking.\nAll data stays on this device.")
    }

    // ---- builder helpers ----

    private fun section(title: String) {
        container.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setPadding(0, 28, 0, 6)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
    }

    private fun label(text: String) {
        container.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            setPadding(0, 10, 0, 0)
        })
    }

    /** Label row with an ⓘ info icon; tapping it explains the setting. */
    private fun labelView(text: String, help: String?) = TextView(this).apply {
        this.text = if (help != null) "$text   ⓘ" else text
        textSize = 15f
        setPadding(0, 14, 0, 2)
        if (help != null) {
            setOnClickListener {
                androidx.appcompat.app.AlertDialog.Builder(this@SettingsActivity)
                    .setTitle(text)
                    .setMessage(help)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun choice(labelText: String, options: List<String>, get: () -> String, set: (String) -> Unit, help: String? = null) {
        container.addView(labelView(labelText, help))
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

    private fun switch(labelText: String, get: () -> Boolean, set: (Boolean) -> Unit, help: String? = null) {
        container.addView(labelView(labelText, help))
        container.addView(SwitchMaterial(this).apply {
            isChecked = get()
            setOnCheckedChangeListener { _, checked -> set(checked) }
        })
    }

    private fun seek(labelText: String, min: Int, max: Int, get: () -> Int, set: (Int) -> Unit, help: String? = null) {
        container.addView(labelView(labelText, help))
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
        help: String? = null,
    ) {
        val valueView = TextView(this).apply {
            textSize = 15f
            setPadding(0, 14, 0, 2)
        }
        val hintView = TextView(this).apply {
            text = java.lang.String.format(java.util.Locale.US, "range %.4g–%.4g %s · default %.4g %s", minVal, maxVal, unit, defaultValue, unit)
            textSize = 12f
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
        container.addView(labelView(labelText, help))
        container.addView(valueView)
        container.addView(bar)
        container.addView(hintView)
    }

    private fun text(labelText: String, get: () -> String, set: (String) -> Unit, help: String? = null) {
        container.addView(labelView(labelText, help))
        container.addView(EditText(this).apply {
            setText(get())
            setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) set(text.toString()) }
        })
    }

    private fun Double.snap(): Double {
        val decimals = if (this < 1.0) 2 else if (this == this.toLong().toDouble()) 0 else 1
        return java.lang.String.format(java.util.Locale.US, "%.${decimals}f", this).toDouble()
    }

    private fun formatValue(value: Double, step: Double): String {
        val decimals = if (step < 0.1) 2 else if (step == step.toLong().toDouble()) 0 else 1
        return java.lang.String.format(java.util.Locale.US, "%.${decimals}f", value)
    }
}
