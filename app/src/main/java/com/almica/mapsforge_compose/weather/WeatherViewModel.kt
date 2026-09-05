package com.almica.mapsforge_compose.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.annotation.StringRes
import com.almica.mapsforge_compose.R
import timber.log.Timber

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(
        val weather: WeatherResponse,
        val isLoading: Boolean = false
    ) : WeatherUiState
    data class Error(
        val message: String? = null,
        @StringRes val messageResId: Int? = null
    ) : WeatherUiState
}

class WeatherViewModel : ViewModel() {
    // 1. Hier wird die Instanz erstellt
    private val repository = WeatherRepository()

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState

    init {
        // 2. Wird direkt beim Start des ViewModels getriggert
        loadWeather()
    }

    fun loadWeather(lat: Double? = null, lon: Double? = null) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is WeatherUiState.Success) {
                _uiState.value = currentState.copy(isLoading = true)
            } else {
                _uiState.value = WeatherUiState.Loading
            }

            try {
                // 3. HIER findet der tatsächliche Netzwerk-Aufruf statt!
                Timber.i("WeatherViewModel loadWeather: $lat, $lon")
                val data = if (lat != null && lon != null) {
                    repository.fetchCurrentWeather(lat, lon)
                } else {
                    repository.fetchCurrentWeather()
                }

                // 4. Die Daten werden in den UI-State geladen
                _uiState.value = WeatherUiState.Success(data, isLoading = false)
                Timber.i("WeatherUiState Success weather_code: ${data.current.weather_code}")
            } catch (e: Exception) {
                _uiState.value = if (e.localizedMessage != null) {
                    WeatherUiState.Error(message = e.localizedMessage)
                } else {
                    WeatherUiState.Error(messageResId = R.string.unknown_error)
                }
            }
        }
    }
}

