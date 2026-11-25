package dev.schrammel.personal_running_tracker.domain

enum class DistanceUnit(val label: String, val metersPerUnit: Double) {
    KILOMETERS("Kilometers", 1000.0),
    MILES("Miles", 1609.344);

    fun format(meters: Double): String = "$meters ${this.label.lowercase()}"
}

enum class PaceUnit(val label: String, val secondsPerMeter: Double) {
    MIN_PER_KM("min/km", 60.0),
    MIN_PER_MILE("min/mile", 1440.0);

    fun formatPace(secondsPerMeter: Double): String = "$secondsPerMeter ${this.label.lowercase()}"
}
