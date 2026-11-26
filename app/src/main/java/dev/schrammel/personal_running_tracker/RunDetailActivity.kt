package dev.schrammel.personal_running_tracker

import android.app.Application
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.schrammel.personal_running_tracker.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Shows a finished run: route map coloured by GPS reliability plus per-km splits.
 */
class RunDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RUN_ID = "run_id"
        // Reliability colouring per spec §6.2
        private const val COLOR_RELIABLE = "#2E7D32"    // green: accuracy <= 10 m
        private const val COLOR_ASSISTED = "#F9A825"    // amber: accuracy <= 25 m
        private const val COLOR_POOR = "#C62828"        // red: worse than 25 m
    }

    private lateinit var mapView: MapView
    private lateinit var statsText: TextView
    private lateinit var splitsText: TextView
    @Volatile private var routeBuckets: Map<String, List<Feature>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_run_detail)
        mapView = findViewById(R.id.mapView)
        statsText = findViewById(R.id.detailStats)
        splitsText = findViewById(R.id.splitsText)
        mapView.onCreate(savedInstanceState)

        val db = AppDatabase.getInstance(applicationContext as Application)
        val runId = intent.getLongExtra(EXTRA_RUN_ID, 0L)
        if (runId <= 0L) {
            finish()
            return
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.editRunButton)
            .setOnClickListener { showEditDialog(db, runId) }

        lifecycleScope.launch {
            val run = withContext(Dispatchers.IO) { db.runDao().getRunById(runId) }
            val gps = withContext(Dispatchers.IO) { db.gpsDao().getGpsForRun(runId) }
            val splits = withContext(Dispatchers.IO) { db.splitDao().getSplitsForRun(runId) }

            // Build reliability buckets off the main thread (O(n) over all fixes)
            val prefs = dev.schrammel.personal_running_tracker.settings.TrackingPreferences(this@RunDetailActivity)
            withContext(Dispatchers.IO) {
                if (gps.size >= 2) {
                    val reliable = mutableListOf<Feature>()
                    val assisted = mutableListOf<Feature>()
                    val poor = mutableListOf<Feature>()
                    for (i in 1 until gps.size) {
                        val pts = listOf(
                            Point.fromLngLat(gps[i - 1].longitude, gps[i - 1].latitude),
                            Point.fromLngLat(gps[i].longitude, gps[i].latitude),
                        )
                        val feature = Feature.fromGeometry(LineString.fromLngLats(pts))
                        when {
                            gps[i - 1].horizontalAccuracyMeters <= prefs.mapReliableMeters -> reliable.add(feature)
                            gps[i - 1].horizontalAccuracyMeters <= prefs.mapAssistedMeters -> assisted.add(feature)
                            else -> poor.add(feature)
                        }
                    }
                    routeBuckets = mapOf("reliable" to reliable, "assisted" to assisted, "poor" to poor)
                }
            }

            run?.let {
                val prefs = dev.schrammel.personal_running_tracker.settings.TrackingPreferences(this@RunDetailActivity)
                val units = dev.schrammel.personal_running_tracker.domain.units.Units
                val name = run.notes.lineSequence().firstOrNull { it.isNotBlank() }?.take(30) ?: "Run ${run.id}"
                findViewById<TextView>(R.id.detailTitle).text = name
                statsText.text = String.format(
                    "%.2f %s  •  %d:%02d  •  %.1f %s",
                    units.distance(run.distanceMeters, prefs.distanceUnit),
                    units.distanceLabel(prefs.distanceUnit),
                    it.elapsedDurationSeconds / 60,
                    it.elapsedDurationSeconds % 60,
                    units.speed(it.averageSpeedMetersPerSecond, prefs.distanceUnit),
                    units.speedLabel(prefs.distanceUnit),
                )
            }
            splitsText.text = if (splits.isEmpty()) "No splits recorded" else {
                val prefs = dev.schrammel.personal_running_tracker.settings.TrackingPreferences(this@RunDetailActivity)
                val units = dev.schrammel.personal_running_tracker.domain.units.Units
                splits.joinToString("\n") { s ->
                    "Split %d: %.2f %s in %d:%02d (%.1f %s, %+d m)".format(
                        s.splitNumber,
                        units.distance(s.distanceMeters, prefs.distanceUnit),
                        units.distanceLabel(prefs.distanceUnit),
                        s.durationSeconds / 60, s.durationSeconds % 60,
                        units.speed(s.averageSpeedMetersPerSecond, prefs.distanceUnit),
                        units.speedLabel(prefs.distanceUnit),
                        s.elevationChangeMeters.toInt(),
                    )
                }
            }

                mapView.getMapAsync { map ->
                    // Buckets are built off the main thread beforehand; only layer registration here.
                    map.setStyle("https://demotiles.maplibre.org/style.json") { style ->
                        val bucketMap = routeBuckets
                        if (bucketMap.isNullOrEmpty() || gps.size < 2) return@setStyle
                        val colors = mapOf(
                            "reliable" to COLOR_RELIABLE,
                            "assisted" to COLOR_ASSISTED,
                            "poor" to COLOR_POOR,
                        )
                        for ((key, features) in bucketMap) {
                            if (features.isEmpty()) continue
                            val sourceId = "route-$key"
                            style.addSource(GeoJsonSource(sourceId, FeatureCollection.fromFeatures(features)))
                            style.addLayer(
                                LineLayer("route-layer-$key", sourceId).withProperties(
                                    lineColor(Color.parseColor(colors.getValue(key))),
                                    lineWidth(4f)
                                )
                            )
                        }
                        val lats = gps.map { it.latitude }
                        val lngs = gps.map { it.longitude }
                        val bounds = LatLngBounds.from(
                            lats.max(), lngs.max(),
                            lats.min(), lngs.min()
                        )
                        map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds, 100))
                    }
                }
        }
    }

    private fun showEditDialog(db: AppDatabase, runId: Long) {
        lifecycleScope.launch {
            val run = withContext(Dispatchers.IO) { db.runDao().getRunById(runId) } ?: return@launch
            val container = android.widget.LinearLayout(this@RunDetailActivity).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(48, 24, 48, 0)
            }
            val distanceInput = android.widget.EditText(this@RunDetailActivity).apply {
                hint = "Distance (km)"
                setText("%.2f".format(run.distanceMeters / 1000.0))
            }
            val notesInput = android.widget.EditText(this@RunDetailActivity).apply {
                hint = "Notes"
                setText(run.notes)
            }
            container.addView(distanceInput)
            container.addView(notesInput)
            androidx.appcompat.app.AlertDialog.Builder(this@RunDetailActivity)
                .setTitle("Edit run")
                .setView(container)
                .setPositiveButton("Save") { _, _ ->
                    val km = distanceInput.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                    lifecycleScope.launch(Dispatchers.IO) {
                        val newDistance = km * 1000.0
                        // Manual corrections must not overwrite original measurements (spec §7.2)
                        val updated = run.copy(
                            distanceMeters = newDistance,
                            averagePaceSecondsPerMeter = if (newDistance > 0) run.elapsedDurationSeconds.toDouble() / newDistance else 0.0,
                            averageSpeedMetersPerSecond = if (run.elapsedDurationSeconds > 0) newDistance / run.elapsedDurationSeconds else 0.0,
                            userAdjusted = true,
                            notes = notesInput.text.toString(),
                            modifiedAt = System.currentTimeMillis(),
                        )
                        db.runDao().updateRun(updated)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState)
    }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
}
