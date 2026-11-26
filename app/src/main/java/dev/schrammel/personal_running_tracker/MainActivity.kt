package dev.schrammel.personal_running_tracker

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.app.Application
import androidx.lifecycle.lifecycleScope
import dev.schrammel.personal_running_tracker.data.AppDatabase
import dev.schrammel.personal_running_tracker.data.CsvExporter
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

    private var trackingService: TrackingService? = null
    private var bound = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiTicker = object : Runnable {
        override fun run() {
            val svc = trackingService
            if (svc != null && svc.isRunningFlag()) {
                binding.elapsedText.text = formatElapsed(svc.currentElapsedMs())
            }
            uiHandler.postDelayed(this, 1000L)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? TrackingService.LocalBinder ?: return
            trackingService = binder.getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            trackingService = null
            bound = false
        }
    }

    private fun formatElapsed(ms: Long): String {
        val totalSeconds = ms / 1000
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

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
        binding.exportButton.setOnClickListener { exportData() }
        binding.runHistoryList.setOnItemClickListener { _, _, position, _ ->
            val run = repository.allRuns.value.getOrNull(position) ?: return@setOnItemClickListener
            binding.statusText.text = "Run ${run.id}: ${run.distanceMeters.toInt()} m"
        }

        bindService(
            Intent(this, TrackingService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        uiHandler.post(uiTicker)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacks(uiTicker)
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
    }

    private fun exportData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val exporter = CsvExporter()
                val exportDir = java.io.File(getExternalFilesDir(null), "export").apply { mkdirs() }
                val runsCsv = java.io.File(exportDir, "runs.csv")
                runsCsv.writeText(exporter.exportRuns(repository.allRuns.value))
                val diagnostics = AppDatabase.getInstance(applicationContext as Application)
                    .runDiagnosticDao().getAllDiagnostics()
                val diagnosticsCsv = java.io.File(exportDir, "run_diagnostics.csv")
                diagnosticsCsv.writeText(exporter.exportDiagnostics(diagnostics))
                val tuningCsv = java.io.File(exportDir, "tracking_tuning.csv")
                tuningCsv.writeText(exporter.exportTuning(TrackingPreferences(this@MainActivity)))
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    binding.statusText.text = "Exported to ${exportDir.absolutePath}"
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    binding.statusText.text = "Export failed: ${e.message}"
                }
            }
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
