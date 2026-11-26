package dev.schrammel.personal_running_tracker.tracking

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.room.withTransaction
import dev.schrammel.personal_running_tracker.data.AppDatabase
import dev.schrammel.personal_running_tracker.data.GpsEntity
import dev.schrammel.personal_running_tracker.data.RunEntity
import dev.schrammel.personal_running_tracker.data.RunDiagnostic
import dev.schrammel.personal_running_tracker.data.RunStateEntity
import dev.schrammel.personal_running_tracker.data.SplitEntity
import dev.schrammel.personal_running_tracker.domain.split.GpsPoint
import dev.schrammel.personal_running_tracker.domain.split.SplitCalculator
import dev.schrammel.personal_running_tracker.domain.sensor.GpsAnchor
import dev.schrammel.personal_running_tracker.domain.sensor.SensorFusion
import dev.schrammel.personal_running_tracker.settings.TrackingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class TrackingService : Service(), SensorAdapter.Callback {

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
    private lateinit var prefs: TrackingPreferences
    private var locationListener: LocationListener? = null
    private var sensorAdapter: SensorAdapter? = null
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
    private var trackingProfile: String = "Balanced"
    private var stepCount: Long = 0L
    private var batteryStartPct: Int? = null
    private var gpsFixCount: Int = 0
    private var sensorFixCount: Int = 0
    private var maxAccuracyMetersSeen: Float = 0f
    private var lastAccuracyMeters: Float? = null
    private var lastNotificationDegraded: Boolean = false
    private var lastFixTimeMs: Long = 0L
    private var lastFixGapMs: Long = 0L
    private var movingSegmentMs: Long = 0L
    private var fusion: SensorFusion? = null
    private var baseDistanceMeters: Double = 0.0
    private var rawGpsDistanceMeters: Double = 0.0
    private val recentSpeeds = ArrayDeque<Pair<Long, Float>>() // (timeMs, speedMps)
    @Volatile private var lastFinishedRunId: Long = 0L

    // Public API for MainActivity to query live state
    fun isRunningFlag(): Boolean = isRunning
    fun currentElapsedMs(): Long = accumElapsedMs + (if (isPaused) 0L else System.currentTimeMillis() - segmentStartMs)
    fun currentDistanceMeters(): Double = currentDistanceMeters
    fun currentAccuracyMeters(): Float? = lastAccuracyMeters

    /** Smoothed current speed (m/s). The effective window scales up to cover the
     * configured GPS fix interval so the buffer never sits empty between fixes. */
    fun currentSmoothedSpeedMps(): Double? {
        val effWindow = maxOf(prefs.paceSmoothingWindowMs, 3 * lastFixGapMs + 1_000L)
        val minSpeed = prefs.paceMinSpeedMps.toDouble()
        val cutoff = System.currentTimeMillis() - effWindow
        recentSpeeds.removeAll { it.first < cutoff }
        while (recentSpeeds.size > 30) recentSpeeds.removeFirst()
        if (recentSpeeds.isEmpty()) return null
        val mean = recentSpeeds.map { it.second }.average()
        return if (mean >= minSpeed) mean else null
    }

    inner class LocalBinder : android.os.Binder() {
        fun getService(): TrackingService = this@TrackingService
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        database = AppDatabase.getInstance(getApplication() as Application)
        prefs = TrackingPreferences(this)
        createNotificationChannel()
        serviceScope.launch { restoreInterruptedRun() }
        serviceScope.launch {
            // Persist tracking state every 5 s while a run is active (spec §14)
            while (true) {
                kotlinx.coroutines.delay(prefs.statePersistIntervalMs.coerceAtLeast(1_000L))
                if (isRunning) persistState()
            }
        }
    }

    /** Resume an interrupted run's counters after process death. */
    private suspend fun restoreInterruptedRun() {
        val state = database.runStateDao().get() ?: return
        if (!state.isRunning) return
        runId = state.runId
        isRunning = true
        isPaused = state.isPaused
        accumElapsedMs = state.accumElapsedMs
        accumMovingMs = state.accumMovingMs
        segmentStartMs = System.currentTimeMillis()
        currentDistanceMeters = state.distanceMeters
        stepCount = state.stepCount
        trackingProfile = state.trackingProfile
        batteryStartPct = state.batteryStartPct
        movingSegmentMs = prefs.runStateMovingSegmentMs
        // Rebuild fusion from the restored distance; anchors resume reconciliation
        // from here without double counting (pending sensor distance starts at 0).
        fusion = SensorFusion(
            maxGpsSegmentRatio = prefs.tuningSensorFusionMaxGpsSegmentRatio,
            uncertaintyPerStepMeters = 5.0,
        )
        baseDistanceMeters = state.distanceMeters
    }

    private suspend fun persistState() {
        prefs.runStateMovingSegmentMs = movingSegmentMs
        database.runStateDao().upsert(
            RunStateEntity(
                runId = runId, isRunning = isRunning, isPaused = isPaused,
                accumElapsedMs = accumElapsedMs, accumMovingMs = accumMovingMs,
                segmentStartMs = segmentStartMs, distanceMeters = currentDistanceMeters,
                stepCount = stepCount, trackingProfile = trackingProfile,
                batteryStartPct = batteryStartPct,
            )
        )
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

    // (onBind is overridden above to expose LocalBinder)


    private fun startRun() {
        if (!isRunning) {
            isRunning = true
            isPaused = false
            accumElapsedMs = 0L
            accumMovingMs = 0L
            movingSegmentMs = 0L
            lastFixTimeMs = 0L
            segmentStartMs = System.currentTimeMillis()
            currentDistanceMeters = 0.0
            lastLat = null
            lastLng = null
            runId = System.currentTimeMillis()
            // Fresh fusion instance per run: GPS anchors reconcile with sensor estimates
            fusion = SensorFusion(
                maxGpsSegmentRatio = prefs.tuningSensorFusionMaxGpsSegmentRatio,
                uncertaintyPerStepMeters = 5.0,
            )
            baseDistanceMeters = 0.0
            rawGpsDistanceMeters = 0.0
            // Battery reads are binder calls; keep them off the UI/caller thread.
            serviceScope.launch { batteryStartPct = getBatteryLevel() }
            gpsFixCount = 0
            sensorFixCount = 0
            maxAccuracyMetersSeen = 0f
        } else if (isPaused) {
            isPaused = false
            segmentStartMs = System.currentTimeMillis()
        }
        startForegroundIfNeeded()
        startLocationUpdates(trackingProfile)
        startSensorUpdates()
    }

    private fun getBatteryLevel(): Int? {
        val bm = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (level in 1..100) level else null
    }

    /** Surface degraded GPS quality on the foreground notification (spec §14). */
    private fun updateNotificationText(accuracy: Float) {
        val degraded = accuracy > prefs.degradedGpsThresholdMeters
        if (degraded == lastNotificationDegraded) return
        lastNotificationDegraded = degraded
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            1,
            NotificationCompat.Builder(this, "tracking_service_channel")
                .setContentTitle("Personal Running Tracker")
                .setContentText(if (degraded) "Tracking — poor GPS, sensor-assisted" else "Tracking")
                .setSmallIcon(dev.schrammel.personal_running_tracker.R.drawable.ic_runner)
                .setOngoing(true)
                .build()
        )
    }

    private fun startForegroundIfNeeded() {
        val notification = NotificationCompat.Builder(this, "tracking_service_channel")
            .setContentTitle("Personal Running Tracker")
            .setContentText("Tracking")
            .setSmallIcon(dev.schrammel.personal_running_tracker.R.drawable.ic_runner)
            .build()
        ServiceCompat.startForeground(
            this,
            1,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    private fun startLocationUpdates(profile: String) {
        if (locationListener != null) return
        val listener = LocationListener { location: Location ->
            location?.let { onLocationChanged(it) }
        }
        locationListener = listener
        val (minTimeMs, minDistanceMeters) = locationRequestParams(profile)
        locationManager.requestLocationUpdates(
            minTimeMs,
            minDistanceMeters,
            CriteriaProvider(),
            listener,
            android.os.Looper.getMainLooper()
        )
    }

    private fun locationRequestParams(profile: String): Pair<Long, Float> {
        val minTime = prefs.tuningGpsMinIntervalMs.coerceAtLeast(1_000L)
        val minDistance = prefs.tuningGpsMinDistanceMeters.coerceAtLeast(1f)
        return when (profile) {
            "Saver" -> Pair(prefs.saverGpsMinIntervalMs, prefs.saverGpsMinDistanceMeters)
            "High Accuracy" -> Pair(prefs.highAccuracyGpsMinIntervalMs, prefs.highAccuracyGpsMinDistanceMeters)
            else -> Pair(minTime, minDistance)
        }
    }

    private fun stopLocationUpdates() {
        val listener: LocationListener? = locationListener
        if (listener != null) {
            locationManager.removeUpdates(listener)
        }
        locationListener = null
    }

    private fun startSensorUpdates() {
        if (sensorAdapter != null) return
        sensorAdapter = SensorAdapter(this, this).also { it.start() }
    }

    private fun stopSensorUpdates() {
        sensorAdapter?.stop()
        sensorAdapter = null
    }

    private fun CriteriaProvider(): android.location.Criteria = android.location.Criteria().apply {
        isBearingRequired = false
        isSpeedRequired = false
        isAltitudeRequired = false
        // accuracy = android.location.Criteria.ACCURACY_HIGH  // Removed: ACCURACY_HIGH is out of range [0, 2] in Android 35
    }

    override fun onStepCount(totalSteps: Long) {
        stepCount = totalSteps
        sensorFixCount++
        // Sensor-assisted distance: pending estimate until the next GPS anchor verifies it
        fusion?.onSensorStep(prefs.tuningStrideLengthMeters)
    }

    override fun onAccelerometerMagnitude(magnitude: Double) {}

    override fun onGyroscope(x: Float, y: Float, z: Float) {}

    override fun onHeading(azimuthDegrees: Float) {}

    private fun onLocationChanged(location: Location) {
        val lat = location.latitude
        val lng = location.longitude
        val timeMs = System.currentTimeMillis()
        val prevLat = lastLat
        val prevLng = lastLng
        val gapMs = if (lastFixTimeMs > 0) (timeMs - lastFixTimeMs).coerceAtLeast(0) else 0L
        lastFixTimeMs = timeMs
        lastFixGapMs = gapMs
        if (prevLat != null && prevLng != null) {
            rawGpsDistanceMeters += haversineMeters(prevLat, prevLng, lat, lng)
        }
        val accuracy = location.accuracy.coerceAtLeast(0f)
        if (accuracy > maxAccuracyMetersSeen) maxAccuracyMetersSeen = accuracy
        lastAccuracyMeters = accuracy
        // Speed: trust the GPS-reported value when present, otherwise derive it
        // from the segment distance/time (reported speed is often 0 on network fixes).
        val reported = location.speed.coerceAtLeast(0f)
        val segmentSpeed = if (prevLat != null && prevLng != null && gapMs > 0) {
            (haversineMeters(prevLat, prevLng, lat, lng) / (gapMs / 1000.0)).toFloat()
        } else reported
        val effectiveSpeed = if (reported > 0.5f) reported else segmentSpeed.coerceAtLeast(0f)
        recentSpeeds.addLast(timeMs to effectiveSpeed)

        // Sensor fusion: reconcile this anchor with the pending sensor estimate.
        // Never double counts: the pending sensor distance is replaced by the
        // verified GPS segment (or kept when GPS looks glitchy).
        val f = fusion
        if (f != null) {
            val update = f.onGpsAnchor(
                GpsAnchor(
                    latitude = lat, longitude = lng,
                    altitudeMeters = location.altitude, timestampMs = timeMs,
                    accuracyMeters = accuracy, speedMps = effectiveSpeed,
                    bearingDegrees = location.bearing,
                )
            )
            currentDistanceMeters = baseDistanceMeters + update.totalDistanceMeters
        }

        // Auto-pause: stationary periods do not accumulate moving time (spec §10)
        val stationaryThresholdMps = 0.2 + prefs.autoPauseSensitivity * 0.03
        if (!isPaused && gapMs > 0 && prefs.autoPauseEnabled &&
            effectiveSpeed < stationaryThresholdMps
        ) {
            // stationary: elapsed time still runs, moving time does not
        } else if (!isPaused && gapMs > 0) {
            movingSegmentMs += gapMs
        }
        if (isRunning) updateNotificationText(accuracy)
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
        gpsFixCount++
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
        // Moving time is accumulated per fix in onLocationChanged (auto-pause aware)
        accumMovingMs += movingSegmentMs
        movingSegmentMs = 0
        isPaused = true
        stopLocationUpdates()
        stopSensorUpdates()
    }

    private fun resumeRun() {
        if (!isRunning || !isPaused) return
        isPaused = false
        movingSegmentMs = 0L
        segmentStartMs = System.currentTimeMillis()
        startLocationUpdates(trackingProfile)
        startSensorUpdates()
    }

    private fun finishRun() {
        if (!isRunning) return
        val wasPaused = isPaused
        val now = System.currentTimeMillis()
        val elapsedMs = accumElapsedMs + (if (wasPaused) 0L else now - segmentStartMs)
        val movingMs = accumMovingMs + movingSegmentMs
        isRunning = false
        isPaused = false
        stopLocationUpdates()
        stopSensorUpdates()
        val distance = currentDistanceMeters
        if (runId > 0L) {
            serviceScope.launch {
                // Single transaction: run + diagnostics + splits commit or roll back together
                database.withTransaction {
                    database.runDao().insertRun(
                    RunEntity(
                        id = runId,
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
                        stepCount = stepCount,
                        trackingProfile = trackingProfile,
                        trackingAlgorithmVersion = "1.0.0",
                        userAdjusted = false,
                        notes = "",
                        createdAt = now,
                        modifiedAt = now
                    )
                    )
                    recordDiagnostics(runId, elapsedMs, distance)
                    computeAndStoreSplits(runId)
                }
                database.runStateDao().clear()
            }
        }
        lastFinishedRunId = runId
        stopForeground(0)
        stopSelf()
    }

    /** Fetched by MainActivity after finish to show the run summary. */
    suspend fun fetchLastRunSummary(): RunEntity? =
        lastFinishedRunId.takeIf { it > 0L }?.let { database.runDao().getRunById(it) }
    suspend fun fetchLastRunDiagnostic(): RunDiagnostic? =
        lastFinishedRunId.takeIf { it > 0L }?.let {
            database.runDiagnosticDao().getDiagnosticByRunId(it)
        }

    private suspend fun computeAndStoreSplits(runId: Long) {
        val points = database.gpsDao().getGpsForRun(runId).map {
            GpsPoint(it.latitude, it.longitude, it.altitudeMeters, it.timestamp)
        }
        val splits = SplitCalculator.computeSplits(points, prefs.splitLengthMeters)
        if (splits.isNotEmpty()) {
            database.splitDao().insertSplits(splits.map {
                SplitEntity(
                    runId = runId,
                    splitNumber = it.splitNumber,
                    distanceMeters = it.distanceMeters,
                    durationSeconds = it.durationMs / 1000L,
                    averagePaceSecondsPerMeter = it.averagePaceSecondsPerMeter,
                    averageSpeedMetersPerSecond = it.averageSpeedMetersPerSecond,
                    elevationChangeMeters = it.elevationChangeMeters,
                )
            })
        }
    }

    private suspend fun recordDiagnostics(runId: Long, durationMs: Long, fusedDistanceMeters: Double) {
        val benchEnabled = prefs.batteryBenchmarkEnabled
        val batteryStart = if (benchEnabled) batteryStartPct else null
        val batteryEnd = if (benchEnabled) getBatteryLevel() else null
        // Compute battery consumed percent: (start - end), clamped to 0-100
        val batteryConsumed = if (batteryStart != null && batteryEnd != null) {
            (batteryStart - batteryEnd).coerceAtLeast(0).coerceAtMost(100)
        } else 0
        val batteryDurationMs = durationMs
        val gpsCount = gpsFixCount
        val sensorCount = sensorFixCount
        val fusedVsGpsDelta = rawGpsDistanceMeters - fusedDistanceMeters
        val maxAccuracy = if (gpsFixCount > 0) maxAccuracyMetersSeen else 0f
        val accuracyRating = when {
            gpsFixCount == 0 -> null
            maxAccuracy <= prefs.ratingExcellentMeters -> 5
            maxAccuracy <= prefs.ratingGoodMeters -> 4
            maxAccuracy <= prefs.ratingFairMeters -> 3
            maxAccuracy <= prefs.ratingPoorMeters -> 2
            else -> 1
        }

        val diagnostic = RunDiagnostic(
            runId = runId,
            batteryStartPercent = batteryStart,
            batteryEndPercent = batteryEnd,
            batteryConsumedPercent = batteryConsumed,
            batteryDurationMs = batteryDurationMs,
            gpsFixCount = gpsCount,
            sensorFixCount = sensorCount,
            fusedVsGpsDeltaMeters = fusedVsGpsDelta,
            maxAccuracyMeters = maxAccuracy,
            accuracyRating = accuracyRating
        )

        database.runDiagnosticDao().insertDiagnostic(diagnostic)
    }

    private fun discardRun() {
        isRunning = false
        isPaused = false
        stopLocationUpdates()
        stopSensorUpdates()
        serviceScope.launch { database.runStateDao().clear() }
        stopForeground(0)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopLocationUpdates()
        stopSensorUpdates()
    }
}