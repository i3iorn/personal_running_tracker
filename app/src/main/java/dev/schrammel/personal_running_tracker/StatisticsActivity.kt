package dev.schrammel.personal_running_tracker

import android.app.Application
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.schrammel.personal_running_tracker.data.AppDatabase
import dev.schrammel.personal_running_tracker.data.toRecord
import dev.schrammel.personal_running_tracker.domain.split.GpsPoint
import dev.schrammel.personal_running_tracker.domain.stats.Comparison
import dev.schrammel.personal_running_tracker.domain.stats.PersonalRecord
import dev.schrammel.personal_running_tracker.domain.stats.StatisticsEngine
import dev.schrammel.personal_running_tracker.domain.stats.Totals
import dev.schrammel.personal_running_tracker.model.TimeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Statistics section (spec §8): overview, comparison, rolling chart, personal records. */
class StatisticsActivity : AppCompatActivity() {

    private lateinit var overview: TextView
    private lateinit var comparisonText: TextView
    private lateinit var prText: TextView
    private var selectedRange: TimeRange = TimeRange.LAST_30_DAYS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)
        overview = findViewById(R.id.statsOverview)
        comparisonText = findViewById(R.id.statsComparison)
        prText = findViewById(R.id.prText)

        val ranges = listOf(
            TimeRange.LAST_7_DAYS, TimeRange.LAST_30_DAYS, TimeRange.LAST_90_DAYS,
            TimeRange.LAST_365_DAYS, TimeRange.CURRENT_WEEK, TimeRange.CURRENT_MONTH,
            TimeRange.CURRENT_YEAR, TimeRange.PREVIOUS_YEAR, TimeRange.ALL_TIME,
        )
        val spinner = findViewById<Spinner>(R.id.rangeSpinner)
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            ranges.map { it.label }
        )
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                selectedRange = ranges[pos]
                reload()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext as Application)
            val now = System.currentTimeMillis()
            // All aggregation happens off the main thread; only UI assignment below.
            val computed = withContext(Dispatchers.IO) {
                val allRuns = db.runDao().observeAllRuns().first().map { it.toRecord() }
                val filtered = StatisticsEngine.filterRuns(allRuns, selectedRange, now)
                val totals = StatisticsEngine.totals(filtered, now)

                val previous = previousWindowTotals(allRuns, selectedRange, now)
                val comparison = previous?.let { StatisticsEngine.compare(totals, it) }

                val rolling = StatisticsEngine.rollingDistance(allRuns, prefs().rollingWindowDays, now)
                val trends = StatisticsEngine.weeklyTrends(allRuns, prefs().chartWeeks, now)
                val distribution = StatisticsEngine.distanceDistribution(allRuns)

                ComputedStats(allRuns, totals, comparison,
                    rolling.reversed().map { it.second.toFloat() },
                    trends, distribution)
            }

            overview.text = formatTotals(computed.totals)
            comparisonText.text = computed.comparison?.let { formatComparison(it) } ?: ""

            val charts = findViewById<dev.schrammel.personal_running_tracker.view.BarChartView>(R.id.rollingChart)
            charts.setValues(computed.rolling)
            findViewById<dev.schrammel.personal_running_tracker.view.BarChartView>(R.id.weeklyDistanceChart)
                .setValues(computed.trends.map { it.second.toFloat() })
            val paceBars = computed.trends.map { it.third }.filter { it > 0 }
            val maxPace = paceBars.maxOrNull() ?: 1.0
            findViewById<dev.schrammel.personal_running_tracker.view.BarChartView>(R.id.weeklyPaceChart)
                .setValues(computed.trends.map { if (it.third > 0) (maxPace - it.third + maxPace * 0.1).toFloat() else 0f })
            findViewById<TextView>(R.id.distributionText).text =
                computed.distribution.joinToString("\n") { "%-10s %d".format(it.first, it.second) }

            prText.text = withContext(Dispatchers.IO) { computePersonalRecords(db) }
        }
    }

    private class ComputedStats(
        val allRuns: List<dev.schrammel.personal_running_tracker.model.RunRecord>,
        val totals: Totals,
        val comparison: Comparison?,
        val rolling: List<Float>,
        val trends: List<Triple<Long, Double, Double>>,
        val distribution: List<Pair<String, Int>>,
    )

    private fun prefs() = dev.schrammel.personal_running_tracker.settings.TrackingPreferences(this)

    /** Previous equal-length calendar window for comparison (spec §8.5). */
    private fun previousWindowTotals(
        allRuns: List<dev.schrammel.personal_running_tracker.model.RunRecord>,
        range: TimeRange,
        now: Long,
    ): Totals? {
        val day = 86_400_000L
        return when (range) {
            TimeRange.LAST_7_DAYS, TimeRange.LAST_30_DAYS, TimeRange.LAST_90_DAYS, TimeRange.LAST_365_DAYS -> {
                val len = when (range) {
                    TimeRange.LAST_7_DAYS -> 7 * day
                    TimeRange.LAST_30_DAYS -> 30 * day
                    TimeRange.LAST_90_DAYS -> 90 * day
                    else -> 365 * day
                }
                StatisticsEngine.totals(
                    allRuns.filter { it.startTimestamp in (now - 2 * len) until (now - len) }, now
                )
            }
            TimeRange.CURRENT_WEEK -> StatisticsEngine.totals(
                allRuns.filter { it.startTimestamp in ((now - 14 * day)) until (now - 7 * day) }, now
            )
            TimeRange.CURRENT_MONTH -> StatisticsEngine.totals(
                allRuns.filter { it.startTimestamp in ((now - 61 * day)) until (now - 30 * day) }, now
            )
            TimeRange.CURRENT_YEAR -> StatisticsEngine.totals(
                allRuns.filter { it.startTimestamp in ((now - 730 * day)) until (now - 365 * day) }, now
            )
            else -> null
        }
    }

    /** Best times over user-configurable distances, computed from raw GPS observations (spec §8.3). */
    private suspend fun computePersonalRecords(db: AppDatabase): String {
        val prefs = dev.schrammel.personal_running_tracker.settings.TrackingPreferences(this)
        val targets = prefs.prDistanceKmList.split(",")
            .mapNotNull { it.trim().toDoubleOrNull() }
            .filter { it > 0 }
            .map { it * 1000.0 }
        if (targets.isEmpty()) return "No PR distances configured"
        val best = mutableMapOf<Double, PersonalRecord>()
        val runs = db.runDao().observeAllRuns().first()
        for (run in runs) {
            val points = db.gpsDao().getGpsForRun(run.id).map {
                GpsPoint(it.latitude, it.longitude, it.altitudeMeters, it.timestamp)
            }
            for (target in targets) {
                val pr = StatisticsEngine.bestTimeForDistance(points, target, run.id)
                if (pr != null) {
                    val cur = best[target]
                    if (cur == null || pr.bestSeconds < cur.bestSeconds) best[target] = pr
                }
            }
        }
        if (best.isEmpty()) return "No GPS data yet"
        return targets.mapNotNull { t ->
            best[t]?.let { "%5.1f km  %d:%02d".format(t / 1000.0, it.bestSeconds / 60, it.bestSeconds % 60) }
        }.joinToString("\n")
    }

    private fun formatTotals(t: Totals): String {
        val prefs = dev.schrammel.personal_running_tracker.settings.TrackingPreferences(this)
        val units = dev.schrammel.personal_running_tracker.domain.units.Units
        fun hms(s: Long) = "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        fun dist(m: Double) = "%.2f %s".format(units.distance(m, prefs.distanceUnit), units.distanceLabel(prefs.distanceUnit))
        return buildString {
            append("Runs:            %d\n".format(t.runCount))
            append("Distance:        %s\n".format(dist(t.totalDistanceMeters)))
            append("Time:            %s\n".format(hms(t.totalElapsedSeconds)))
            append("Moving:          %s\n".format(hms(t.totalMovingSeconds)))
            append("Avg distance:    %s\n".format(dist(t.averageDistanceMeters)))
            append("Avg pace:        %s %s\n".format(
                units.paceText(t.averagePaceSecondsPerKm / 1000.0, prefs.paceUnit),
                units.paceLabel(prefs.paceUnit)))
            append("Avg speed:       %.1f %s\n".format(
                units.speed(t.averageSpeedMetersPerSecond, prefs.distanceUnit),
                units.speedLabel(prefs.distanceUnit)))
            append("Longest run:     %s\n".format(dist(t.longestRunMeters)))
            t.fastestRunPaceSecondsPerKm?.let {
                append("Fastest run:     %s %s\n".format(
                    units.paceText(it / 1000.0, prefs.paceUnit), units.paceLabel(prefs.paceUnit)))
            }
            append("Avg weekly:      %s\n".format(dist(t.averageWeeklyDistanceMeters)))
            append("Avg monthly:     %s".format(dist(t.averageMonthlyDistanceMeters)))
        }
    }

    private fun formatComparison(c: Comparison): String {
        fun pct(v: Double?): String = if (v == null) "n/a" else "%+.1f%%".format(v)
        return buildString {
            append("vs previous period\n")
            append("Distance: %+.1f km (%s)\n".format(c.absoluteDeltaDistanceMeters / 1000.0, pct(c.percentDeltaDistance)))
            append("Runs:     %+d (%s)\n".format(c.absoluteDeltaRuns, pct(c.percentDeltaRuns)))
            append("Time:     %+.0f min (%s)\n".format(c.absoluteDeltaSeconds / 60.0, pct(c.percentDeltaSeconds)))
            c.percentDeltaPace?.let { append("Pace:     %s (negative = faster)\n".format(pct(it))) }
            c.percentDeltaSpeed?.let { append("Speed:    %s".format(pct(it))) }
        }
    }
}
