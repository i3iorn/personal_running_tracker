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

        lifecycleScope.launch {
            val run = withContext(Dispatchers.IO) { db.runDao().getRunById(runId) }
            val gps = withContext(Dispatchers.IO) { db.gpsDao().getGpsForRun(runId) }
            val splits = withContext(Dispatchers.IO) { db.splitDao().getSplitsForRun(runId) }

            run?.let {
                statsText.text = String.format(
                    "%.2f km  •  %d:%02d  •  %.1f km/h",
                    it.distanceMeters / 1000.0,
                    it.elapsedDurationSeconds / 60,
                    it.elapsedDurationSeconds % 60,
                    it.averageSpeedMetersPerSecond * 3.6,
                )
            }
            splitsText.text = if (splits.isEmpty()) "No splits recorded" else splits.joinToString("\n") { s ->
                "Split %d: %.2f km in %d:%02d (%.1f km/h, %+d m)".format(
                    s.splitNumber, s.distanceMeters / 1000.0,
                    s.durationSeconds / 60, s.durationSeconds % 60,
                    s.averageSpeedMetersPerSecond * 3.6,
                    s.elevationChangeMeters.toInt(),
                )
            }

            mapView.getMapAsync { map ->
                map.setStyle("https://demotiles.maplibre.org/style.json") { style ->
                    if (gps.size < 2) return@setStyle
                    // One layer per reliability bucket: colour by each segment's fix accuracy
                    val buckets = mapOf(
                        "reliable" to mutableListOf<Feature>(),
                        "assisted" to mutableListOf<Feature>(),
                        "poor" to mutableListOf<Feature>(),
                    )
                    for (i in 1 until gps.size) {
                        val pts = listOf(
                            Point.fromLngLat(gps[i - 1].longitude, gps[i - 1].latitude),
                            Point.fromLngLat(gps[i].longitude, gps[i].latitude),
                        )
                        val acc = gps[i - 1].horizontalAccuracyMeters
                        val key = when {
                            acc <= 10f -> "reliable"
                            acc <= 25f -> "assisted"
                            else -> "poor"
                        }
                        buckets.getValue(key).add(
                            Feature.fromGeometry(LineString.fromLngLats(pts))
                        )
                    }
                    val colors = mapOf(
                        "reliable" to COLOR_RELIABLE,
                        "assisted" to COLOR_ASSISTED,
                        "poor" to COLOR_POOR,
                    )
                    for ((key, features) in buckets) {
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
