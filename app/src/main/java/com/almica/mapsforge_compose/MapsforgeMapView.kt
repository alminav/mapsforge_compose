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
    pois: List<PoiEntity> = emptyList(),
    followGps: Boolean = true,
    state: MapsforgeMapState = remember { MapsforgeMapState() },
    modifier: Modifier = Modifier,
    onMapViewReady: (MapView) -> Unit = {},
    onCenterChanged: (LatLong) -> Unit = {},
    onZoomChanged: (Int) -> Unit = {},
    onPoiClick: (PoiEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val currentOnCenterChanged by rememberUpdatedState(onCenterChanged)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)
    val currentOnPoiClick by rememberUpdatedState(onPoiClick)
    
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

                model.mapViewPosition.addObserver {
                    val newCenter = model.mapViewPosition.center
                    val newZoom = model.mapViewPosition.zoomLevel.toInt()
                    
                    if (state.center != newCenter) {
                        state.center = newCenter
                        currentOnCenterChanged(newCenter)
                    }
                    if (state.zoomLevel != newZoom) {
                        state.zoomLevel = newZoom
                        currentOnZoomChanged(newZoom)
                    }
                }

                if (mapFile?.exists() == true) {
                    try {
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
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load map file: ${mapFile.absolutePath}")
                        // Optionally delete corrupted file so it can be re-downloaded next time
                        if (mapFile.exists()) {
                            mapFile.delete()
                        }
                    }
                }
                onMapViewReady(this)
            }
        },
        update = { view ->
            // Sync state to view only if it's different to avoid feedback loops
            if (view.model.mapViewPosition.zoomLevel.toInt() != state.zoomLevel) {
                view.model.mapViewPosition.setZoomLevel(state.zoomLevel.toByte())
            }

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

            // Update POIs (only when pois or zoomLevel change)
            if (view.tag != pois.hashCode() + state.zoomLevel) {
                updatePoiMarkers(view, pois, state.zoomLevel, currentOnPoiClick)
                view.tag = pois.hashCode() + state.zoomLevel
            }
            
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

private fun updatePoiMarkers(map: MapView, pois: List<PoiEntity>, zoomLevel: Int, onPoiClick: (PoiEntity) -> Unit) {
    val layers = map.layerManager.layers
    
    // Remove old POI markers
    val existingPoiMarkers = layers.filterIsInstance<PoiMarker>()
    layers.removeAll(existingPoiMarkers)

    // Add new markers
    pois.forEach { poi ->
        val marker = createPoiMarker(poi, zoomLevel, onPoiClick, map)
        layers.add(marker)
    }
}

private class PoiMarker(
    latLong: LatLong, 
    bitmap: org.mapsforge.core.graphics.Bitmap, 
    val poi: PoiEntity,
    horizontalOffset: Int,
    verticalOffset: Int,
    private val onClick: (PoiEntity) -> Unit,
    private val mapView: MapView
) : Marker(latLong, bitmap, horizontalOffset, verticalOffset) {
    override fun onTap(tapLatLong: LatLong?, layerXY: org.mapsforge.core.model.Point?, tapXY: org.mapsforge.core.model.Point?): Boolean {
        if (contains(layerXY, tapXY, mapView)) {
            onClick(poi)
            return true
        }
        return false
    }
}

private fun createPoiMarker(poi: PoiEntity, zoomLevel: Int, onPoiClick: (PoiEntity) -> Unit, mapView: MapView): PoiMarker {
    val radius = (zoomLevel * 1.1f).toInt().coerceIn(8, 40)
    val size = radius * 2 + 4
    val bitmap = AndroidGraphicFactory.INSTANCE.createBitmap(size, size)
    val canvas = AndroidGraphicFactory.INSTANCE.createCanvas()
    canvas.setBitmap(bitmap)
    
    val center = size / 2.0f
    
    val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(Color.RED)
        setStyle(org.mapsforge.core.graphics.Style.FILL)
    }
    canvas.drawCircle(center.toInt(), center.toInt(), radius, paint)
    
    val borderPaint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(Color.WHITE) // White border often looks better on maps
        strokeWidth = 2f
        setStyle(org.mapsforge.core.graphics.Style.STROKE)
    }
    canvas.drawCircle(center.toInt(), center.toInt(), radius, borderPaint)
    
    val outerBorderPaint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(Color.BLACK)
        strokeWidth = 1f
        setStyle(org.mapsforge.core.graphics.Style.STROKE)
    }
    canvas.drawCircle(center.toInt(), center.toInt(), radius + 1, outerBorderPaint)
    
    return PoiMarker(LatLong(poi.latitude, poi.longitude), bitmap, poi, 0, 0, onPoiClick, mapView)
}
