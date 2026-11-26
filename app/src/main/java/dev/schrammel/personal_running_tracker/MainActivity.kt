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
import dev.schrammel.personal_running_tracker.data.BackupManager
import dev.schrammel.personal_running_tracker.data.CsvExporter
import dev.schrammel.personal_running_tracker.data.GpxExporter
import dev.schrammel.personal_running_tracker.data.GpxImporter
import dev.schrammel.personal_running_tracker.data.RunRepository
import dev.schrammel.personal_running_tracker.data.toEntity
import dev.schrammel.personal_running_tracker.data.toObservation
import kotlinx.coroutines.flow.first
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
                val prefs = TrackingPreferences(this@MainActivity)
                val units = dev.schrammel.personal_running_tracker.domain.units.Units
                // Screen-on preference only applies while a run is active
                binding.root.keepScreenOn = prefs.screenOnEnabled
                binding.elapsedText.text = formatElapsed(svc.currentElapsedMs())
                binding.distanceText.text = String.format(
                    "%.2f %s", units.distance(svc.currentDistanceMeters(), prefs.distanceUnit),
                    units.distanceLabel(prefs.distanceUnit))
                val accuracy = svc.currentAccuracyMeters()
                val speed = svc.currentSmoothedSpeedMps()
                // Confidence gate: hide instantaneous pace when GPS quality is poor
                if (speed != null && accuracy != null && accuracy <= prefs.paceMaxAccuracyMeters) {
                    val secPerMeter = 1.0 / speed
                    binding.paceText.text = units.paceText(secPerMeter, prefs.paceUnit) + units.paceLabel(prefs.paceUnit)
                    binding.speedText.text = String.format("%.1f %s",
                        units.speed(speed, prefs.distanceUnit), units.speedLabel(prefs.distanceUnit))
                } else {
                    binding.paceText.text = "--:--" + units.paceLabel(prefs.paceUnit)
                    binding.speedText.text = String.format("%.1f %s",
                        units.speed(speed ?: 0.0, prefs.distanceUnit), units.speedLabel(prefs.distanceUnit))
                }
                binding.accuracyText.text = if (accuracy != null) "± %.0f m".format(accuracy) else "± -- m"
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
        binding.discardButton.setOnClickListener { discardRun() }
        binding.profileButton.setOnClickListener { cycleTrackingProfile() }
        binding.statsButton.setOnClickListener {
            startActivity(android.content.Intent(this, StatisticsActivity::class.java))
        }
        binding.exportButton.setOnClickListener { exportData() }
        binding.importButton.setOnClickListener { importGpx() }
        binding.backupButton.setOnClickListener { backupData() }
        binding.restoreButton.setOnClickListener { restoreData() }
        setupHistoryList()
        binding.runHistoryList.setOnItemClickListener { _, _, position, _ ->
            val run = displayedRuns.getOrNull(position) ?: return@setOnItemClickListener
            startActivity(
                android.content.Intent(this, RunDetailActivity::class.java)
                    .putExtra(RunDetailActivity.EXTRA_RUN_ID, run.id)
            )
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
                val db = AppDatabase.getInstance(applicationContext as Application)
                val allRuns = db.runDao().observeAllRuns().first()
                val splitsCsv = java.io.File(exportDir, "run_splits.csv")
                splitsCsv.writeText(
                    exporter.exportSplits(allRuns.flatMap { db.splitDao().getSplitsForRun(it.id) })
                )
                val gpx = GpxExporter()
                allRuns.forEach { run ->
                    val gps = db.gpsDao().getGpsForRun(run.id)
                    if (gps.size >= 2) {
                        java.io.File(exportDir, "run-${run.id}.gpx").writeText(gpx.toGpx(run.id, gps.map { it.toObservation() }))
                    }
                }
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

    private fun backupData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = BackupManager(this@MainActivity).createBackup()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    binding.statusText.text = "Backup: ${file.absolutePath}"
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    binding.statusText.text = "Backup failed: ${e.message}"
                }
            }
        }
    }

    private fun restoreData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val latest = BackupManager(this@MainActivity).listBackups().firstOrNull()
                if (latest == null) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        binding.statusText.text = "No backup found"
                    }
                    return@launch
                }
                val ok = BackupManager(this@MainActivity).restoreBackup(latest)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    binding.statusText.text = if (ok) {
                        "Restored ${latest.name} — restart the app to see the data"
                    } else "Restore failed"
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    binding.statusText.text = "Restore failed: ${e.message}"
                }
            }
        }
    }

    private val gpxImportPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importGpxFrom(uri)
    }

    private fun importGpx() {
        gpxImportPicker.launch(arrayOf("*/*"))
    }

    private fun importGpxFrom(uri: android.net.Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val maxMb = dev.schrammel.personal_running_tracker.settings.TrackingPreferences(this@MainActivity).importMaxMb
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val sb = StringBuilder()
                    val buf = CharArray(8192)
                    while (true) {
                        val n = reader.read(buf)
                        if (n < 0) break
                        sb.append(buf, 0, n)
                        if (sb.length > maxMb * 1_000_000) {
                            throw IllegalArgumentException("GPX file too large (limit $maxMb MB)")
                        }
                    }
                    sb.toString()
                } ?: throw IllegalStateException("Cannot read file")
                val result = GpxImporter().importGpx(text)
                val db = AppDatabase.getInstance(applicationContext as Application)
                db.runDao().insertRun(result.run.toEntity())
                result.gpsObservations.forEach { obs ->
                    db.gpsDao().insertGps(
                        dev.schrammel.personal_running_tracker.data.GpsEntity(
                            runId = obs.runId, timestamp = obs.timestamp,
                            latitude = obs.latitude, longitude = obs.longitude,
                            altitudeMeters = obs.altitudeMeters,
                            horizontalAccuracyMeters = obs.horizontalAccuracyMeters,
                            verticalAccuracyMeters = obs.verticalAccuracyMeters,
                            speedMetersPerSecond = obs.speedMetersPerSecond,
                            bearingDegrees = obs.bearingDegrees,
                            provider = obs.provider, measurementQuality = obs.measurementQuality,
                        )
                    )
                }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    binding.statusText.text = "Imported: %.2f km from GPX".format(result.run.distanceMeters / 1000.0)
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    binding.statusText.text = "Import failed: ${e.message}"
                }
            }
        }
    }

    private var filterDays: Int? = null
    private var displayedRuns: List<dev.schrammel.personal_running_tracker.model.RunRecord> = emptyList()
    private var allRunsCache: List<dev.schrammel.personal_running_tracker.model.RunRecord> = emptyList()
    private lateinit var historyAdapter: HistoryAdapter
    private val thumbnailsLoading = mutableSetOf<Long>()

    private fun setupHistoryList() {
        historyAdapter = HistoryAdapter(this)
        binding.runHistoryList.adapter = historyAdapter
        lifecycleScope.launch {
            repository.allRuns.collect { runs ->
                allRunsCache = runs
                applyHistoryFilter()
            }
        }
        binding.filterButton.setOnClickListener { showFilterDialog() }
        binding.titleText.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
    }

    private fun applyHistoryFilter() {
        val prefs = TrackingPreferences(this)
        val units = dev.schrammel.personal_running_tracker.domain.units.Units
        displayedRuns = allRunsCache.filter { run ->
            filterDays == null ||
                run.startTimestamp >= System.currentTimeMillis() - filterDays!! * 86_400_000L
        }
        val fmt = java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.US)
        historyAdapter.setRows(displayedRuns.map { run ->
            val name = run.notes.lineSequence().firstOrNull { it.isNotBlank() }?.take(30)
                ?: "Run ${run.id}"
            HistoryAdapter.Row(
                runId = run.id,
                title = name,
                subtitle = "%s  •  %.2f %s  •  %d:%02d:%02d".format(
                    fmt.format(java.util.Date(run.startTimestamp)),
                    units.distance(run.distanceMeters, prefs.distanceUnit),
                    units.distanceLabel(prefs.distanceUnit),
                    run.elapsedDurationSeconds / 3600,
                    (run.elapsedDurationSeconds % 3600) / 60,
                    run.elapsedDurationSeconds % 60,
                ),
            )
        })
        // Render route thumbnails lazily, off the main thread
        val db = AppDatabase.getInstance(applicationContext as Application)
        for (run in displayedRuns) {
            if (historyAdapter.hasThumbnail(run.id) || !thumbnailsLoading.add(run.id)) continue
            lifecycleScope.launch(Dispatchers.IO) {
                val points = db.gpsDao().getGpsForRun(run.id)
                val bitmap = if (points.size >= 2) {
                    // Sample to at most 200 points for the tiny canvas
                    val step = maxOf(1, points.size / 200)
                    HistoryAdapter.renderThumbnail(points.filterIndexed { i, _ -> i % step == 0 })
                } else null
                if (bitmap != null) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        historyAdapter.setThumbnail(run.id, bitmap)
                    }
                }
                thumbnailsLoading.remove(run.id)
            }
        }
    }

    private fun showFilterDialog() {
        val options = arrayOf("All", "Last 7 days", "Last 30 days", "Last 90 days")
        val values = listOf<Int?>(null, 7, 30, 90)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Filter history")
            .setSingleChoiceItems(options, values.indexOf(filterDays)) { dialog, which ->
                filterDays = values[which]
                binding.filterButton.text = "Filter: ${options[which]}"
                dialog.dismiss()
                applyHistoryFilter()
            }
            .show()
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
        val svc = trackingService
        if (svc?.isRunningFlag() == true) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Finish run?")
                .setMessage("The current run will be saved.")
                .setPositiveButton("Finish") { _, _ -> actuallyFinishRun() }
                .setNegativeButton("Keep running", null)
                .show()
        } else {
            actuallyFinishRun()
        }
    }

    private fun actuallyFinishRun() {
        startService(Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_FINISH_RUN
        })
        binding.statusText.text = "Run finished"
        lifecycleScope.launch {
            // The service writes the run + diagnostics asynchronously; retry briefly.
            var summary: dev.schrammel.personal_running_tracker.data.RunEntity? = null
            var diag: dev.schrammel.personal_running_tracker.data.RunDiagnostic? = null
            repeat(5) {
                kotlinx.coroutines.delay(500)
                val svc = trackingService ?: return@repeat
                summary = svc.fetchLastRunSummary()
                diag = svc.fetchLastRunDiagnostic()
                if (summary != null) return@launch
            }
            summary?.let { showRunSummary(it, diag) }
        }
    }

    private fun discardRun() {
        val svc = trackingService
        if (svc?.isRunningFlag() == true) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Discard run?")
                .setMessage("All data from the current run will be lost. This cannot be undone.")
                .setPositiveButton("Discard") { _, _ -> actuallyDiscardRun() }
                .setNegativeButton("Keep running", null)
                .show()
        } else {
            actuallyDiscardRun()
        }
    }

    private fun actuallyDiscardRun() {
        startService(Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_DISCARD_RUN
        })
        binding.statusText.text = "Run discarded"
        binding.elapsedText.text = "--:--"
        binding.distanceText.text = "0.0 km"
        binding.paceText.text = "--:-- /km"
        binding.speedText.text = "0.0 km/h"
        binding.accuracyText.text = "-- m"
    }

    private fun showRunSummary(
        run: dev.schrammel.personal_running_tracker.data.RunEntity,
        diag: dev.schrammel.personal_running_tracker.data.RunDiagnostic?,
    ) {
        val prefs = TrackingPreferences(this)
        val units = dev.schrammel.personal_running_tracker.domain.units.Units
        val name = run.notes.lineSequence().firstOrNull { it.isNotBlank() }?.take(40) ?: "Run Summary"
        val text = buildString {
            append("Distance: %.2f %s\n".format(
                units.distance(run.distanceMeters, prefs.distanceUnit),
                units.distanceLabel(prefs.distanceUnit)))
            append("Duration: %d:%02d:%02d\n".format(
                run.elapsedDurationSeconds / 3600,
                (run.elapsedDurationSeconds % 3600) / 60,
                run.elapsedDurationSeconds % 60))
            append("Moving: %d:%02d:%02d\n".format(
                run.movingDurationSeconds / 3600,
                (run.movingDurationSeconds % 3600) / 60,
                run.movingDurationSeconds % 60))
            append("Paused: %d:%02d:%02d\n".format(
                run.pausedDurationSeconds / 3600,
                (run.pausedDurationSeconds % 3600) / 60,
                run.pausedDurationSeconds % 60))
            append("Avg pace: %s %s\n".format(
                units.paceText(run.averagePaceSecondsPerMeter, prefs.paceUnit),
                units.paceLabel(prefs.paceUnit)))
            append("Avg speed: %.1f %s\n".format(
                units.speed(run.averageSpeedMetersPerSecond, prefs.distanceUnit),
                units.speedLabel(prefs.distanceUnit)))
            append("Steps: %d\n".format(run.stepCount))
            if (diag != null) {
                append("\nBattery: %s%% used (start %s%%, end %s%%)\n".format(
                    diag.batteryConsumedPercent.toString(),
                    diag.batteryStartPercent?.toString() ?: "?",
                    diag.batteryEndPercent?.toString() ?: "?"))
                append("GPS fixes: %d, sensor samples: %d\n".format(diag.gpsFixCount, diag.sensorFixCount))
                append("Raw GPS vs fused distance: %+.1f m\n".format(diag.fusedVsGpsDeltaMeters))
                append("Max GPS error: %.0f m (rating %s/5)".format(
                    diag.maxAccuracyMeters ?: 0f,
                    diag.accuracyRating?.toString() ?: "?"))
            }
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(name)
            .setMessage(text)
            .setPositiveButton("Save", null)
            .show()
    }
}
