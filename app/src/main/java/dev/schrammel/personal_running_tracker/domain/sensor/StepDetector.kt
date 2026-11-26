package dev.schrammel.personal_running_tracker.domain.sensor

/**
 * Software step detector operating on accelerometer magnitude samples.
 *
 * This is a fallback for devices without a hardware step counter. It uses a
 * light dynamic-baseline peak detector rather than a fixed threshold so that
 * moderate device orientation changes and signal offset do not create false
 * steps. Hardware step counting is preferred when available.
 *
 * Pure Kotlin: no Android dependencies, deterministic and unit-testable.
 */
class StepDetector(
    private val upperDelta: Double = 1.0,
    private val baselineAlpha: Double = 0.1,
    private val refractoryMs: Long = 300L,
) {
    private var baseline: Double = NOMINAL_GRAVITY
    private var wasAboveUpper: Boolean = false
    private var lastStepTimeMs: Long? = null

    /**
     * Feed one accelerometer magnitude sample.
     * @return true if this sample completes a step.
     */
    fun onAccelerometerMagnitude(magnitude: Double, timestampMs: Long): Boolean {
        val upperThreshold = baseline + upperDelta
        val aboveUpper = magnitude > upperThreshold
        val risingEdge = aboveUpper && !wasAboveUpper
        val elapsedOk = lastStepTimeMs?.let { timestampMs - it >= refractoryMs } ?: true
        val step = andResetIfStep(risingEdge, aboveUpper, elapsedOk, timestampMs)
        // Update baseline after the decision so the current peak is measured
        // against the previous baseline, not itself.
        baseline += baselineAlpha * (magnitude - baseline)
        wasAboveUpper = aboveUpper
        return step
    }

    private fun andResetIfStep(
        risingEdge: Boolean,
        aboveUpper: Boolean,
        elapsedOk: Boolean,
        timestampMs: Long,
    ): Boolean {
        // Count a step on a rising edge, or when a sustained peak follows the
        // refractory period (allows step counting on a continuous high signal).
        val isStep = (risingEdge && elapsedOk) || (aboveUpper && elapsedOk && lastStepTimeMs != null)
        if (isStep) lastStepTimeMs = timestampMs
        return isStep
    }

    companion object {
        const val NOMINAL_GRAVITY = 9.81
    }
}