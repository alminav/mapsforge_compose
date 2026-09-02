package com.almica.mapsforge_compose.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almica.mapsforge_compose.TrackingService
import com.almica.mapsforge_compose.charts.MapUtils
import com.almica.mapsforge_compose.charts.RouteEntity
import com.almica.mapsforge_compose.charts.LatLngH
import com.almica.mapsforge_compose.charts.simplifyToTargetCount
import com.almica.mapsforge_compose.charts.getDistanceFromLllh
import com.almica.mapsforge_compose.charts.kmlString2Lllh
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

sealed class GradientChartUiState(
    open val latLng: LatLng? = null,
    open val locationSpeed: Float = 0f,
    open val locationBearing: Float = 0f,
    open val locationAltitude: Double = 0.0,
    open val locationTime: Long = 0L
) {
    data class Loading(
        override val latLng: LatLng? = null,
        override val locationSpeed: Float = 0f,
        override val locationBearing: Float = 0f,
        override val locationAltitude: Double = 0.0,
        override val locationTime: Long = 0L
    ) : GradientChartUiState(latLng, locationSpeed, locationBearing, locationAltitude, locationTime)

    data class Success(
        val name: String,
        val points: List<LatLngH>,
        val distances: List<Double>,
        val dataModel: GradientChartDataModel,
        override val latLng: LatLng? = null,
        override val locationSpeed: Float = 0f,
        override val locationBearing: Float = 0f,
        override val locationAltitude: Double = 0.0,
        override val locationTime: Long = 0L
    ) : GradientChartUiState(latLng, locationSpeed, locationBearing, locationAltitude, locationTime)
}

fun GradientChartUiState.copy(
    latLng: LatLng? = this.latLng,
    locationSpeed: Float = this.locationSpeed,
    locationBearing: Float = this.locationBearing,
    locationAltitude: Double = this.locationAltitude,
    locationTime: Long = this.locationTime
): GradientChartUiState {
    return when (this) {
        is GradientChartUiState.Loading -> copy(
            latLng = latLng,
            locationSpeed = locationSpeed,
            locationBearing = locationBearing,
            locationAltitude = locationAltitude,
            locationTime = locationTime
        )
        is GradientChartUiState.Success -> copy(
            latLng = latLng,
            locationSpeed = locationSpeed,
            locationBearing = locationBearing,
            locationAltitude = locationAltitude,
            locationTime = locationTime
        )
    }
}

class GradientChartViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<GradientChartUiState>(GradientChartUiState.Loading())
    val uiState: StateFlow<GradientChartUiState> = _uiState.asStateFlow()

    fun loadRoute(route: RouteEntity) {
        viewModelScope.launch(Dispatchers.Default) {
            val lllh = route.kmlString.kmlString2Lllh()
            val routeDistance = lllh.getDistanceFromLllh()
            
            Timber.i("${route.name} lllh.size:${lllh.size}")
            
            val stepCount = (0.001 * routeDistance).toInt().coerceAtMost(42)
            val simplifiedPoints = if (lllh.isNotEmpty()) {
                lllh.simplifyToTargetCount(stepCount)
            } else {
                emptyList()
            }
            
            val cumulativeDistances = calculateCumulativeDistances(simplifiedPoints)
            val barChartDataModel = GradientChartDataModel(simplifiedPoints, -1, routeDistance)
            
            _uiState.update { 
                GradientChartUiState.Success(
                    name = route.name,
                    points = simplifiedPoints,
                    distances = cumulativeDistances,
                    dataModel = barChartDataModel,
                    latLng = it.latLng,
                    locationSpeed = it.locationSpeed,
                    locationBearing = it.locationBearing,
                    locationAltitude = it.locationAltitude,
                    locationTime = it.locationTime
                )
            }
        }
    }

    init {
        observeGpsData()
    }

    private fun calculateCumulativeDistances(points: List<LatLngH>): List<Double> {
        var sum = 0.0
        val distances = mutableListOf(0.0)
        for (i in 1 until points.size) {
            sum += SphericalUtil.computeDistanceBetween(points[i-1].latLng, points[i].latLng)
            distances.add(sum)
        }
        return distances
    }

    private fun observeGpsData() {
        viewModelScope.launch {
            TrackingService.locationFlow.collectLatest { location ->
                _uiState.update { 
                    it.copy(
                        latLng = LatLng(location.latitude, location.longitude),
                        locationAltitude = location.altitude,
                        locationTime = location.time
                    )
                }
                updateDataPoint()
            }
        }
    }

    private fun updateDataPoint() {
        val state = _uiState.value
        if (state is GradientChartUiState.Success && state.latLng != null) {
            val pointer = state.points.indices.minByOrNull { index ->
                val point = state.points[index]
                MapUtils.calculateHaversineDistance(
                    LatLng(state.latLng.latitude, state.latLng.longitude),
                    LatLng(point.latitude, point.longitude)
                )
            } ?: -1
            /**
             * 20aug2026 heading check removed
            val pointer = nearestRoutePoint(
            state.locationBearing.toDouble(),
            state.latLng,
            state.points
            )
             */
            if (pointer != state.dataModel.routePointer) {
                // label = if ((i-1)==routePointer) Const.UC_ARROW_UP

                state.dataModel.routePointer = pointer
                val routeDistance = state.distances.lastOrNull() ?: 0.0
                state.dataModel.sliderPosition = pointer.toFloat() + 0.5f
                state.dataModel.barChartData = generateGradientChart(state.points, pointer, routeDistance)
            }
        }
    }
}
