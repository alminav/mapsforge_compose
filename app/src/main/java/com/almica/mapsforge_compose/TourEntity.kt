package com.almica.mapsforge_compose

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Parcelable
import android.util.Base64
import androidx.room.*
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

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
    val routePoints: List<RoutePoint>,
    val thumbnail: Bitmap? = null
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
        val jsonBytes = jsonArray.toString().toByteArray(Charsets.UTF_8)
        val byteArrayOutputStream = ByteArrayOutputStream()
        GZIPOutputStream(byteArrayOutputStream).use { gzip ->
            gzip.write(jsonBytes)
        }
        return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP)
    }

    @TypeConverter
    fun toRoutePointList(value: String): List<RoutePoint> {
        val list = mutableListOf<RoutePoint>()
        val jsonString = try {
            val decodedBytes = Base64.decode(value, Base64.DEFAULT)
            ByteArrayInputStream(decodedBytes).use { bais ->
                GZIPInputStream(bais).use { gzip ->
                    gzip.bufferedReader(Charsets.UTF_8).readText()
                }
            }
        } catch (_: Exception) {
            value
        }

        val jsonArray = JSONArray(jsonString)
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

    @TypeConverter
    fun fromBitmap(bitmap: Bitmap?): ByteArray? {
        if (bitmap == null) return null
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return outputStream.toByteArray()
    }

    @TypeConverter
    fun toBitmap(byteArray: ByteArray?): Bitmap? {
        if (byteArray == null) return null
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }
}
