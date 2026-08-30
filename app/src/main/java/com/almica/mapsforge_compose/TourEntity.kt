package com.almica.mapsforge_compose

import androidx.room.*
import org.json.JSONArray

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double
)

@Entity(tableName = "tours")
data class TourEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String? = null,
    val timestamp: Long,
    val totalDistanceKm: Double,
    val elevationGainMeters: Double,
    val routePoints: List<RoutePoint>
)

class RoomTypeConverters {
    @TypeConverter
    fun fromRoutePointList(value: List<RoutePoint>): String {
        val jsonArray = JSONArray()
        for (pt in value) {
            val pointArray = JSONArray().apply {
                put(pt.latitude)
                put(pt.longitude)
                put(pt.altitude)
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
                    pointArray.getDouble(0),
                    pointArray.getDouble(1),
                    pointArray.getDouble(2)
                )
            )
        }
        return list
    }
}
