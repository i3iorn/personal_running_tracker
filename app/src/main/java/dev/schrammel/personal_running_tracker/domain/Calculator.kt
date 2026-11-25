package dev.schrammel.personal_running_tracker.domain

object Calculator {
    fun metersToKilometers(meters: Double): Double = meters / 1000.0
    fun kilometersToMeters(kilometers: Double): Double = kilometers * 1000.0
    fun mpsToKmh(mps: Double): Double = mps * 3.6
    fun mpsToMinutesPerKilometer(mps: Double): Double = (60.0 / mps).coerceAtLeast(0.0)
    fun mpsToMinutesPerMile(mps: Double): Double = ((60.0 * 1609.344) / mps).coerceAtLeast(0.0)
    fun secondsFromDistanceAndPace(distanceMeters: Double, paceSecondsPerMeter: Double): Double =
        distanceMeters * paceSecondsPerMeter
}
