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
import dev.schrammel.personal_running_tracker.data.RunEvent
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
        const val ACTION_MARK_RUN = "action_mark_run"
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
    private val pendingGps = mutableListOf<GpsEntity>()
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var markerCount: Int = 0
    @Volatile private var currentBatteryPct: Int? = null
    // Run/walk classification (ultra review #5)
    private var accumRunMs: Long = 0L
    private var accumWalkMs: Long = 0L
    private var runSegmentMs: Long = 0L
    private var walkSegmentMs: Long = 0L
    private val recentSpeeds = ArrayDeque<Pair<Long, Float>>() // (timeMs, speedMps)
    @Volatile private var lastFinishedRunId: Long = 0L
    // Step history for live speed/movement between GPS fixes (indoor walking fix)
    private val stepHistory = ArrayDeque<Pair<Long, Long>>() // (timeMs, cumulativeTotalSteps)
    private var lastStepCountAtLastFix: Long = 0L

    // Public API for MainActivity to query live state
    fun isRunningFlag(): Boolean = isRunning
    fun isPausedFlag(): Boolean = isPaused
    fun currentElapsedMs(): Long = accumElapsedMs + (if (isPaused) 0L else System.currentTimeMillis() - segmentStartMs)
    fun currentDistanceMeters(): Double = baseDistanceMeters + (fusion?.totalDistanceMeters ?: 0.0)
    fun currentAccuracyMeters(): Float? = lastAccuracyMeters

    /** Live movement state for the live display: "RUN", "WALK" or null (unknown/stationary). */
    fun currentMovementState(): String? = when {
        isPaused -> "PAUSED"
        !isRunning -> null
        else -> {
            val info = currentSpeedInfo()
            val speed = info.first
            val fromGps = info.second
            if (speed == null) return null
            val stepsGrew = stepCount > lastStepCountAtLastFix
            when {
                speed >= prefs.runningSpeedThresholdMps -> "RUN"
                else -> if (stepsGrew) "WALK" else "STATIONARY"
            }
        }
    }

    /** Live battery percent, refreshed on the persist loop (off main thread). */
    fun currentBatteryPercent(): Int? = currentBatteryPct

    /** Battery drain per hour over the run so far; negative = draining. */
    fun batteryRatePerHour(): Int? {
        val start = batteryStartPct ?: return null
        val now = currentBatteryPct ?: return null
        val elapsedH = currentElapsedMs() / 3_600_000.0
        if (elapsedH < 0.25) return null  // too early for a stable rate
        return (((start - now) / elapsedH)).toInt()
    }

    /** Smoothed current speed (m/s). Falls back to a step-derived estimate so
     * walking is visible live between GPS fixes and indoors where GPS is poor.
     * The second value reports whether the figure came from GPS (true) or the
     * step sensor (false), so the UI can skip the accuracy gate for sensors. */
    fun currentSpeedInfo(): Pair<Double?, Boolean> {
        val gps = smoothedGpsSpeedMps()
        if (gps != null) return gps to true
        val step = stepSpeedMps()
        return step to false
    }

    /** Back-compat convenience wrapper. */
    fun currentSmoothedSpeedMps(): Double? = currentSpeedInfo().first

    private fun smoothedGpsSpeedMps(): Double? {
        val effWindow = maxOf(prefs.paceSmoothingWindowMs, 3 * lastFixGapMs + 1_000L)
        val minSpeed = prefs.paceMinSpeedMps.toDouble()
        val cutoff = System.currentTimeMillis() - effWindow
        recentSpeeds.removeAll { it.first < cutoff }
        while (recentSpeeds.size > 30) recentSpeeds.removeFirst()
        if (recentSpeeds.isEmpty()) return null
        val mean = recentSpeeds.map { it.second }.average()
        return if (mean >= minSpeed) mean else null
    }

    /** Walking/running speed estimated from recent step cadence × stride length.
     * Returns null when there is not enough recent step activity. */
    private fun stepSpeedMps(): Double? {
        val now = System.currentTimeMillis()
        val cutoff = now - 6_000L
        while (stepHistory.isNotEmpty() && stepHistory.first().first < cutoff) stepHistory.removeFirst()
        if (stepHistory.size < 2) return null
        val entries = stepHistory.toList()
        val first = entries[0]
        val last = entries[entries.size - 1]
        val dtSec = (last.first - first.first) / 1000.0
        if (dtSec <= 0.5) return null
        val stepsPerSec = (last.second - first.second) / dtSec
        if (stepsPerSec <= 0.1) return null
        return stepsPerSec * prefs.tuningStrideLengthMeters
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
            // Persist tracking state + flush queued GPS fixes on the configured cadence
            while (true) {
                kotlinx.coroutines.delay(prefs.statePersistIntervalMs.coerceAtLeast(1_000L))
                if (isRunning) {
                    currentBatteryPct = getBatteryLevel()
                    maybeAutoSwitchToSaver()
                    persistState()
                    flushPendingGps()
                }
            }
        }
    }

    /** Low battery strategy: auto-switch to Saver to stretch the remaining charge. */
    private suspend fun maybeAutoSwitchToSaver() {
        val level = currentBatteryPct ?: return
        if (level <= prefs.lowBatteryThresholdPercent && trackingProfile != "Saver" && isRunning && !isPaused) {
            trackingProfile = "Saver"
            prefs.trackingProfile = "Saver"
            stopLocationUpdates()
            startLocationUpdates(trackingProfile)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(
                1,
                NotificationCompat.Builder(this, "tracking_service_channel")
                    .setContentTitle("Personal Running Tracker")
                    .setContentText("Low battery ($level%) — switched to Battery Saver")
                    .setSmallIcon(dev.schrammel.personal_running_tracker.R.drawable.ic_runner)
                    .setOngoing(true)
                    .build()
            )
        }
    }

    /** Batch GPS inserts (ultra review #4): fewer transactions, less disk wakeups. */
    private suspend fun flushPendingGps() {
        val batch = synchronized(pendingGps) {
            val copy = pendingGps.toList()
            pendingGps.clear()
            copy
        }
        if (batch.isNotEmpty()) {
            database.withTransaction {
                batch.forEach { database.gpsDao().insertGps(it) }
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
        baseDistanceMeters = state.distanceMeters
        stepCount = state.stepCount
        trackingProfile = state.trackingProfile
        batteryStartPct = state.batteryStartPct
        movingSegmentMs = prefs.runStateMovingSegmentMs
        accumRunMs = prefs.runStateRunMs
        accumWalkMs = prefs.runStateWalkMs
        runSegmentMs = 0L
        walkSegmentMs = 0L
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
        prefs.runStateRunMs = accumRunMs + runSegmentMs
        prefs.runStateWalkMs = accumWalkMs + walkSegmentMs
        database.runStateDao().upsert(
            RunStateEntity(
                runId = runId, isRunning = isRunning, isPaused = isPaused,
                accumElapsedMs = accumElapsedMs, accumMovingMs = accumMovingMs,
                segmentStartMs = segmentStartMs, distanceMeters = currentDistanceMeters(),
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
            ACTION_MARK_RUN -> { markRun(); START_NOT_STICKY }
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
            accumRunMs = 0L
            accumWalkMs = 0L
            runSegmentMs = 0L
            walkSegmentMs = 0L
            markerCount = 0
            segmentStartMs = System.currentTimeMillis()
            lastLat = null
            lastLng = null
            runId = System.currentTimeMillis()
            baseDistanceMeters = 0.0
            rawGpsDistanceMeters = 0.0
            lastStepCountAtLastFix = stepCount
            // Fresh fusion instance per run: GPS anchors reconcile with sensor estimates
            fusion = SensorFusion(
                maxGpsSegmentRatio = prefs.tuningSensorFusionMaxGpsSegmentRatio,
                uncertaintyPerStepMeters = 5.0,
            )
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
        // Record step for live speed/movement detection between GPS fixes
        val now = System.currentTimeMillis()
        stepHistory.addLast(now to totalSteps)
        while (stepHistory.size > 200) stepHistory.removeFirst()
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
        val segment = if (prevLat != null && prevLng != null) haversineMeters(prevLat, prevLng, lat, lng) else 0.0
        val accuracy = location.accuracy.coerceAtLeast(0f)
        // Outlier rejection: a wild inaccurate fix must not poison the route,
        // distance, splits or map. Drop it entirely and keep the previous anchor.
        if (dev.schrammel.personal_running_tracker.domain.gps.FixPlausibility.isImplausible(
                segment, gapMs, accuracy)
        ) {
            return
        }
        lastFixTimeMs = timeMs
        lastFixGapMs = gapMs
        rawGpsDistanceMeters += segment
        if (accuracy > maxAccuracyMetersSeen) maxAccuracyMetersSeen = accuracy
        lastAccuracyMeters = accuracy
        // Speed: trust the GPS-reported value when present, otherwise derive it
        // from the segment distance/time (reported speed is often 0 on network fixes).
        val reported = location.speed.coerceAtLeast(0f)
        val segmentSpeed = if (prevLat != null && gapMs > 0) {
            (segment / (gapMs / 1000.0)).toFloat()
        } else reported
        val effectiveSpeed = if (reported > 0.5f) reported else segmentSpeed.coerceAtLeast(0f)
        recentSpeeds.addLast(timeMs to effectiveSpeed)

        // Sensor fusion: reconcile this anchor with the pending sensor estimate.
        // Never double counts: the pending sensor distance is replaced by the
        // verified GPS segment (or kept when GPS looks glitchy).
        val f = fusion
        if (f != null) {
            f.onGpsAnchor(
                GpsAnchor(
                    latitude = lat, longitude = lng,
                    altitudeMeters = location.altitude, timestampMs = timeMs,
                    accuracyMeters = accuracy, speedMps = effectiveSpeed,
                    bearingDegrees = location.bearing,
                )
            )
        }

        // Auto-pause: when enabled, stationary periods do not accumulate moving
        // time (spec §10). Run vs walk is classified separately for ultra efforts.
        // Steps are a reliable movement signal even when GPS looks stationary indoors.
        val stationaryThresholdMps = 0.2 + prefs.autoPauseSensitivity * 0.03
        val topSpeedPauseMps = prefs.runSpeedPauseThresholdKmh.toDouble() / 3.6
        val stepsGrew = stepCount > lastStepCountAtLastFix
        lastStepCountAtLastFix = stepCount
        val moving = !isPaused && gapMs > 0 && effectiveSpeed <= topSpeedPauseMps &&
            (!prefs.autoPauseEnabled || effectiveSpeed >= stationaryThresholdMps || stepsGrew)
        if (moving) {
            if (effectiveSpeed >= prefs.runningSpeedThresholdMps) runSegmentMs += gapMs
            else walkSegmentMs += gapMs
        }
        // Partial wake lock while actually moving; released when stationary/paused (ultra #13)
        updateWakeLock(moving)
        if (isRunning) updateNotificationText(accuracy)
        lastLat = lat
        lastLng = lng

        // Queue the fix; persisted in batches on the persist loop (ultra #4)
        synchronized(pendingGps) {
            pendingGps.add(
                GpsEntity(
                    runId = runId,
                    timestamp = timeMs,
                    latitude = lat,
                    longitude = lng,
                    altitudeMeters = location.altitude.toDouble(),
                    horizontalAccuracyMeters = accuracy,
                    verticalAccuracyMeters = 0f,
                    speedMetersPerSecond = effectiveSpeed,
                    bearingDegrees = location.bearing.coerceAtLeast(0.0f),
                    provider = location.provider ?: "unknown",
                    measurementQuality = 1
                )
            )
        }
        gpsFixCount++
    }

    private fun updateWakeLock(needed: Boolean) {
        val wm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (needed && wakeLock == null) {
            wakeLock = wm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "prt:moving").apply {
                setReferenceCounted(false)
                acquire(30 * 60 * 1000L) // safety cap; re-acquired on next moving fix
            }
        } else if (!needed && wakeLock != null) {
            wakeLock?.release()
            wakeLock = null
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
        // Run/walk times are accumulated per fix in onLocationChanged (auto-pause aware)
        accumRunMs += runSegmentMs
        accumWalkMs += walkSegmentMs
        runSegmentMs = 0
        walkSegmentMs = 0
        accumMovingMs += movingSegmentMs
        movingSegmentMs = 0
        updateWakeLock(false)
        isPaused = true
        stopLocationUpdates()
        stopSensorUpdates()
    }

    private fun resumeRun() {
        if (!isRunning || !isPaused) return
        isPaused = false
        runSegmentMs = 0L
        walkSegmentMs = 0L
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
        val runMs = accumRunMs + runSegmentMs
        val walkMs = accumWalkMs + walkSegmentMs
        isRunning = false
        isPaused = false
        updateWakeLock(false)
        stopLocationUpdates()
        stopSensorUpdates()
        val distance = currentDistanceMeters()
        if (runId > 0L) {
            serviceScope.launch {
                // Flush queued fixes first: elevation, splits and PR cache read from the DB
                flushPendingGps()
                val gpsRows = database.gpsDao().getGpsForRun(runId)
                val (gain, loss) = dev.schrammel.personal_running_tracker.domain.elevation.ElevationCalculator
                    .totals(gpsRows.map { it.altitudeMeters })
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
                        elevationGainMeters = gain,
                        elevationLossMeters = loss,
                        stepCount = stepCount,
                        trackingProfile = trackingProfile,
                        trackingAlgorithmVersion = "1.0.0",
                        userAdjusted = false,
                        notes = "",
                        runningDurationSeconds = runMs / 1000L,
                        walkingDurationSeconds = walkMs / 1000L,
                        createdAt = now,
                        modifiedAt = now
                    )
                    )
                    recordDiagnostics(runId, elapsedMs, distance)
                    computeAndStoreSplits(runId)
                    updatePersonalRecordCache(runId)
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

    /** Incremental PR cache update: keep the best time per configured distance. */
    private suspend fun updatePersonalRecordCache(runId: Long) {
        val targets = prefs.prDistanceKmList.split(",")
            .mapNotNull { it.trim().toDoubleOrNull() }.filter { it > 0 }.map { it * 1000.0 }
        val points = database.gpsDao().getGpsForRun(runId).map {
            dev.schrammel.personal_running_tracker.domain.split.GpsPoint(
                it.latitude, it.longitude, it.altitudeMeters, it.timestamp)
        }
        for (target in targets) {
            val pr = dev.schrammel.personal_running_tracker.domain.stats.StatisticsEngine
                .bestTimeForDistance(points, target, runId) ?: continue
            val current = database.personalRecordCacheDao().get(target)
            if (current == null || pr.bestSeconds < current.bestSeconds) {
                database.personalRecordCacheDao().upsert(
                    dev.schrammel.personal_running_tracker.data.PersonalRecordCache(
                        distanceMeters = target, bestSeconds = pr.bestSeconds,
                        runId = runId, updatedAt = System.currentTimeMillis())
                )
            }
        }
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
        synchronized(pendingGps) { pendingGps.clear() }  // discarded run must not persist queued fixes
        stopLocationUpdates()
        stopSensorUpdates()
        updateWakeLock(false)
        serviceScope.launch { database.runStateDao().clear() }
        stopForeground(0)
        stopSelf()
    }

    /** Mid-run marker (aid station, crew stop) — recorded without pausing (ultra #9). */
    private fun markRun() {
        if (!isRunning) return
        markerCount++
        val now = System.currentTimeMillis()
        val eventId = runId
        serviceScope.launch {
            database.runEventDao().insert(
                RunEvent(
                    runId = eventId, timestamp = now,
                    label = "Marker $markerCount", type = "MARKER",
                )
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        updateWakeLock(false)
        stopLocationUpdates()
        stopSensorUpdates()
    }
}