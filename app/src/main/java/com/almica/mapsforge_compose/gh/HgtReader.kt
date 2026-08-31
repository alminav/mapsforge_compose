package com.almica.mapsforge_compose.gh

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import java.io.File

class HgtReader(context: Context, file: File) {
    fun getElevationFromHgt(latLng: LatLng): Double {
        return 0.0
    }
}

fun getTileName(lat: Double, lon: Double): String {
    val latInt = Math.floor(lat).toInt()
    val lonInt = Math.floor(lon).toInt()
    val latChar = if (latInt >= 0) 'N' else 'S'
    val lonChar = if (lonInt >= 0) 'E' else 'W'
    return String.format("%c%02d%c%03d", latChar, Math.abs(latInt), lonChar, Math.abs(lonInt))
}
