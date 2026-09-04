package com.almica.mapsforge_compose

import org.mapsforge.core.model.LatLong

enum class AppScreen {
    MAP, HISTORY, SETTINGS
}

data class GeocoderResult(
    val displayAddress: String,
    val latLong: LatLong
)
