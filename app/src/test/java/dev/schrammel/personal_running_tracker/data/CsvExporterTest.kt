package dev.schrammel.personal_running_tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.schrammel.personal_running_tracker.model.RunRecord

class CsvExporterTest {

    private val exporter = CsvExporter()

    @Test
    fun `exports header row first`() {
        val csv = exporter.exportRuns(emptyList())
        val lines = csv.split("\n")
        assertTrue(lines.first().startsWith("id,start_timestamp,end_timestamp"))
    }

    @Test
    fun `exports run data rows`() {
        val runs = listOf(
            createRun(id = 1L, distanceMeters = 5000.0, durationSeconds = 1800L),
            createRun(id = 2L, distanceMeters = 10000.0, durationSeconds = 3600L),
        )
        val csv = exporter.exportRuns(runs)
        val lines = csv.trim().split("\n")
        assertEquals(3, lines.size) // header + 2 runs
        assertTrue(lines[1].contains("1,"))
        assertTrue(lines[2].contains("2,"))
    }

    @Test
    fun `includes all run summary fields`() {
        val run = createRun(
            id = 42L,
            distanceMeters = 7500.0,
            durationSeconds = 2700L,
            movingDurationSeconds = 2400L,
            averagePace = 360.0,
            averageSpeed = 2.7778,
            elevationGain = 45.0,
            elevationLoss = 30.0,
            stepCount = 4500L,
            trackingProfile = "balanced",
            userAdjusted = true,
            notes = "Morning run",
        )
        val csv = exporter.exportRuns(listOf(run))
        val row = csv.trim().split("\n")[1]
        assertTrue(row.contains("42"))
        assertTrue(row.contains("7500.0"))
        assertTrue(row.contains("2700"))
        assertTrue(row.contains("2400"))
        assertTrue(row.contains("360.0"))
        assertTrue(row.contains("2.7778"))
        assertTrue(row.contains("45.0"))
        assertTrue(row.contains("30.0"))
        assertTrue(row.contains("4500"))
        assertTrue(row.contains("balanced"))
        assertTrue(row.contains("true"))
        assertTrue(row.contains("Morning run"))
    }

    @Test
    fun `quotes fields containing commas`() {
        val run = createRun(notes = "Ran, walked, rested")
        val csv = exporter.exportRuns(listOf(run))
        val row = csv.trim().split("\n")[1]
        assertTrue(row.contains("\"Ran, walked, rested\""))
    }

    @Test
    fun `escapes quotes in notes`() {
        val run = createRun(notes = "He said \"hello\"")
        val csv = exporter.exportRuns(listOf(run))
        val row = csv.trim().split("\n")[1]
        assertTrue(row.contains("\"\"hello\"\""))
    }

    @Test
    fun `empty runs produces header only`() {
        val csv = exporter.exportRuns(emptyList())
        val lines = csv.trim().split("\n")
        assertEquals(1, lines.size) // only header
    }

    @Test
    fun `csv is deterministic for same input`() {
        val runs = listOf(createRun(id = 1L), createRun(id = 2L))
        val csv1 = exporter.exportRuns(runs)
        val csv2 = exporter.exportRuns(runs)
        assertEquals(csv1, csv2)
    }

    private fun createRun(
        id: Long = 1L,
        distanceMeters: Double = 1000.0,
        durationSeconds: Long = 600L,
        movingDurationSeconds: Long = 550L,
        averagePace: Double = 600.0,
        averageSpeed: Double = 1.6667,
        elevationGain: Double = 10.0,
        elevationLoss: Double = 5.0,
        stepCount: Long = 600L,
        trackingProfile: String = "balanced",
        userAdjusted: Boolean = false,
        notes: String = "",
    ): RunRecord = RunRecord(
        id = id,
        startTimestamp = 1000000L,
        endTimestamp = 1000000L + durationSeconds * 1000,
        elapsedDurationSeconds = durationSeconds,
        movingDurationSeconds = movingDurationSeconds,
        pausedDurationSeconds = 0L,
        distanceMeters = distanceMeters,
        originalDistanceMeters = distanceMeters,
        averagePaceSecondsPerMeter = averagePace,
        averageSpeedMetersPerSecond = averageSpeed,
        elevationGainMeters = elevationGain,
        elevationLossMeters = elevationLoss,
        stepCount = stepCount,
        trackingProfile = trackingProfile,
        trackingAlgorithmVersion = "1.0",
        userAdjusted = userAdjusted,
        notes = notes,
        createdAt = 1000000L,
        modifiedAt = 1000000L,
    )
}