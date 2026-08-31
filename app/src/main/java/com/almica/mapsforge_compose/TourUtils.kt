package com.almica.mapsforge_compose

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

object TourUtils {
    data class Point(val x: Double, val y: Double, val z: Double)
    const val EARTH_RADIUS_M: Double = 6378137.0
    fun getMercatorY(lat: Double): Double {
        val sinLat = sin(Math.toRadians(lat))
        return (EARTH_RADIUS_M / 2
                * ln((1 + sinLat) / (1 - sinLat)))
    }

    fun getMercatorX(lon: Double): Double {
        return EARTH_RADIUS_M * Math.toRadians(lon)
    }
    fun List<RoutePoint>.simplifyToTargetCount(targetCount: Int): List<RoutePoint> {
        return this.toMercatorPoints().simplifyToTargetCount(targetCount).toRoutePoints()
    }
    fun List<RoutePoint>.toMercatorPoints(): List<Point> = map { routePoint ->
        Point(
            x = getMercatorX(routePoint.longitude),
            y = getMercatorY(routePoint.latitude),
            z = routePoint.altitude,
        )
    }
    fun List<Point>.toRoutePoints(): List<RoutePoint> = map { point ->
        mercatorToLatLng(point.x, point.y, point.z)
    }
    fun mercatorToLatLng(x: Double, y: Double, z: Double): RoutePoint {
        val earthRadius = 6378137.0

        val longitude = (x / earthRadius) * (180.0 / Math.PI)
        val latitude = atan(sinh(y / earthRadius)) * (180.0 / Math.PI)

        return RoutePoint(latitude, longitude, z)
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
}