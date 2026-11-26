package dev.schrammel.personal_running_tracker

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.appcompat.app.AppCompatActivity
import dev.schrammel.personal_running_tracker.settings.TrackingPreferences

/** Settings (spec §9.4): profile, units, auto-pause, screen-on. All stored in TrackingPreferences. */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val prefs = TrackingPreferences(this)

        val profileSpinner = findViewById<Spinner>(R.id.profileSpinner)
        profileSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Saver", "Balanced", "High Accuracy")
        )
        profileSpinner.setSelection(
            listOf("Saver", "Balanced", "High Accuracy").indexOf(prefs.trackingProfile).coerceAtLeast(0)
        )
        profileSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                prefs.trackingProfile = listOf("Saver", "Balanced", "High Accuracy")[pos]
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        val distanceUnits = listOf("km", "mi")
        val distanceSpinner = findViewById<Spinner>(R.id.distanceUnitSpinner)
        distanceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, distanceUnits)
        distanceSpinner.setSelection(distanceUnits.indexOf(prefs.distanceUnit).coerceAtLeast(0))
        distanceSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                prefs.distanceUnit = distanceUnits[pos]
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        val paceUnits = listOf("min/km", "min/mi")
        val paceSpinner = findViewById<Spinner>(R.id.paceUnitSpinner)
        paceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, paceUnits)
        paceSpinner.setSelection(paceUnits.indexOf(prefs.paceUnit).coerceAtLeast(0))
        paceSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                prefs.paceUnit = paceUnits[pos]
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        val autoPause = findViewById<SwitchMaterial>(R.id.autoPauseSwitch)
        autoPause.isChecked = prefs.autoPauseEnabled
        autoPause.setOnCheckedChangeListener { _, checked -> prefs.autoPauseEnabled = checked }

        val seek = findViewById<SeekBar>(R.id.sensitivitySeek)
        seek.progress = prefs.autoPauseSensitivity.coerceIn(0, 10)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser) prefs.autoPauseSensitivity = value
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })

        val screenOn = findViewById<SwitchMaterial>(R.id.screenOnSwitch)
        screenOn.isChecked = prefs.screenOnEnabled
        screenOn.setOnCheckedChangeListener { _, checked -> prefs.screenOnEnabled = checked }
    }
}
