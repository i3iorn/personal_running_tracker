package dev.schrammel.personal_running_tracker.domain.units

/**
 * Unit conversion for displays (spec §3.2). Preferences store
 * "KILOMETERS"/"MILES" and "MIN_PER_KM"/"MIN_PER_MILE".
 * Pure Kotlin, deterministic, unit-testable.
 */
object Units {

    private const val METERS_PER_MILE = 1609.344

    fun isMetricDistance(unit: String): Boolean = unit != "MILES"

    /** Distance in the configured unit from meters. */
    fun distance(meters: Double, distanceUnit: String): Double =
        if (isMetricDistance(distanceUnit)) meters / 1000.0 else meters / METERS_PER_MILE

    fun distanceLabel(distanceUnit: String): String =
        if (isMetricDistance(distanceUnit)) "km" else "mi"

    /** Speed in the distance unit per hour from m/s. */
    fun speed(mps: Double, distanceUnit: String): Double =
        if (isMetricDistance(distanceUnit)) mps * 3.6 else mps * 3600.0 / METERS_PER_MILE

    fun speedLabel(distanceUnit: String): String =
        if (isMetricDistance(distanceUnit)) "km/h" else "mph"

    /** Pace in seconds per distance unit from seconds-per-meter. */
    fun paceSecondsPerUnit(secondsPerMeter: Double, paceUnit: String): Double =
        if (paceUnit != "MIN_PER_MILE") secondsPerMeter * 1000.0 else secondsPerMeter * METERS_PER_MILE

    fun paceLabel(paceUnit: String): String =
        if (paceUnit != "MIN_PER_MILE") "/km" else "/mi"

    /** "m:ss" formatting for a pace in seconds per unit; negative values clamp to 0:00. */
    fun paceText(secondsPerMeter: Double, paceUnit: String): String {
        val s = paceSecondsPerUnit(secondsPerMeter, paceUnit).coerceAtLeast(0.0)
        return "%d:%02d".format((s / 60).toInt(), (s % 60).toInt())
    }
}
