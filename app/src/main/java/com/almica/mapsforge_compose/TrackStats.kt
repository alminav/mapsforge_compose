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
}
