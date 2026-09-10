package com.almica.mapsforge_compose

import android.os.Parcelable
import androidx.room.*
import kotlinx.parcelize.Parcelize
import org.json.JSONArray

@Parcelize
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val time: Long = 0L
) : Parcelable

@Parcelize
@Entity(tableName = "tours")
data class TourEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String? = null,
    val timestamp: Long,
    val startTime: Long = 0L,
    val totalDistanceKm: Double,
    val elevationGainMeters: Double,
    val routePoints: List<RoutePoint>
) : Parcelable {
    fun calculateElevationDifference(): Double = calculateElevationDifference(routePoints)

    companion object {
        fun calculateElevationDifference(points: List<RoutePoint>): Double {
            if (points.isEmpty()) return 0.0
            val altitudes = points.map { it.altitude }
            val maxAlt = altitudes.maxOrNull() ?: 0.0
            val minAlt = altitudes.minOrNull() ?: 0.0
            return maxAlt - minAlt
        }
    }
}

class RoomTypeConverters {
    @TypeConverter
    fun fromRoutePointList(value: List<RoutePoint>): String {
        val jsonArray = JSONArray()
        for (pt in value) {
            val pointArray = JSONArray().apply {
                put(pt.latitude)
                put(pt.longitude)
                put(pt.altitude)
                put(pt.time)
            }
            jsonArray.put(pointArray)
        }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toRoutePointList(value: String): List<RoutePoint> {
        val list = mutableListOf<RoutePoint>()
        val jsonArray = JSONArray(value)
        for (i in 0 until jsonArray.length()) {
            val pointArray = jsonArray.getJSONArray(i)
            list.add(
                RoutePoint(
                    latitude = pointArray.getDouble(0),
                    longitude = pointArray.getDouble(1),
                    altitude = pointArray.getDouble(2),
                    time = pointArray.optLong(3, 0L)
                )
            )
        }
        return list
    }
}
