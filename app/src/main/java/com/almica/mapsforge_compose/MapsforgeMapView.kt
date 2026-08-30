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
import timber.log.Timber
import java.io.File

@Composable
fun MapsforgeMapView(
    mapFile: File?,
    themeXmlFile: File?,
    currentLocation: RoutePoint?,
    loadedTrackPoints: List<RoutePoint>,
    activeTrackPoints: List<RoutePoint>,
    followGps: Boolean = true,
    modifier: Modifier = Modifier,
    onMapViewReady: (MapView) -> Unit = {}
) {
    val mapViewInstance = remember { mutableStateOf<MapView?>(null) }
    val tileCache = remember { mutableStateOf<TileCache?>(null) }
    
    val gpsMarker = remember { createGpsMarker() }
    val loadedPolyline = remember { createPolyline(Color.RED) }
    val activePolyline = remember { createPolyline(Color.GREEN) }
    val tileRendererLayer = remember { mutableStateOf<TileRendererLayer?>(null) }

    LaunchedEffect(currentLocation, loadedTrackPoints, activeTrackPoints, followGps) {
        val map = mapViewInstance.value ?: return@LaunchedEffect
        
        if (currentLocation != null) {
            gpsMarker.latLong = LatLong(currentLocation.latitude, currentLocation.longitude)
            if (!map.layerManager.layers.contains(gpsMarker)) map.layerManager.layers.add(gpsMarker)
            
            if (followGps) {
                map.model.mapViewPosition.animateTo(LatLong(currentLocation.latitude, currentLocation.longitude))
            }
        }

        updatePolyline(map, loadedPolyline, loadedTrackPoints)
        updatePolyline(map, activePolyline, activeTrackPoints)
        
        map.layerManager.redrawLayers()
    }

    LaunchedEffect(themeXmlFile) {
        val layer = tileRendererLayer.value ?: return@LaunchedEffect
        Timber.i("Theme selected: ${themeXmlFile?.absolutePath}")
        try {
            if (themeXmlFile != null && themeXmlFile.exists()) {
                layer.setXmlRenderTheme(ExternalRenderTheme(themeXmlFile))
            } else {
                layer.setXmlRenderTheme(org.mapsforge.map.rendertheme.InternalRenderTheme.DEFAULT)
            }
            mapViewInstance.value?.layerManager?.redrawLayers()
        } catch (e: Exception) {
            Timber.e(e, "Failed to apply theme")
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                isClickable = true
                mapZoomControls.setShowMapZoomControls(true)

                val cache = AndroidUtil.createTileCache(
                    ctx, "mapcache", getModel().displayModel.tileSize, 1.0f, getModel().frameBufferModel.overdrawFactor
                )
                tileCache.value = cache

                if (mapFile != null && mapFile.exists()) {
                    val mapDataStore = org.mapsforge.map.reader.MapFile(mapFile)
                    val trl = TileRendererLayer(
                        cache, mapDataStore, getModel().mapViewPosition, AndroidGraphicFactory.INSTANCE
                    )
                    tileRendererLayer.value = trl
                    Timber.i("try external theme file: ${themeXmlFile?.absolutePath}")
                    if (themeXmlFile != null && themeXmlFile.exists()) {
                        trl.setXmlRenderTheme(ExternalRenderTheme(themeXmlFile))
                        Timber.i("Using external theme file: ${themeXmlFile.absolutePath}")
                    } else {
                        trl.setXmlRenderTheme(org.mapsforge.map.rendertheme.InternalRenderTheme.DEFAULT)
                        Timber.i("Using internal theme")
                    }

                    layerManager.layers.add(trl)
                    getModel().mapViewPosition.setZoomLevel(15.toByte())
                }
                mapViewInstance.value = this
                onMapViewReady(this)
            }
        },
        onRelease = {
            tileCache.value?.destroy()
            mapViewInstance.value?.destroyAll()
        }
    )
}

private fun createGpsMarker(): Marker {
    val bitmap = AndroidGraphicFactory.INSTANCE.createBitmap(32, 32)
    val canvas = AndroidGraphicFactory.INSTANCE.createCanvas()
    canvas.setBitmap(bitmap)
    val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        color = AndroidGraphicFactory.INSTANCE.createColor(Color.BLUE)
        setStyle(org.mapsforge.core.graphics.Style.FILL)
    }
    canvas.drawCircle(16, 16, 12, paint)
    return Marker(LatLong(0.0, 0.0), bitmap, 0, 0)
}

private fun createPolyline(color: Color): Polyline {
    val linePaint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        this.color = AndroidGraphicFactory.INSTANCE.createColor(color)
        strokeWidth = 8f
        setStyle(org.mapsforge.core.graphics.Style.STROKE)
    }
    return Polyline(linePaint, AndroidGraphicFactory.INSTANCE)
}

private fun updatePolyline(map: MapView, polyline: Polyline, points: List<RoutePoint>) {
    if (points.isEmpty()) {
        map.layerManager.layers.remove(polyline)
    } else {
        val latLongs = points.map { LatLong(it.latitude, it.longitude) }
        polyline.setPoints(latLongs)
        if (!map.layerManager.layers.contains(polyline)) {
            map.layerManager.layers.add(polyline)
        }
    }
}
