package com.almica.mapsforge_compose

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mapsforge.core.graphics.Color
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.InternalRenderTheme
import androidx.compose.ui.platform.LocalContext
import org.mapsforge.map.reader.MapFile
import timber.log.Timber
import java.io.File

@Stable
class MapsforgeMapState(
    initialZoom: Int = 15,
    initialCenter: LatLong = LatLong(0.0, 0.0)
) {
    var zoomLevel by mutableIntStateOf(initialZoom)
    var center by mutableStateOf(initialCenter)
}

@Composable
fun MapsforgeMapView(
    mapFile: File?,
    themeXmlFile: File?,
    currentLocation: RoutePoint?,
    loadedTrackPoints: List<RoutePoint>,
    activeTrackPoints: List<RoutePoint>,
    followGps: Boolean = true,
    state: MapsforgeMapState = remember { MapsforgeMapState() },
    modifier: Modifier = Modifier,
    onMapViewReady: (MapView) -> Unit = {}
) {
    val context = LocalContext.current
    
    val tileCache = remember {
        AndroidUtil.createTileCache(
            context, 
            "mapcache", 
            256, // Default tile size, could be retrieved from MapView model
            1.0f, 
            1.2
        )
    }
    
    val gpsMarker = remember { createGpsMarker() }
    val loadedPolyline = remember { createPolyline(Color.RED) }
    val activePolyline = remember { createPolyline(Color.GREEN) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                isClickable = true
                mapZoomControls.setShowMapZoomControls(true)

                if (mapFile?.exists() == true) {
                    val mapDataStore = MapFile(mapFile)
                    val trl = TileRendererLayer(
                        tileCache, mapDataStore, model.mapViewPosition, AndroidGraphicFactory.INSTANCE
                    )
                    
                    applyTheme(trl, themeXmlFile)
                    layerManager.layers.add(trl)
                    
                    // Apply initial state or center on map file/GPS
                    model.mapViewPosition.setZoomLevel(state.zoomLevel.toByte())
                    
                    if (state.center.latitude == 0.0 && state.center.longitude == 0.0) {
                        currentLocation?.let {
                            state.center = LatLong(it.latitude, it.longitude)
                        } ?: run {
                            state.center = mapDataStore.startPosition()
                        }
                    }
                    model.mapViewPosition.setCenter(state.center)
                }
                onMapViewReady(this)
            }
        },
        update = { view ->
            // Sync state to view
            view.model.mapViewPosition.setZoomLevel(state.zoomLevel.toByte())

            // Update GPS location
            currentLocation?.let {
                val newPos = LatLong(it.latitude, it.longitude)
                gpsMarker.latLong = newPos
                if (!view.layerManager.layers.contains(gpsMarker)) {
                    view.layerManager.layers.add(gpsMarker)
                }
                
                if (followGps) {
                    view.model.mapViewPosition.animateTo(newPos)
                    state.center = newPos
                }
            }

            // Update Polylines
            updatePolyline(view, loadedPolyline, loadedTrackPoints)
            updatePolyline(view, activePolyline, activeTrackPoints)
            
            // Update Theme
            val trl = view.layerManager.layers.filterIsInstance<TileRendererLayer>().firstOrNull()
            trl?.let { applyTheme(it, themeXmlFile) }
            
            view.layerManager.redrawLayers()
        },
        onRelease = { view ->
            tileCache.destroy()
            view.destroyAll()
        }
    )
}

private fun applyTheme(layer: TileRendererLayer, themeFile: File?) {
    try {
        val theme = if (themeFile?.exists() == true) {
            ExternalRenderTheme(themeFile)
        } else {
            InternalRenderTheme.DEFAULT
        }
        
        // Mapsforge InternalRenderTheme.DEFAULT is a singleton, 
        // setXmlRenderTheme handles the check usually, but we can be explicit if needed.
        layer.setXmlRenderTheme(theme)
    } catch (e: Exception) {
        Timber.e(e, "Failed to apply theme")
    }
}

private fun createGpsMarker(
    color: Color = Color.BLUE,
    size: Int = 32,
    radius: Int = 12
): Marker {
    val bitmap = AndroidGraphicFactory.INSTANCE.createBitmap(size, size)
    val canvas = AndroidGraphicFactory.INSTANCE.createCanvas()
    canvas.setBitmap(bitmap)
    val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(color)
        setStyle(org.mapsforge.core.graphics.Style.FILL)
    }
    canvas.drawCircle(size / 2, size / 2, radius, paint)
    return Marker(LatLong(0.0, 0.0), bitmap, 0, 0)
}

private fun createPolyline(
    color: Color,
    strokeWidth: Float = 8f
): Polyline {
    val linePaint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(color)
        this.strokeWidth = strokeWidth
        setStyle(org.mapsforge.core.graphics.Style.STROKE)
    }
    return Polyline(linePaint, AndroidGraphicFactory.INSTANCE)
}

private fun updatePolyline(map: MapView, polyline: Polyline, points: List<RoutePoint>) {
    val layers = map.layerManager.layers
    if (points.isEmpty()) {
        layers.remove(polyline)
        return
    }

    // Optimization: avoid mapping if the number of points hasn't changed and it's already visible
    // This is a basic optimization for performance in typical track recording scenarios.
    if (layers.contains(polyline) && polyline.getLatLongs().size == points.size) {
        return
    }

    val latLongs = points.map { LatLong(it.latitude, it.longitude) }
    polyline.setPoints(latLongs)
    
    if (!layers.contains(polyline)) {
        layers.add(polyline)
    }
}
