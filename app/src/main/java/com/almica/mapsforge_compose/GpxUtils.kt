package com.almica.mapsforge_compose

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

object GpxUtils {

    data class GpxImportResult(
        val name: String?,
        val points: List<RoutePoint>
    )

    fun importFromGpx(inputStream: InputStream): GpxImportResult? {
        val points = mutableListOf<RoutePoint>()
        var tourName: String? = null
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            var currentLat: Double? = null
            var currentLon: Double? = null
            var currentEle: Double? = null
            var inName = false
            var inEle = false
            var inDesc = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName?.lowercase()) {
                            "trkpt" -> {
                                currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                currentEle = 0.0
                            }
                            "ele" -> inEle = true
                            "name" -> if (tourName == null) inName = true
                            "desc" -> if (tourName == null) inDesc = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        when {
                            inEle -> currentEle = parser.text?.toDoubleOrNull() ?: 0.0
                            inName -> tourName = parser.text?.trim()
                            inDesc -> tourName = parser.text?.trim()?.substringBefore(" ")
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (tagName?.lowercase()) {
                            "trkpt" -> {
                                if (currentLat != null && currentLon != null) {
                                    points.add(RoutePoint(currentLat, currentLon, currentEle ?: 0.0))
                                }
                                currentLat = null
                                currentLon = null
                                currentEle = null
                            }
                            "ele" -> inEle = false
                            "name" -> inName = false
                            "desc" -> inDesc = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            inputStream.close()
        }
        return if (points.isNotEmpty()) GpxImportResult(tourName, points) else null
    }
}
