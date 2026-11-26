package dev.schrammel.personal_running_tracker.domain.sensor

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure spherical-earth geometry helpers. Replaces ad-hoc inline math and
 * provides the projection used by the sensor fusion layer to place
 * sensor-estimated positions on the globe.
 */
object GeoMath {
    private const val EARTH_RADIUS_METERS = 6_378_137.0
    private const val METERS_PER_DEGREE_LAT = 111_195.0

    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * asin(sqrt(a))
        return (EARTH_RADIUS_METERS * c).coerceAtLeast(0.0)
    }

    /** Initial bearing from point 1 to point 2, degrees clockwise from north, in [0,360). */
    fun bearingDegrees(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val dLng = Math.toRadians(lng2 - lng1)
        val y = sin(dLng) * cos(lat2R)
        val x = cos(lat1R) * sin(lat2R) - sin(lat1R) * cos(lat2R) * cos(dLng)
        val bearing = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        return bearing
    }

    /** Offset a lat/lng point by meters north and east. Returns (lat, lng). */
    fun offsetPointMeters(
        latitude: Double,
        longitude: Double,
        metersNorth: Double,
        metersEast: Double,
    ): Pair<Double, Double> {
        val latDelta = metersNorth / METERS_PER_DEGREE_LAT
        val lngDelta = metersEast /
            (METERS_PER_DEGREE_LAT * cos(Math.toRadians(latitude)).coerceAtLeast(0.001))
        return Pair(latitude + latDelta, longitude + lngDelta)
    }
}