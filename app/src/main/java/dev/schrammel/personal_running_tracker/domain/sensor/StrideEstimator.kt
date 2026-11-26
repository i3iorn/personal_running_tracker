package dev.schrammel.personal_running_tracker.domain.sensor

import kotlin.math.sqrt

/**
 * Estimates running stride length from calibrated GPS-vs-step observations.
 *
 * Pure Kotlin: no Android dependencies, fully unit-testable.
 */
class StrideEstimator(
    private val defaultStrideMeters: Double = 0.75,
) {
    private var calibratedStride: Double = defaultStrideMeters
    private val calibrationHistory = mutableListOf<Double>()

    fun strideLengthMeters(): Double = calibratedStride

    /**
     * Observe a calibration data point: distance between GPS anchors and steps counted.
     * Only accepts positive distance and positive step count.
     * Clamps result to [0.4, 2.5] m/step.
     */
    fun observeCalibration(distanceMeters: Double, steps: Long) {
        if (distanceMeters <= 0.0 || steps <= 0L) return
        val estimate = (distanceMeters / steps.toDouble()).coerceIn(MIN_STRIDE, MAX_STRIDE)
        calibrationHistory.add(estimate)
        if (calibrationHistory.size > MAX_HISTORY) calibrationHistory.removeAt(0)
        recompute()
    }

    private fun recompute() {
        // Linear weighting: most recent observation has the highest weight.
        val n = calibrationHistory.size
        if (n == 0) return
        var weightedSum = 0.0
        var weightSum = 0.0
        for (i in 0 until n) {
            val weight = 1.0 + i.toDouble() // most recent = n
            weightedSum += calibrationHistory[i] * weight
            weightSum += weight
        }
        calibratedStride = (weightedSum / weightSum).coerceIn(MIN_STRIDE, MAX_STRIDE)
    }

    companion object {
        const val MIN_STRIDE = 0.4
        const val MAX_STRIDE = 2.5
        const val MAX_HISTORY = 20
    }
}