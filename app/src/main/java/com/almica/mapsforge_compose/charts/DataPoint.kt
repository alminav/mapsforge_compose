package com.almica.mapsforge_compose.charts

data class DataPoint(
    val distanceKm: Float,
    val elevationMeters: Float,
    val latitude: Double,
    val longitude: Double,
    val time: Long = 0L,
    var speedKmPerHour: Float = 0f
)