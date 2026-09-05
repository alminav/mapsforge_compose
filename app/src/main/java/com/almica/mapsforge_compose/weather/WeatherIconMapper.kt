package com.almica.mapsforge_compose.weather

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.annotation.StringRes
import com.almica.mapsforge_compose.R

data class WeatherInfo(
    @StringRes val descriptionResId: Int,
    val icon: ImageVector
)

fun mapWmoCodeToWeather(code: Int): WeatherInfo {
    return when (code) {
        0 -> WeatherInfo(R.string.weather_clear, Icons.Filled.WbSunny)
        1, 2, 3 -> WeatherInfo(R.string.weather_partly_cloudy_desc, Icons.Filled.CloudQueue)
        45, 48 -> WeatherInfo(R.string.weather_foggy, Icons.Filled.Cloud)
        51, 53, 55, 56, 57 -> WeatherInfo(R.string.weather_drizzle, Icons.Filled.WaterDrop)
        61, 63, 65, 66, 67 -> WeatherInfo(R.string.weather_rain, Icons.Filled.Grain)
        71, 73, 75, 77 -> WeatherInfo(R.string.weather_snow, Icons.Filled.AcUnit)
        80, 81, 82 -> WeatherInfo(R.string.weather_rain_showers, Icons.Filled.Thunderstorm)
        85, 86 -> WeatherInfo(R.string.weather_snow_showers, Icons.Filled.SevereCold)
        95, 96, 99 -> WeatherInfo(R.string.weather_thunderstorm, Icons.Filled.Thunderstorm)
        else -> WeatherInfo(R.string.weather_unknown, Icons.AutoMirrored.Filled.HelpOutline)
    }
}