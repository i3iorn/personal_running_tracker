package dev.schrammel.personal_running_tracker.tracking

import android.location.Location

data class RunState(
    val startTimeMs: Long = System.currentTimeMillis(),
    val elapsedMs: Long = 0L,
    val movingMs: Long = 0L,
    val distanceMeters: Double = 0.0,
    val currentSpeedMps: Double = 0.0,
    val currentPaceSpm: Double = 0.0,
    val trackingProfile: String = "Balanced",
    val algorithmVersion: String = "1.0.0",
    var isRunning: Boolean = false,
    var isPaused: Boolean = false,
    var lastLocation: Location? = null,
    var lastFixTimeMs: Long = 0L,
    var lastDistanceMeters: Double = 0.0
)