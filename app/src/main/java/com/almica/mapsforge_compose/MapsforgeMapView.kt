package com.almica.mapsforge_compose

//import org.mapsforge.map.rendertheme.InternalRenderTheme
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.almica.mapsforge_compose.charts.RamaniTheme
import com.almica.mapsforge_compose.gh.Const
import org.mapsforge.core.graphics.Align
import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Color
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.layer.download.TileDownloadLayer
import org.mapsforge.map.layer.download.tilesource.OnlineTileSource
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.ExternalRenderTheme
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
    distanceMarkers: List<DistanceMarker> = emptyList(),
    pois: List<PoiEntity> = emptyList(),
    followGps: Boolean = true,
    state: MapsforgeMapState = remember { MapsforgeMapState() },
    modifier: Modifier = Modifier,
    onMapViewReady: (MapView) -> Unit = {},
    onCenterChanged: (LatLong) -> Unit = {},
    onZoomChanged: (Int) -> Unit = {},
    onPoiClick: (PoiEntity) -> Unit = {},
    onFollowGpsChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val currentOnCenterChanged by rememberUpdatedState(onCenterChanged)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)
    val currentOnPoiClick by rememberUpdatedState(onPoiClick)
    val currentOnFollowGpsChanged by rememberUpdatedState(onFollowGpsChanged)
    val currentFollowGps by rememberUpdatedState(followGps)

    var lastViewCenter by remember { mutableStateOf<LatLong?>(null) }
    var lastViewZoom by remember { mutableIntStateOf(-1) }
    
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
    val mapFolder = remember { File(context.getExternalFilesDir(null), Const.MAPFOLDER) }
    val coastlineMapFile = remember { File(mapFolder, "coastline.map") }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                isClickable = true
                mapZoomControls.isShowMapZoomControls = true

                setOnTouchListener { view, event ->
                    if (currentFollowGps && (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE)) {
                        currentOnFollowGpsChanged(false)
                    }
                    if (event.action == MotionEvent.ACTION_UP) {
                        view.performClick()
                    }
                    false
                }

                model.mapViewPosition.addObserver {
                    val newCenter = model.mapViewPosition.center
                    val newZoom = model.mapViewPosition.zoomLevel.toInt()
                    
                    if (state.center != newCenter) {
                        lastViewCenter = newCenter
                        state.center = newCenter
                        
                        // Only notify ViewModel of center changes if not following GPS 
                        // to avoid heavy recomposition loops during animation.
                        if (!currentFollowGps) {
                            currentOnCenterChanged(newCenter)
                        }
                    }
                    if (state.zoomLevel != newZoom) {
                        lastViewZoom = newZoom
                        state.zoomLevel = newZoom
                        currentOnZoomChanged(newZoom)
                    }
                }

                val fileName = mapFile?.name?.lowercase() ?: ""
                val isTileMap = fileName.matches(Regex("^[ns]\\d{2}[ew]\\d{3}.*\\.map$"))

                if (mapFile?.exists() == true) {
                    try {
                        val mapDataStore = MultiMapDataStore().apply {
                            if (isTileMap && coastlineMapFile.exists()) {
                                addMapDataStore(MapFile(coastlineMapFile), false, false)
                            }
                            addMapDataStore(MapFile(mapFile), true, true)
                        }
                        val trl = TileRendererLayer(
                            tileCache, mapDataStore, model.mapViewPosition, AndroidGraphicFactory.INSTANCE
                        )
                        
                        applyTheme(trl, themeXmlFile)
                        layerManager.layers.add(trl)

                        // 2. Erstelle und füge das Lat/Long Gitter hinzu
                        val gridLayer = LatLngGridLayer()
                        if (SettingsRepository(context).getLatLngGrid())
                            layerManager.layers.add(gridLayer)

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
            val cache = view.getOrCreateCache()

            // Sync state to view only if it's different and didn't originate from the view
            if (state.zoomLevel != lastViewZoom) {
                view.model.mapViewPosition.setZoomLevel(state.zoomLevel.toByte())
                lastViewZoom = state.zoomLevel
            }
            if (state.center != lastViewCenter) {
                view.model.mapViewPosition.setCenter(state.center)
                lastViewCenter = state.center
            }

            // Update GPS location
            val lastAnimatedGps = cache["last_animated_gps"] as? LatLong
            currentLocation?.let {
                val newPos = LatLong(it.latitude, it.longitude)
                if (gpsMarker.latLong != newPos) {
                    gpsMarker.latLong = newPos
                }
                if (!view.layerManager.layers.contains(gpsMarker)) {
                    view.layerManager.layers.add(gpsMarker)
                }
                
                if (followGps && newPos != lastAnimatedGps) {
                    val currentPos = view.model.mapViewPosition.center
                    // Only animate if the distance is significant (> 1 meter approx)
                    // and we haven't already triggered an animation for this GPS point.
                    if (currentPos.distance(newPos) > 0.00001) { 
                        view.model.mapViewPosition.animateTo(newPos)
                        cache["last_animated_gps"] = newPos
                        lastViewCenter = newPos
                    }
                }
            }

            // Update Polylines
            updatePolyline(view, loadedPolyline, loadedTrackPoints, cache, "loaded")
            updateDistanceMarkers(view, distanceMarkers, state.zoomLevel, loadedTrackPoints.isNotEmpty())
            updatePolyline(view, activePolyline, activeTrackPoints, cache, "active")

            // Update POIs (only when pois or zoomLevel change)
            val poiCacheKey = "pois_${pois.hashCode()}_${state.zoomLevel}"
            if (cache["poi_key"] != poiCacheKey) {
                updatePoiMarkers(view, pois, state.zoomLevel, currentOnPoiClick)
                cache["poi_key"] = poiCacheKey
            }
            
            // Update Theme (only when file changes)
            val themeKey = themeXmlFile?.absolutePath ?: "none"
            if (cache["theme_key"] != themeKey) {
                val trl = view.layerManager.layers.filterIsInstance<TileRendererLayer>().firstOrNull()
                trl?.let { applyTheme(it, themeXmlFile) }
                cache["theme_key"] = themeKey
            }
            
            // Only redraw if something actually changed or periodically?
            // Redrawing every update (which happens on every scroll pixel) is expensive.
            // view.layerManager.redrawLayers()
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
            null
        }
// mapsforgeThemes = "0.25.0"
//        else {
//            InternalRenderTheme.DEFAULT
//        }
        
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

private fun updateDistanceMarkers(
    map: MapView,
    markers: List<DistanceMarker>,
    zoomLevel: Int,
    hasLoadedTrack: Boolean
) {
    val layers = map.layerManager.layers
    
    // Filter out active track distance markers if a track is loaded
    val validMarkers = if (hasLoadedTrack) {
        markers.filter { !it.isActive }
    } else {
        markers
    }

    // Remove old distance markers
    val existingMarkers = layers.filterIsInstance<DistanceMarkerOverlay>()

    if (validMarkers.isEmpty()) {
        if (existingMarkers.isNotEmpty()) {
            layers.removeAll(existingMarkers)
            map.layerManager.redrawLayers()
        }
        return
    }

    // Optimization: Check if we need to recreate markers without allocating iterators/pairs
    if (existingMarkers.size == validMarkers.size &&
        existingMarkers.firstOrNull()?.zoomLevel == zoomLevel) {
        
        var allMatch = true
        for (i in validMarkers.indices) {
            val existing = existingMarkers[i]
            val new = validMarkers[i]
            if (existing.distanceKm != new.distanceKm || 
                existing.latLong != new.latLong ||
                existing.isActive != new.isActive) {
                allMatch = false
                break
            }
        }
        if (allMatch) return
    }

    Timber.i("Updating distance markers: ${validMarkers.size} at zoom $zoomLevel")
    layers.removeAll(existingMarkers)


    // Add new markers
    validMarkers.forEach { dm ->
        val marker = createDistanceMarkerOverlay(dm, zoomLevel, hasLoadedTrack)
        if (marker != null) {
            layers.add(marker)
        }
    }
}

private class DistanceMarkerOverlay(
    latLong: LatLong,
    bitmap: Bitmap,
    val distanceKm: Int,
    val zoomLevel: Int,
    val isActive: Boolean
) : Marker(latLong, bitmap, 0, 0)

private fun createDistanceMarkerOverlay(
    dm: DistanceMarker,
    zoomLevel: Int,
    hasLoadedTrack: Boolean = false
): DistanceMarkerOverlay? {
    if (hasLoadedTrack && dm.isActive) {
        return null
    }

    val radius = (zoomLevel * 1.8f).toInt().coerceIn(20, 56)
    val size = radius * 2 + 12
    val bitmap = AndroidGraphicFactory.INSTANCE.createBitmap(size, size)
    val canvas = AndroidGraphicFactory.INSTANCE.createCanvas()
    canvas.setBitmap(bitmap)
    
    val center = size / 2.0f
    
    // Outer shadow/border
    val shadowPaint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(Color.BLACK)
        strokeWidth = 1f
        setStyle(Style.STROKE)
    }
    canvas.drawCircle(center.toInt(), center.toInt(), radius + 1, shadowPaint)

    val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(Color.WHITE)
        setStyle(Style.FILL)
    }
    canvas.drawCircle(center.toInt(), center.toInt(), radius, paint)
    
    val borderPaint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(if (dm.isActive) Color.GREEN else Color.RED)
        strokeWidth = 2f
        setStyle(Style.STROKE)
    }
    canvas.drawCircle(center.toInt(), center.toInt(), radius, borderPaint)
    
    // Draw text (distance number)
    val textPaint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(Color.BLACK)
        setTextSize(radius * 1.1f)
        setTextAlign(Align.CENTER)
    }
    // Baseline adjustment for centering text vertically
    val textSize = radius * 1.1f
    val yPos = (center + (textSize / 3f)).toInt()
    canvas.drawText(dm.distanceKm.toString(), center.toInt(), yPos, textPaint)
    
    return DistanceMarkerOverlay(dm.latLong, bitmap, dm.distanceKm, zoomLevel, dm.isActive)
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

private fun updatePolyline(
    map: MapView, 
    polyline: Polyline, 
    points: List<RoutePoint>,
    cache: MutableMap<String, Any?>,
    key: String
) {
    val layers = map.layerManager.layers
    if (points.isEmpty()) {
        layers.remove(polyline)
        cache.remove("polyline_hash_$key")
        return
    }

    // Optimization: avoid mapping if the points haven't changed
    val currentPointsHash = points.hashCode()
    if (layers.contains(polyline) && cache["polyline_hash_$key"] == currentPointsHash) {
        return
    }

    val latLongs = points.map { LatLong(it.latitude, it.longitude) }
    polyline.setPoints(latLongs)
    cache["polyline_hash_$key"] = currentPointsHash
    
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

@Preview(showBackground = true)
@Composable
fun MapsforgeMapViewPreview() {
    RamaniTheme {
        MapsforgeMapView(
            mapFile = null,
            themeXmlFile = null,
            currentLocation = RoutePoint(latitude = 52.520008, longitude = 13.404954),
            loadedTrackPoints = listOf(
                RoutePoint(latitude = 52.520008, longitude = 13.404954),
                RoutePoint(latitude = 52.525008, longitude = 13.410954)
            ),
            activeTrackPoints = listOf(
                RoutePoint(latitude = 52.520008, longitude = 13.404954)
            ),
            distanceMarkers = listOf(
                DistanceMarker(latLong = LatLong(52.520008, 13.404954), distanceKm = 0, isActive = true)
            ),
            pois = listOf(
                PoiEntity(id = 1, label = "Sample POI", latitude = 52.522008, longitude = 13.407954)
            )
        )
    }
}

/**
 * Helper to manage cache on the MapView tag.
 */
@Suppress("UNCHECKED_CAST")
private fun MapView.getOrCreateCache(): MutableMap<String, Any?> {
    var cache = tag as? MutableMap<String, Any?>
    if (cache == null) {
        cache = mutableMapOf()
        tag = cache
    }
    return cache
}
