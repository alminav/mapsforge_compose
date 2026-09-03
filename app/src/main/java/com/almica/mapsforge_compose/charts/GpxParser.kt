package com.almica.mapsforge_compose.charts

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import kotlin.math.*

object GpxParser {
    fun parseInputStream(inputStream: InputStream): List<DataPoint> {
        val points = mutableListOf<DataPoint>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var currentLat: Double? = null
        var currentLon: Double? = null
        var currentEle: Float? = null
        var totalDistance = 0f
        var lastLat: Double? = null
        var lastLon: Double? = null

        try {
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "trkpt", "wpt" -> {
                                currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            }
                            "ele" -> {
                                currentEle = parser.nextText()?.toFloatOrNull()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "trkpt" || parser.name == "wpt") {
                            if (currentLat != null && currentLon != null && currentEle != null) {
                                if (lastLat != null && lastLon != null) {
                                    totalDistance += calculateDistance(lastLat, lastLon, currentLat, currentLon)
                                }
                                points.add(DataPoint(totalDistance, currentEle, currentLat, currentLon))
                                lastLat = currentLat
                                lastLon = currentLon
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            inputStream.close()
        }
        return points
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c).toFloat()
    }
}
