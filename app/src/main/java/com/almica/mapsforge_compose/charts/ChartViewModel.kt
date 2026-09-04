package com.almica.mapsforge_compose.charts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almica.mapsforge_compose.RoutePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChartUiState(
    val dataPoints: List<DataPoint> = emptyList(),
    val titleExtension: String? = null,
    val isLoading: Boolean = false
)

class ChartViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(ChartUiState())
    val uiState: StateFlow<ChartUiState> = _uiState.asStateFlow()

    fun setRouteData(points: List<RoutePoint>, title: String? = null) {
        if (points.isEmpty()) {
            _uiState.update { it.copy(dataPoints = emptyList(), titleExtension = title, isLoading = false) }
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            val dataPoints = points.toDataPoints()
            _uiState.update { it.copy(dataPoints = dataPoints, titleExtension = title, isLoading = false) }
        }
    }

    fun clear() {
        _uiState.update { ChartUiState() }
    }
}
