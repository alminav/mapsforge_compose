package com.almica.mapsforge_compose.weather

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [WeatherRepository]
 *        ▲
 *        │ (wird aufgerufen in)
 *  [WeatherViewModel]  ◄─── (hält den Zustand/UI-State)
 *        ▲
 *        │ (beobachtet den Zustand)
 *   [WeatherScreen] (Compose UI)
 */
// Der Aufruf geschieht in der Methode loadWeather() innerhalb der Datei WeatherViewModel.kt:
@Serializable
data class WeatherResponse(
    // This value is written by the JSON parser when it receives the response
    // from https://api.open-meteo.com/v1/forecast
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val current: CurrentWeather,
    val daily: DailyWeather? = null,
    val hourly: HourlyWeather? = null
)

@Serializable
data class DailyWeather(
    val time: List<String>,
    val sunrise: List<String>,
    val sunset: List<String>,
    val temperature_2m_max: List<Double>? = null,
    val temperature_2m_min: List<Double>? = null,
    val weather_code: List<Int>? = null
)

@Serializable
data class HourlyWeather(
    val time: List<String>,
    val temperature_2m: List<Double>,
    val weather_code: List<Int>
)

@Serializable
data class CurrentWeather(
    val time: String,
    val temperature_2m: Double,
    val wind_speed_10m: Double,
    val wind_direction_10m: Double,
    val weather_code: Int, // <-- Neu hinzugefügt für Icons
    @SerialName("relative_humidity_2m") val humidity: Int
)

class WeatherRepository {
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    // Beispielkoordinaten für Berlin (Latitude: 52.52, Longitude: 13.41)
    suspend fun fetchCurrentWeather(lat: Double = 52.52, lon: Double = 13.41): WeatherResponse {
        val url = "https://api.open-meteo.com/v1/forecast"
        return httpClient.get(url) {
            parameter("latitude", lat)
            // The API response will contain the 'latitude' key which maps to the data class
            parameter("longitude", lon)
            parameter("current", "temperature_2m,wind_speed_10m,wind_direction_10m,weather_code,relative_humidity_2m")
            parameter("daily", "temperature_2m_max,temperature_2m_min,weather_code,sunrise,sunset")
            parameter("hourly", "temperature_2m,weather_code")
            parameter("timezone", "auto")
            parameter("forecast_days", 7)
        }.body()
    }
}
