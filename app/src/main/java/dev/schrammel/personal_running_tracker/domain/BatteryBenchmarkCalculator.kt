/** Battery consumption benchmarking (Phase 6 item 1).
 *
 * Records battery level at run start/end and computes percent consumed per
 * hour of tracked time. All logic is pure Kotlin; Android battery APIs feed
 * in from TrackingService. When the feature is removed, delete this file,
 * the RunDiagnostic table, and the preference entries.
 */
object BatteryBenchmarkCalculator {
    /** Battery percent consumed over the run duration (startPct - endPct). */
    fun consumedPercent(startPct: Int?, endPct: Int?): Int? {
        if (startPct == null || endPct == null) return null
        return (startPct - endPct).coerceAtLeast(0).coerceAtMost(100)
    }

    /** Battery percent per hour of tracked time. */
    fun consumedPercentPerHour(
        startPct: Int?,
        endPct: Int?,
        durationMs: Long,
    ): Double? {
        val consumed = consumedPercent(startPct, endPct) ?: return null
        val hours = durationMs / 3_600_000.0
        return if (hours > 0.0) consumed / hours else 0.0
    }
}