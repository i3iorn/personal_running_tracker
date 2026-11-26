package dev.schrammel.personal_running_tracker.domain.elevation

/**
 * Elevation gain/loss from GPS altitude traces (spec §6.1).
 * GPS altitude is noisy: altitudes are smoothed with a moving average and only
 * changes exceeding [thresholdMeters] count toward gain/loss, which suppresses
 * the constant ±1 m flutter of raw fixes. Pure Kotlin.
 */
object ElevationCalculator {

    fun totals(
        altitudesMeters: List<Double>,
        smoothingWindow: Int = 5,
        thresholdMeters: Double = 2.0,
    ): Pair<Double, Double> {
        if (altitudesMeters.size < 2) return 0.0 to 0.0
        val smoothed = altitudesMeters.mapIndexed { i, _ ->
            val from = maxOf(0, i - smoothingWindow + 1)
            altitudesMeters.subList(from, i + 1).average()
        }
        var gain = 0.0
        var loss = 0.0
        var reference = smoothed.first()
        for (a in smoothed.drop(1)) {
            val delta = a - reference
            if (delta >= thresholdMeters) {
                gain += delta
                reference = a
            } else if (delta <= -thresholdMeters) {
                loss += -delta
                reference = a
            }
        }
        return gain to loss
    }
}
