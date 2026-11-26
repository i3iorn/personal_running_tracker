package dev.schrammel.personal_running_tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.app.Application
import dev.schrammel.personal_running_tracker.data.AppDatabase
import dev.schrammel.personal_running_tracker.data.RunRepository
import dev.schrammel.personal_running_tracker.databinding.ActivityMainBinding
import dev.schrammel.personal_running_tracker.settings.TrackingPreferences
import dev.schrammel.personal_running_tracker.tracking.TrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: RunRepository

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            actuallyStartRun()
        } else {
            binding.statusText.text = "Location permission required"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.getInstance(applicationContext as Application)
        repository = RunRepository(db, CoroutineScope(Dispatchers.Default + SupervisorJob()))

        binding.titleText.text = "Personal Running Tracker"
        binding.statusText.text = "Ready"
        binding.elapsedText.text = "--:--"
        binding.distanceText.text = "0.0 km"
        binding.paceText.text = "--:-- /km"
        binding.speedText.text = "0.0 km/h"
        binding.accuracyText.text = "-- m"
        binding.profileText.text = "Tracking Profile: ${TrackingPreferences(this).trackingProfile}"

        binding.startButton.setOnClickListener { startRun() }
        binding.pauseButton.setOnClickListener { pauseRun() }
        binding.finishButton.setOnClickListener { finishRun() }
        binding.profileButton.setOnClickListener { cycleTrackingProfile() }
        binding.runHistoryList.setOnItemClickListener { _, _, position, _ ->
            val run = repository.allRuns.value.getOrNull(position) ?: return@setOnItemClickListener
            binding.statusText.text = "Run ${run.id}: ${run.distanceMeters.toInt()} m"
        }
    }

    private fun cycleTrackingProfile() {
        val prefs = TrackingPreferences(this)
        val next = when (prefs.trackingProfile) {
            "Saver" -> "Balanced"
            "Balanced" -> "High Accuracy"
            else -> "Saver"
        }
        prefs.trackingProfile = next
        binding.profileText.text = "Tracking Profile: $next"
    }

    private fun startRun() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (granted == PackageManager.PERMISSION_GRANTED) {
            actuallyStartRun()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun actuallyStartRun() {
        startService(Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_RUN
        })
        binding.statusText.text = "Tracking…"
        binding.elapsedText.text = "00:00"
        binding.distanceText.text = "0.0 km"
        binding.paceText.text = "--:-- /km"
        binding.speedText.text = "0.0 km/h"
        binding.accuracyText.text = "-- m"
    }

    private fun pauseRun() {
        startService(Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_PAUSE_RUN
        })
        binding.statusText.text = "Paused"
    }

    private fun finishRun() {
        startService(Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_FINISH_RUN
        })
        binding.statusText.text = "Run finished"
    }
}
