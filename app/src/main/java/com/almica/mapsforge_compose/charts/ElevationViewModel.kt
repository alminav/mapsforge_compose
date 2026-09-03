package com.almica.mapsforge_compose.charts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almica.mapsforge_compose.TrackingService
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class ElevationUiState(
    open val locationBearing: Float = 0f,
    open val locationSpeed: Float = 0f,
    open val locationAltitude: Double = 0.0,
    open val locationTime: Long = 0L,
    open val latLng: LatLng? = null
) {
    data class Loading(
        override val locationBearing: Float = 0f,
        override val locationSpeed: Float = 0f,
        override val locationAltitude: Double = 0.0,
        override val locationTime: Long = 0L,
        override val latLng: LatLng? = null
    ) : ElevationUiState(locationBearing, locationSpeed, locationAltitude, locationTime, latLng)

    data class Success(
        val points: List<LatLngH>,
        val distances: List<Double>,
        //val dataModel: GradientChartDataModel,
        override val locationBearing: Float = 0f,
        override val locationSpeed: Float = 0f,
        override val locationAltitude: Double = 0.0,
        override val locationTime: Long = 0L,
        override val latLng: LatLng? = null
    ) : ElevationUiState(locationBearing, locationSpeed, locationAltitude, locationTime, latLng)
}

fun ElevationUiState.copy(
    locationBearing: Float = this.locationBearing,
    locationSpeed: Float = this.locationSpeed,
    locationAltitude: Double = this.locationAltitude,
    locationTime: Long = this.locationTime,
    latLng: LatLng? = this.latLng
): ElevationUiState {
    return when (this) {
        is ElevationUiState.Loading -> copy(
            locationBearing = locationBearing,
            locationSpeed = locationSpeed,
            locationAltitude = locationAltitude,
            locationTime = locationTime,
            latLng = latLng
        )
        is ElevationUiState.Success -> copy(
            locationBearing = locationBearing,
            locationSpeed = locationSpeed,
            locationAltitude = locationAltitude,
            locationTime = locationTime,
            latLng = latLng
        )
    }
}

class ElevationViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<ElevationUiState>(ElevationUiState.Loading())
    val uiState: StateFlow<ElevationUiState> = _uiState.asStateFlow()
    init {
        observeGpsData()
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
                //updateDataPoint()
            }
        }
    }

    fun setRouteData(dataPoints: List<DataPoint>) {
        viewModelScope.launch(Dispatchers.Default) {
            val lllh = dataPoints.map { LatLngH(it.latitude, it.longitude, it.elevationMeters.toDouble()) }
            val routeDistance = lllh.getDistanceFromLllh()
            val cumulativeDistances = calculateCumulativeDistances(lllh)
            //val barChartDataModel = GradientChartDataModel(lllh, -1, routeDistance)

            _uiState.update {
                ElevationUiState.Success(
                    points = lllh,
                    distances = cumulativeDistances,
                    //dataModel = barChartDataModel,
                    latLng = it.latLng,
                    locationSpeed = it.locationSpeed,
                    locationBearing = it.locationBearing,
                    locationAltitude = it.locationAltitude,
                    locationTime = it.locationTime
                )
            }
        }
    }

    private fun calculateCumulativeDistances(points: List<LatLngH>): List<Double> {
        var sum = 0.0
        val distances = mutableListOf(0.0)
        for (i in 1 until points.size) {
            sum += SphericalUtil.computeDistanceBetween(points[i - 1].latLng, points[i].latLng)
            distances.add(sum)
        }
        return distances
    }
}
