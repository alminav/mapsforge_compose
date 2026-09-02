package com.almica.mapsforge_compose.charts

import com.google.gson.annotations.SerializedName

class ChartUtils {
}
data class ElevationResultsObject(
    @SerializedName("results") val elevationResults: List<ElevationResultObject>? = null
)
data class ElevationResultObject(
    @SerializedName("elevation") val elevation: Double? = null,
    @SerializedName("location") val location: LocationObject? = null
)
data class LocationObject(
    @SerializedName("lat") val latitude: Double? = null,
    @SerializedName("lng") val longitude: Double? = null
)
data class RoutesObject(
    @SerializedName("routes") val routes: List<RouteObject>? = null
)
data class RouteObject(
    @SerializedName("bounds") val bounds: Any? = null,
    @SerializedName("copyrights") val copyrights: String? = null,
    @SerializedName("legs") val legs: List<Any>? = null,
    @SerializedName("overview_polyline") val overview_polyline: PointsObject? = null
)
data class PointsObject(
    @SerializedName("points") val points: String? = null
)