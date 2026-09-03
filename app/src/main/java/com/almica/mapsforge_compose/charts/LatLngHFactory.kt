package com.almica.mapsforge_compose.charts

import android.util.Xml
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import com.almica.mapsforge_compose.RoutePoint
import com.almica.mapsforge_compose.TourUtils
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sinh
import kotlin.math.sqrt

class LatLngH {
    var instructionText: String? = null
    var instructionName: String? = null
    constructor(latitude: Double, longitude: Double) : this(LatLng(latitude, longitude), 0.0)
    var latLng: LatLng
    var latLngGms: LatLng
    var altitude: Double
    var time: Long

    constructor(latLng: LatLng, altitude: Double) {
        this.latLngGms = LatLng(latLng.latitude, latLng.longitude)
        this.latLng = latLng
        this.altitude = altitude
        this.time = 0
    }

    constructor(latLng: LatLng, altitude: Double, time: Long) {
        this.latLngGms = LatLng(latLng.latitude, latLng.longitude)
        this.latLng = latLng
        this.altitude = altitude
        this.time = time
    }

    constructor(latitude: Double, longitude: Double, altitude: Double) : this(
        LatLng(
            latitude,
            longitude
        ), altitude
    )

    constructor(latitude: Double, longitude: Double, altitude: Double, time: Long) : this(
        LatLng(
            latitude,
            longitude
        ), altitude, time
    )


    val longitude: Double
        get() = latLng.longitude

    val latitude: Double
        get() = latLng.latitude
}
/**
 * example targetCount = 30, 30 is the minimum, a 40 km distance route will become 40 sections
 */
fun List<LatLngH>.simplifyToTargetCount(targetCount: Int): List<LatLngH> {
    val routeDistance = this.getDistanceFromLllh()
    val modifiedTargetCount = targetCount.coerceAtLeast((0.001 * routeDistance).toInt())
    Timber.i("modifiedTargetCount: $modifiedTargetCount")
    return this.toMercatorPoints().simplifyToTargetCount(modifiedTargetCount).toLllh()
}
fun List<LatLngH>.getDistanceFromLllh(): Double {
    var dist = 0.0
    for (i in 1 until this.size) dist += SphericalUtil.computeDistanceBetween(
        this[i - 1].latLng,
        this[i].latLng
    )
    return dist
}
data class Point(val x: Double, val y: Double, val z: Double, val time: Long)
//google search kotlin douglas peucker algorithm with number of points instead of epsilon:
fun List<LatLngH>.toMercatorPoints(): List<Point> = map { latLngH ->
    Point(
        x = TourUtils.getMercatorX(latLngH.longitude),
        y = TourUtils.getMercatorY(latLngH.latitude),
        z = latLngH.altitude,
        time = latLngH.time
    )
}
/**
 * Simplifies a polyline to a target number of coordinates using a
 * priority-queue based variant of the Ramer-Douglas-Peucker algorithm.
 */
@JvmName("simplifyPointsToTargetCount")
fun List<Point>.simplifyToTargetCount(targetCount: Int): List<Point> {
    // Edge cases where reduction isn't possible or necessary
    if (this.size <= targetCount) return this
    if (targetCount <= 2) return listOf(this.first(), this.last())

    // Data class to track sub-segments inside the Priority Queue
    data class Segment(val startIndex: Int, val endIndex: Int) {
        var splitIndex: Int = -1
        var maxDistance: Double = -1.0

        init {
            calculateMaxDistance()
        }

        private fun calculateMaxDistance() {
            if (endIndex - startIndex <= 1) return

            val startPoint = this@simplifyToTargetCount[startIndex]
            val endPoint = this@simplifyToTargetCount[endIndex]

            val lineLengthSq = (endPoint.x - startPoint.x) * (endPoint.x - startPoint.x) +
                    (endPoint.y - startPoint.y) * (endPoint.y - startPoint.y)

            for (i in (startIndex + 1) until endIndex) {
                val point = this@simplifyToTargetCount[i]

                // Calculate perpendicular distance
                val distance = if (lineLengthSq == 0.0) {
                    // Start and end points are identical
                    val dx = point.x - startPoint.x
                    val dy = point.y - startPoint.y
                    sqrt(dx * dx + dy * dy)
                } else {
                    val numerator = abs(
                        (endPoint.x - startPoint.x) * (startPoint.y - point.y) -
                                (startPoint.x - point.x) * (endPoint.y - startPoint.y)
                    )
                    numerator / sqrt(lineLengthSq)
                }

                if (distance > maxDistance) {
                    maxDistance = distance
                    splitIndex = i
                }
            }
        }
    }

    // Initialize Max-Heap prioritizing segments with the largest geometric error
    val maxHeap = PriorityQueue<Segment> { a, b -> b.maxDistance.compareTo(a.maxDistance) }

    // Tracks the indices of points we choose to keep. Start and End are kept automatically.
    val keptIndices = sortedSetOf(0, this.lastIndex)

    // Push the initial full polyline segment
    val initialSegment = Segment(0, this.lastIndex)
    if (initialSegment.splitIndex != -1) {
        maxHeap.add(initialSegment)
    }

    // Keep splitting segments until we reach the exact target number of coordinates
    while (keptIndices.size < targetCount && maxHeap.isNotEmpty()) {
        val segment = maxHeap.poll()

        // If the segment has no valid split point, we cannot split further
        if (segment != null) {
            if (segment.splitIndex == -1 || segment.maxDistance <= 0.0) continue

            // Add the split point to our kept points list
            keptIndices.add(segment.splitIndex)

            // Generate left and right sub-segments from the split
            val leftSegment = Segment(segment.startIndex, segment.splitIndex)
            val rightSegment = Segment(segment.splitIndex, segment.endIndex)

            if (leftSegment.splitIndex != -1) maxHeap.add(leftSegment)
            if (rightSegment.splitIndex != -1) maxHeap.add(rightSegment)
        }
    }

    // Map the sorted kept indices back to their original Point objects
    return keptIndices.map { this[it] }
}
/**
 * Once you've converted your latitude/longitude coordinates to Mercator coordinates,
 * they are on a Euclidean plane, where you can use the Pythagorean theorem.
 * Specifically, if your Mercator coordinates are (x1, y1) and (x2, y2), the distance is:
 * sqrt((x2-x1)^2 + (y2-y1)^2)
 * The distance between two points on a Mercator projection map can be approximated using the Pythagorean theorem
 * The Pythagorean method is suitable for short distances in a local area
 */
fun List<Point>.toLllh(): List<LatLngH> = map { webMercatorToLatLng(it.x, it.y, it.z, it.time) }

fun webMercatorToLatLng(x: Double, y: Double, z: Double, time: Long): LatLngH {
    val earthRadius = 6378137.0

    val longitude = (x / earthRadius) * (180.0 / Math.PI)
    val latitude = atan(sinh(y / earthRadius)) * (180.0 / Math.PI)

    return LatLngH(latitude, longitude, z, time)
}

@Throws(IOException::class)
fun String.kmlString2Lllh(): List<LatLngH> {
    val kmlReader: StringReader
    val lllh = mutableListOf<LatLngH>()
    try {
        kmlReader = StringReader(this)
        val factory = XmlPullParserFactory.newInstance()
        val xpp = factory.newPullParser()
        xpp.setInput(kmlReader)

        var inCoordinates = false
        var inLineString = false
        val sb = java.lang.StringBuilder()
        var eventType = xpp.eventType
        do {
            if (eventType == XmlPullParser.START_TAG) {
                val startTagName = xpp.name
                if (startTagName == "coordinates") {
                    inCoordinates = true
                }
                if (startTagName == "LineString") {
                    inLineString = true
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                val endTagName = xpp.name
                if (endTagName == "coordinates") {
                    inCoordinates = false
                }
                if (endTagName == "LineString") {
                    inLineString = false
                }
            } else if (eventType == XmlPullParser.TEXT) {
                if (inCoordinates && inLineString) {
                    sb.append(xpp.text.replace("\n".toRegex(), " "))
                }
            }
            eventType = xpp.next()
        } while (eventType != XmlPullParser.END_DOCUMENT)
        kmlReader.close()

        val coordinates = sb.toString()

        val coordLines = coordinates.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        for (i in coordLines.indices) {
            //Log.i(logtag,i.toString() + " " + coordLines[i])
            if (coordLines[i].length > 3) {
                val llh = coordLines[i].split(",".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                try {
                    val nLongitude = llh[0].toDouble()
                    val nLatitude = llh[1].toDouble()
                    if (llh.size > 2) lllh.add(
                        LatLngH(nLatitude, nLongitude, llh[2].toDouble()))
                    else
                        lllh.add(LatLngH(nLatitude, nLongitude))
                } catch (e: NumberFormatException) {
                    e.message?.let { Timber.e(it) }
                    Timber.e("parse error line $i  ${coordLines[i]}")
                }
            }
        }
    } catch (xppe: XmlPullParserException) {
        xppe.printStackTrace()
        Timber.e("readKml Exception reading KML data")
        // fall through
        return emptyList()
    }
    //Timber.i("$name lllh: ${lllh.size}")
    return lllh
}
fun Modifier.offsetYByPercent(percentage: Float) = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(
                x = 0, //(constraints.maxWidth * percentage).toInt(),
                y = (constraints.maxHeight * percentage).toInt()
            )
        }
    }
)
fun List<RoutePoint>.toKmlString(name: String?): String {
    val df = DecimalFormat("#0.00000")
    val dfele = DecimalFormat("#0")
    val symbols = DecimalFormatSymbols()
    symbols.decimalSeparator = '.'
    df.decimalFormatSymbols = symbols

    val serializer = Xml.newSerializer()
    val ns = "http://earth.google.com/kml/2.1"
    val xmlwriter = StringWriter()
    try {
        serializer.setOutput(xmlwriter)
        // Log.i("write2Kml", "startDocument");
        serializer.startDocument("utf-8", true)

        // Log.i("write2Kml", "setPrefix");
        serializer.setPrefix("", ns)
        //serializer.text("\r\n");
        serializer.startTag(ns, "kml")
        //serializer.text("\r\n");
        serializer.startTag(ns, "Placemark")
        //serializer.text("\r\n");
        serializer.startTag(ns, "Name")
        serializer.text(name) // "GH Kml");
        serializer.endTag(ns, "Name")
        //serializer.text("\r\n");
        serializer.startTag(ns, "LineString")
        //serializer.text("\r\n");
        serializer.startTag(ns, "coordinates")

        var ws: String

        //serializer.text("\r\n");
        Timber.i("route $name has ${this.size} points")
        for (i in this.indices) {
            val lat2: Double = this[i].latitude
            val lon2: Double = this[i].longitude
            ws = (df.format(lon2) + "," + df.format(lat2) + ","
                    + dfele.format(this[i].altitude)
                    + " ")
            serializer.text(ws)
            //serializer.text("\r\n");
        }

        serializer.endTag(ns, "coordinates")
        serializer.endTag(ns, "LineString")
        serializer.endTag(ns, "Placemark")

        serializer.endTag(ns, "kml")
        serializer.endDocument()
        xmlwriter.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }
    return xmlwriter.toString()
}
fun List<RoutePoint>.toDataPoints() : List<DataPoint> {
    var cumulativeDistance = 0.0
    return this.mapIndexed { index, routePoint ->
        if (index > 0) {
            val prev = this[index - 1]
            cumulativeDistance += SphericalUtil.computeDistanceBetween(
                LatLng(prev.latitude, prev.longitude),
                LatLng(routePoint.latitude, routePoint.longitude)
            )
        }
        DataPoint(0.001f * cumulativeDistance.toFloat(),
            routePoint.altitude.toFloat(), routePoint.latitude, routePoint.longitude)
    }
}
