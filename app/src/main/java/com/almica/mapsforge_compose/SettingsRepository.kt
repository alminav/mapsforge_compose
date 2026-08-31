package com.almica.mapsforge_compose

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.almica.mapsforge_compose.gh.Const

data class MapRegion(
    val id: String,
    val displayName: String,
    val downloadUrl: String,
    val fileName: String
)

// ToDo world.map from assets
object MapRegions {
    val AVAILABLE_REGIONS = listOf(
        MapRegion("niedersachsen", "Niedersachsen", "https://download.mapsforge.org/maps/v5/europe/germany/niedersachsen.map", "niedersachsen.map"),
        MapRegion("sachsen-anhalt", "Sachsen-Anhalt", "https://download.mapsforge.org/maps/v5/europe/germany/sachsen-anhalt.map", "sachsen_anhalt.map"),
        MapRegion("balearen", "Balearen", "https://download.mapsforge.org/maps/v5/europe/spain/islas-baleares.map", "baleares.map"),
        MapRegion("kanaren", "Kanaren", "https://download.mapsforge.org/maps/v5/africa/canary-islands.map", "canary.map")
    )
}

data class RenderTheme(
    val id: String,
    val displayName: String,
    val relativePath: String
)

object RenderThemes {
    val AVAILABLE_THEMES = listOf(
        RenderTheme("cruiser", "Cruiser", "cruiser/default.xml"),
        RenderTheme("mapsforge", "Mapsforge", "mapsforge/osmarender.xml"),
        RenderTheme("outdooractive", "OutdoorActive", "outdooractive/outdooractive.xml"),
        RenderTheme("contrast", "Contrast", "render_contrast/render.xml"),
        RenderTheme("outdoor", "Outdoor", "render_outdoor/render.xml"),
        RenderTheme("simplyhike", "SimplyHike", "render_simplyhike/render.xml")
    )
}

class SettingsRepository(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val locomotionKeyString = context.getString(R.string.setting_locomotion)

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

    fun getThemeFilePath(): String? {
        return sharedPreferences.getString("theme_file_path", null)
    }

    fun setThemeFilePath(path: String?) {
        sharedPreferences.edit { putString("theme_file_path", path) }
    }

    fun getSelectedThemeId(): String {
        return sharedPreferences.getString("selected_theme_id", "simplyhike") ?: "simplyhike"
    }

    fun setSelectedThemeId(id: String) {
        sharedPreferences.edit { putString("selected_theme_id", id) }
    }

    fun getSelectedTheme(): RenderTheme {
        val id = getSelectedThemeId()
        return RenderThemes.AVAILABLE_THEMES.find { it.id == id } ?: RenderThemes.AVAILABLE_THEMES.last() // simplyhike is last
    }

    fun getGraphHopperFolder(): String? {
        return sharedPreferences.getString("graphhopper_folder", null)
    }

    fun setGraphHopperFolder(folderName: String?) {
        sharedPreferences.edit { putString("graphhopper_folder", folderName) }
    }

    fun getLocomotionKey(): String {
        return defaultPrefs.getString(locomotionKeyString, Const.DEFAULT_LOCOMOTION) ?: Const.DEFAULT_LOCOMOTION
    }

    fun setLocomotionKey(key: String) {
        defaultPrefs.edit { putString(locomotionKeyString, key) }
    }

    fun getRoundTripFactor(): Float {
        return sharedPreferences.getFloat("round_trip_factor", 0.5f)
    }

    fun setRoundTripFactor(factor: Float) {
        sharedPreferences.edit { putFloat("round_trip_factor", factor) }
    }

    fun getLastLatitude(): Double {
        return sharedPreferences.getFloat("last_latitude", 0.0f).toDouble()
    }

    fun setLastLatitude(lat: Double) {
        sharedPreferences.edit { putFloat("last_latitude", lat.toFloat()) }
    }

    fun getLastLongitude(): Double {
        return sharedPreferences.getFloat("last_longitude", 0.0f).toDouble()
    }

    fun setLastLongitude(lon: Double) {
        sharedPreferences.edit { putFloat("last_longitude", lon.toFloat()) }
    }

    fun getLastZoom(): Int {
        return sharedPreferences.getInt("last_zoom", 12)
    }

    fun setLastZoom(zoom: Int) {
        sharedPreferences.edit { putInt("last_zoom", zoom) }
    }
}
