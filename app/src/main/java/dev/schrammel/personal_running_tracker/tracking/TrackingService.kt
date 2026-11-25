package dev.schrammel.personal_running_tracker.tracking

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.schrammel.personal_running_tracker.R
import dev.schrammel.personal_running_tracker.data.AppDatabase
import dev.schrammel.personal_running_tracker.data.GpsEntity
import dev.schrammel.personal_running_tracker.data.RunEntity
import dev.schrammel.personal_running_tracker.domain.Calculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class TrackingService : Service() {

    companion object {
        const val SERVICE_NAME = "TrackingService"
        const val ACTION_START_RUN = "action_start_run"
        const val ACTION_PAUSE_RUN = "action_pause_run"
        const val ACTION_RESUME_RUN = "action_resume_run"
        const val ACTION_FINISH_RUN = "action_finish_run"
        const val ACTION_DISCARD_RUN = "action_discard_run"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var locationManager: LocationManager
    private lateinit var database: AppDatabase
    private var locationListener: LocationListener? = null
    private val notificationId = 1001

    private var isRunning = false
    private var isPaused = false
    private var accumElapsedMs: Long = 0L
    private var accumMovingMs: Long = 0L
    private var segmentStartMs: Long = 0L

    private var runId: Long = 0L
    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var currentDistanceMeters: Double = 0.0

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        database = AppDatabase.getInstance(getApplication() as Application)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "tracking_service_channel",
            "Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Active run tracking" }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START_RUN -> { startRun(); START_NOT_STICKY }
            ACTION_PAUSE_RUN -> { pauseRun(); START_NOT_STICKY }
            ACTION_RESUME_RUN -> { resumeRun(); START_NOT_STICKY }
            ACTION_FINISH_RUN -> { finishRun(); START_NOT_STICKY }
            ACTION_DISCARD_RUN -> { discardRun(); START_NOT_STICKY }
            else -> START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRun() {
        if (!isRunning) {
            isRunning = true
            isPaused = false
            accumElapsedMs = 0L
            accumMovingMs = 0L
            segmentStartMs = System.currentTimeMillis()
        } else if (isPaused) {
            isPaused = false
            segmentStartMs = System.currentTimeMillis()
        }
        startForegroundIfNeeded()
        startLocationUpdates()
    }

    private fun startForegroundIfNeeded() {
        val notification = NotificationCompat.Builder(this, "tracking_service_channel")
            .setContentTitle("Personal Running Tracker")
            .setContentText("Tracking")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        ServiceCompat.startForeground(
            this,
            1,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    private fun startLocationUpdates() {
        if (locationListener != null) return
        val listener = LocationListener { location: Location ->
            location?.let { onLocationChanged(it) }
        }
        locationListener = listener
        locationManager.requestLocationUpdates(
            TimeUnit.MILLISECONDS.toMillis(30000L),
            30f,
            CriteriaProvider(),
            listener,
            android.os.Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        val listener: LocationListener? = locationListener
        if (listener != null) {
            locationManager.removeUpdates(listener)
        }
        locationListener = null
    }

    private fun CriteriaProvider(): android.location.Criteria = android.location.Criteria().apply {
        isBearingRequired = false
        isSpeedRequired = false
        isAltitudeRequired = false
        accuracy = android.location.Criteria.ACCURACY_HIGH
    }

    private fun onLocationChanged(location: Location) {
        val lat = location.latitude
        val lng = location.longitude
        val timeMs = System.currentTimeMillis()
        if (lastLat != null && lastLng != null) {
            val delta = haversineMeters(lastLat!!, lastLng!!, lat, lng)
            currentDistanceMeters += delta
        }
        lastLat = lat
        lastLng = lng

        serviceScope.launch {
            database.gpsDao().insertGps(
                GpsEntity(
                    runId = runId,
                    timestamp = timeMs,
                    latitude = lat,
                    longitude = lng,
                    altitudeMeters = location.altitude.toDouble(),
                    horizontalAccuracyMeters = location.accuracy.toFloat(),
                    verticalAccuracyMeters = 0f,
                    speedMetersPerSecond = location.speed.coerceAtLeast(0.0f),
                    bearingDegrees = location.bearing.coerceAtLeast(0.0f),
                    provider = location.provider ?: "unknown",
                    measurementQuality = 1
                )
            )
        }
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6378137.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return (R * c).coerceAtLeast(0.0)
    }

    private fun pauseRun() {
        if (!isRunning || isPaused) return
        val now = System.currentTimeMillis()
        accumElapsedMs += now - segmentStartMs
        accumMovingMs += now - segmentStartMs
        isPaused = true
        stopLocationUpdates()
    }

    private fun resumeRun() {
        if (!isRunning || !isPaused) return
        isPaused = false
        segmentStartMs = System.currentTimeMillis()
        startLocationUpdates()
    }

    private fun finishRun() {
        if (!isRunning) return
        val wasPaused = isPaused
        val now = System.currentTimeMillis()
        val elapsedMs = accumElapsedMs + (if (wasPaused) 0L else now - segmentStartMs)
        val movingMs = accumMovingMs + (if (wasPaused) 0L else now - segmentStartMs)
        isRunning = false
        isPaused = false
        stopLocationUpdates()
        val distance = currentDistanceMeters
        if (runId > 0L) {
            serviceScope.launch {
                database.runDao().insertRun(
                    RunEntity(
                        id = 0L,
                        startTimestamp = now - elapsedMs,
                        endTimestamp = now,
                        elapsedDurationSeconds = elapsedMs / 1000L,
                        movingDurationSeconds = movingMs / 1000L,
                        pausedDurationSeconds = (elapsedMs - movingMs) / 1000L,
                        distanceMeters = distance,
                        originalDistanceMeters = distance,
                        averagePaceSecondsPerMeter = if (distance > 0.0) (elapsedMs.toDouble() / 1000.0) / distance else 0.0,
                        averageSpeedMetersPerSecond = if (distance > 0.0) distance / (elapsedMs.toDouble() / 1000.0) else 0.0,
                        elevationGainMeters = 0.0,
                        elevationLossMeters = 0.0,
                        stepCount = 0L,
                        trackingProfile = "Balanced",
                        trackingAlgorithmVersion = "1.0.0",
                        userAdjusted = false,
                        notes = "",
                        createdAt = now,
                        modifiedAt = now
                    )
                )
            }
        }
        stopForeground(0)
        stopSelf()
    }

    private fun discardRun() {
        isRunning = false
        isPaused = false
        stopLocationUpdates()
        stopForeground(0)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopLocationUpdates()
    }
}