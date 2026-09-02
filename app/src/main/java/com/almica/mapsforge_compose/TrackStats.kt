package com.almica.mapsforge_compose

import android.location.Location
import org.mapsforge.core.model.LatLong

data class TourStatistics(
    val totalDistanceKm: Double = 0.0,
    val currentSpeedKmh: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val currentAltitudeMeters: Double = 0.0
)

object TrackStatsCalculator {
    fun calculateDistanceKm(p1: LatLong, p2: LatLong): Double {
        val results = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
        return results[0].toDouble() / 1000.0
    }

    fun calculateStats(points: List<RoutePoint>): TourStatistics {
        if (points.isEmpty()) return TourStatistics()
        var totalDistance = 0.0
        var elevationGain = 0.0
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            totalDistance += calculateDistanceKm(
                LatLong(p1.latitude, p1.longitude),
                LatLong(p2.latitude, p2.longitude)
            )
            val altDiff = p2.altitude - p1.altitude
            if (altDiff > 0.4) { // Small threshold to filter noise
                elevationGain += altDiff
            }
        }
        return TourStatistics(
            totalDistanceKm = totalDistance,
            elevationGainMeters = elevationGain,
            currentAltitudeMeters = points.last().altitude
        )
    }
}
