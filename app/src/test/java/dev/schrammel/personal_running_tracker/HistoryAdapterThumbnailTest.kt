package dev.schrammel.personal_running_tracker

import android.graphics.Bitmap
import dev.schrammel.personal_running_tracker.data.GpsEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryAdapterThumbnailTest {

    private fun point(lat: Double, lng: Double) = GpsEntity(
        runId = 1, timestamp = 0, latitude = lat, longitude = lng,
        altitudeMeters = 0.0, horizontalAccuracyMeters = 5f, verticalAccuracyMeters = 0f,
        speedMetersPerSecond = 3f, bearingDegrees = 0f, provider = "gps", measurementQuality = 1,
    )

    @Test
    fun `thumbnail has the requested size`() {
        val bmp = HistoryAdapter.renderThumbnail(listOf(point(47.0, 11.0), point(47.01, 11.01)), 104)
        assertEquals(104, bmp.width)
        assertEquals(104, bmp.height)
    }

    @Test
    fun `fewer than two points still yields a placeholder bitmap`() {
        val bmp = HistoryAdapter.renderThumbnail(listOf(point(47.0, 11.0)), 52)
        assertEquals(52, bmp.width)
    }
}
