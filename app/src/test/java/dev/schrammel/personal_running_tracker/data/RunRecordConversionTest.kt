package dev.schrammel.personal_running_tracker.data

import dev.schrammel.personal_running_tracker.model.RunRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class RunRecordConversionTest {

    private val record = RunRecord(
        id = 7L, startTimestamp = 1_700_000_000_000L, endTimestamp = 1_700_000_060_000L,
        elapsedDurationSeconds = 60, movingDurationSeconds = 55, pausedDurationSeconds = 5,
        distanceMeters = 250.0, originalDistanceMeters = 200.0,
        averagePaceSecondsPerMeter = 0.24, averageSpeedMetersPerSecond = 4.16,
        elevationGainMeters = 12.0, elevationLossMeters = 3.0, stepCount = 320,
        trackingProfile = "Balanced", trackingAlgorithmVersion = "1.0.0",
        userAdjusted = true, notes = "Morning run", createdAt = 1L, modifiedAt = 2L,
    )

    @Test
    fun `record to entity to record round trip preserves all fields`() {
        val entity = record.toEntity()
        assertEquals(record.id, entity.id)
        assertEquals(record.distanceMeters, entity.distanceMeters, 0.01)
        assertEquals(record.originalDistanceMeters, entity.originalDistanceMeters, 0.01)
        assertEquals(record.userAdjusted, entity.userAdjusted)
        assertEquals(record.notes, entity.notes)
        val back = entity.toRecord()
        assertEquals(record, back)
    }
}
