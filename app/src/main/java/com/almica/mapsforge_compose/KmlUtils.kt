package com.almica.mapsforge_compose

import android.util.Xml
import org.mapsforge.core.model.LatLong
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream

object KmlUtils {

    data class KmlImportResult(
        val name: String?,
        val points: List<RoutePoint>
    )

    fun exportToKml(tour: TourEntity, outputStream: OutputStream) {
        val tourName = tour.name ?: "Tour ${tour.id}"
        val kml = StringBuilder().apply {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
            append("  <Document>\n")
            append("    <name>$tourName</name>\n")
            append("    <Placemark>\n")
            append("      <name>Path</name>\n")
            append("      <LineString>\n")
            append("        <tessellate>1</tessellate>\n")
            append("        <coordinates>\n")
            
            tour.routePoints.forEach { point ->
                append("          ${point.longitude},${point.latitude},${point.altitude}\n")
            }
            
            append("        </coordinates>\n")
            append("      </LineString>\n")
            append("    </Placemark>\n")
            append("  </Document>\n")
            append("</kml>")
        }.toString()

        outputStream.use { it.write(kml.toByteArray()) }
    }

    fun importFromKml(inputStream: InputStream): KmlImportResult? {
        val points = mutableListOf<RoutePoint>()
        var tourName: String? = null
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            var inCoordinates = false
            var inName = false
            var nameCaptured = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name.equals("coordinates", ignoreCase = true)) {
                            inCoordinates = true
                        } else if (name.equals("name", ignoreCase = true) && !nameCaptured) {
                            inName = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inCoordinates) {
                            val coordText = parser.text
                            val coordLines = coordText.trim().split(Regex("\\s+"))
                            coordLines.forEach { line ->
                                val parts = line.split(",")
                                if (parts.size >= 2) {
                                    val lon = parts[0].toDoubleOrNull()
                                    val lat = parts[1].toDoubleOrNull()
                                    val alt = parts.getOrNull(2)?.toDoubleOrNull()
                                    if (lat != null && lon != null && alt == null) {
                                        points.add(RoutePoint(lat, lon, 0.0))
                                    } else if (lat != null && lon != null && alt != null) {
                                        points.add(RoutePoint(lat, lon, alt))
                                    }
                                }
                            }
                        } else if (inName) {
                            tourName = parser.text.trim()
                            nameCaptured = true
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name.equals("coordinates", ignoreCase = true)) {
                            inCoordinates = false
                        } else if (name.equals("name", ignoreCase = true)) {
                            inName = false
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
        return if (points.isNotEmpty()) KmlImportResult(tourName, points) else null
    }
}
