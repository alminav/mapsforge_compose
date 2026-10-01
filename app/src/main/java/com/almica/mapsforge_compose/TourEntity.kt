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
    val timestamp: Long = System.currentTimeMillis(),
    val startTime: Long = 0L,
    val totalDistanceKm: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val routePoints: List<RoutePoint> = emptyList(),
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
    companion object {
        private val cache = object : LinkedHashMap<String, List<RoutePoint>>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<RoutePoint>>): Boolean {
                return size > 50
            }
        }
    }

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
        val result = Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP)
        synchronized(cache) {
            cache[result] = value
        }
        return result
    }

    @TypeConverter
    fun toRoutePointList(value: String?): List<RoutePoint> {
        if (value.isNullOrEmpty()) return emptyList()
        synchronized(cache) {
            cache[value]?.let { return it }
        }

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

        val jsonArray = try {
            JSONArray(jsonString)
        } catch (_: Exception) {
            JSONArray()
        }
        for (i in 0 until jsonArray.length()) {
            val pointArray = jsonArray.optJSONArray(i) ?: continue
            list.add(
                RoutePoint(
                    latitude = pointArray.optDouble(0, 0.0),
                    longitude = pointArray.optDouble(1, 0.0),
                    altitude = pointArray.optDouble(2, 0.0),
                    time = pointArray.optLong(3, 0L)
                )
            )
        }
        
        synchronized(cache) {
            cache[value] = list
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
