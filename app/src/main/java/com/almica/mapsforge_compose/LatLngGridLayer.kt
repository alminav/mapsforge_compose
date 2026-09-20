package com.almica.mapsforge_compose

import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Color
import org.mapsforge.core.graphics.GraphicFactory
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.Layer
import timber.log.Timber
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor


class LatLngGridLayer : Layer() {
    private val linePaint: Paint
    private val textPaint: Paint

    init {
        // Definition für die Gitterlinien
        this.linePaint = AndroidGraphicFactory.INSTANCE.createPaint()
        this.linePaint.color = -0x00ffff01 // Semi-transparentes Blau
        this.linePaint.strokeWidth = 2f
        this.linePaint.setStyle(Style.STROKE)

        // Definition für die Textbeschriftung der Koordinaten
        this.textPaint = AndroidGraphicFactory.INSTANCE.createPaint()
        this.textPaint.setColor(Color.BLACK)
        this.textPaint.setStyle(Style.STROKE)
        this.textPaint.strokeWidth = 1f
    }

    override fun draw(
        boundingBox: BoundingBox?,
        zoomLevel: Byte,
        canvas: Canvas?,
        topLeftPoint: Point?,
        rotation: Rotation?
    ) {
        if (boundingBox == null || canvas == null || topLeftPoint == null) return

        // Update text size based on zoom level
        this.textPaint.setTextSize(getTextSizeForZoom(zoomLevel))
        if (zoomLevel >= 8)
            this.textPaint.strokeWidth = 2f
        else
            this.textPaint.strokeWidth = 1f

        val interval = getGridInterval(zoomLevel)
        val mapSize = MercatorProjection.getMapSize(zoomLevel, displayModel.getTileSize())

        // Breitengrade (Horizontale Linien)
        val startLat = floor(boundingBox.minLatitude / interval) * interval
        val endLat = ceil(boundingBox.maxLatitude / interval) * interval

        var lat = startLat
        while (lat <= endLat) {
            if (lat >= -85.05 && lat <= 85.05) { // Mercator limits
                val y = MercatorProjection.latitudeToPixelY(lat, mapSize) - topLeftPoint.y
                val yInt = y.toInt()
                
                canvas.drawLine(0, yInt, canvas.getWidth(), yInt, linePaint)
                
                val label = String.format(Locale.US, "%.2f°", lat)
                // Zeichne das Label an mehreren Stellen horizontal, damit es immer sichtbar ist
                // (wichtig, falls der Canvas nur ein Ausschnitt/Tile ist)
                for (xPos in listOf(50, canvas.getWidth() / 2, canvas.getWidth() - 150)) {
                    canvas.drawText(label, xPos, yInt - 10, textPaint)
                }
            }
            lat += interval
        }

        // Längengrade (Vertikale Linien)
        val startLon = floor(boundingBox.minLongitude / interval) * interval
        val endLon = ceil(boundingBox.maxLongitude / interval) * interval

        var lon = startLon
        while (lon <= endLon) {
            val x = MercatorProjection.longitudeToPixelX(lon, mapSize) - topLeftPoint.x
            val xInt = x.toInt()

            canvas.drawLine(xInt, 0, xInt, canvas.getHeight(), linePaint)

            val label = String.format(Locale.US, "%.2f°", lon)
            // Zeichne das Label oben, mittig und unten
            for (yPos in listOf(50, canvas.getHeight() / 2, canvas.getHeight() - 50)) {
                canvas.drawText(label, xInt + 10, yPos, textPaint)
            }
            lon += interval
        }
    }

    /**
     * Steuert die Gitterdichte dynamisch in Abhängigkeit des Zoomlevels.
     */
    private fun getGridInterval(zoomLevel: Byte): Double {
//        if (zoomLevel >= 16) return 0.001 // Sehr nah: 0.001 Grad Abstand
//
        if (zoomLevel >= 14) return 0.05
//        if (zoomLevel >= 12) return 0.01
//        if (zoomLevel >= 10) return 0.05
        if (zoomLevel >= 10) return 0.1
        if (zoomLevel >= 5) return 1.0
        return 5.0 // Sehr weit weg: 5 Grad Abstand
    }

    /**
     * Returns a text size based on the current zoom level.
     */
    private fun getTextSizeForZoom(zoomLevel: Byte): Float {
        return when {
            zoomLevel >= 15 -> 40f
            zoomLevel >= 12 -> 35f
            zoomLevel >= 8 -> 32f
            else -> 30f
        }
    }
}
