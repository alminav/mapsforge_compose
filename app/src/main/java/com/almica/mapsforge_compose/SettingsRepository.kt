package com.almica.mapsforge_compose

import android.content.Context
import androidx.core.content.edit

data class MapRegion(
    val id: String,
    val displayName: String,
    val downloadUrl: String,
    val fileName: String
)

object MapRegions {
    val AVAILABLE_REGIONS = listOf(
        MapRegion("niedersachsen", "Niedersachsen", "https://download.mapsforge.org/maps/v5/europe/germany/niedersachsen.map", "niedersachsen.map"),
        MapRegion("sachsen-anhalt", "Sachsen-Anhalt", "https://download.mapsforge.org/maps/v5/europe/germany/sachsen-anhalt.map", "sachsen_anhalt.map"),
        MapRegion("balearen", "Balearen", "https://download.mapsforge.org/maps/v5/europe/spain/islas-baleares.map", "baleares.map"),
        MapRegion("kanaren", "Kanaren", "https://download.mapsforge.org/maps/v5/africa/canary-islands.map", "canary.map")
    )
}

class SettingsRepository(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun getSelectedRegionId(): String {
        return sharedPreferences.getString("selected_region_id", "niedersachsen") ?: "niedersachsen"
    }

    fun getSelectedRegion(): MapRegion {
        val id = getSelectedRegionId()
        return MapRegions.AVAILABLE_REGIONS.find { it.id == id } ?: MapRegions.AVAILABLE_REGIONS.first()
    }

    fun setSelectedRegionId(id: String) {
        sharedPreferences.edit { putString("selected_region_id", id) }
    }

    fun getAltitudeCorrection(): Float {
        return sharedPreferences.getFloat("altitude_correction", 0.0f)
    }

    fun setAltitudeCorrection(correction: Float) {
        sharedPreferences.edit { putFloat("altitude_correction", correction) }
    }

    fun getFollowGps(): Boolean {
        return sharedPreferences.getBoolean("follow_gps", true)
    }

    fun setFollowGps(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("follow_gps", enabled) }
    }
}
