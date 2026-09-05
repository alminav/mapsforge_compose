package com.almica.mapsforge_compose

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LineAxis
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.almica.mapsforge_compose.charts.DataPoint
import com.almica.mapsforge_compose.charts.ElevationChart
import com.almica.mapsforge_compose.charts.ChartViewModel
import com.almica.mapsforge_compose.charts.GradientChart
import com.almica.mapsforge_compose.charts.RouteEntity
import com.almica.mapsforge_compose.charts.SpeedChart
import com.almica.mapsforge_compose.charts.toKmlString
import com.almica.mapsforge_compose.gh.GhHelper.Locomotion
import com.almica.mapsforge_compose.weather.WeatherScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.view.MapView
import com.google.android.gms.maps.model.LatLng
import timber.log.Timber
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gpsLocation by viewModel.locationFlow.collectAsStateWithLifecycle(initialValue = null)
    val tourStats by viewModel.statsFlow.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val chartViewModel: ChartViewModel = viewModel()
    val chartUiState by chartViewModel.uiState.collectAsStateWithLifecycle()

    var showGradientChart by remember { mutableStateOf(false) }
    var showElevationChart by remember { mutableStateOf(false) }
    var showActiveElevationChart by remember { mutableStateOf(false) }
    var showActiveSpeedChart by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val resources = LocalResources.current

    LaunchedEffect(showElevationChart, uiState.loadedTrackPoints) {
        if (showElevationChart) {
            chartViewModel.setRouteData(uiState.loadedTrackPoints, uiState.loadedTrackName)
        }
    }

    LaunchedEffect(showActiveElevationChart, showActiveSpeedChart, uiState.activeTrackPoints) {
        if (showActiveElevationChart || showActiveSpeedChart) {
            chartViewModel.setRouteData(uiState.activeTrackPoints, resources.getString(R.string.active))
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val trackingStoppedMessage = stringResource(R.string.tracking_stopped) // Ensure this exists in strings.xml

    // Stop tracking if back is pressed while tracking is active
    BackHandler(enabled = uiState.isTrackingActive) {
        viewModel.stopTracking(context)
        scope.launch {
            snackbarHostState.showSnackbar(
                message = trackingStoppedMessage,
                duration = SnackbarDuration.Short
            )
        }
    }

    MainScreenContent(
        currentScreen = uiState.currentScreen,
        isDownloading = uiState.isDownloading,
        downloadMessage = uiState.downloadMessage ?: "",
        downloadProgress = uiState.downloadProgress,
        mapFileExists = uiState.mapFileExists,
        loadedTrackPoints = uiState.loadedTrackPoints,
        loadedTrackName = uiState.loadedTrackName,
        activeTrackPoints = uiState.activeTrackPoints,
        chartDataPoints = chartUiState.dataPoints,
        chartTitleExtension = chartUiState.titleExtension,
        showGradientChart = showGradientChart,
        onDismissGradientChart = { showGradientChart = false },
        showElevationChart = showElevationChart,
        showActiveElevationChart = showActiveElevationChart,
        showActiveSpeedChart = showActiveSpeedChart,
        showSearch = showSearch,
        searchResults = searchResults,
        onSearchQueryChange = { viewModel.searchAddress(it) },
        onSearchDismiss = { showSearch = false },
        onDismissElevationChart = { showElevationChart = false },
        onDismissActiveElevationChart = { showActiveElevationChart = false },
        onDismissActiveSpeedChart = { showActiveSpeedChart = false },
        snackbarHostState = snackbarHostState,
        onMove = viewModel::setTargetPosition,
        targetPosition = uiState.targetPosition,
        mapViewContainer = {
            MapViewContainer(
                uiState = uiState,
                context = context,
                gpsLocation = gpsLocation,
                stats = tourStats,
                onMove = viewModel::setTargetPosition,
                onZoomChanged = viewModel::setZoomLevel,
                onStartTracking = { viewModel.startTracking(context) },
                onStopTracking = { viewModel.stopTracking(context) },
                searchAddressPreset = uiState.pendingPoiAddress,
                onDismissSearchPreset = { viewModel.setPendingPoiAddress(null) },
                onAddPoi = { label, desc, latLong ->
                    viewModel.setPendingPoiAddress(null)
                    viewModel.addPoi(label, desc, latLong)
                },
                onDeletePoi = viewModel::deletePoi,
                onToggleFollowGps = { viewModel.setFollowGps(!uiState.followGps) },
                onSaveTrack = { name -> viewModel.saveCurrentTrack(name) },
                onClearTrack = {
                    viewModel.setLoadedTrackPoints(emptyList())
                    viewModel.setLoadedTrackName(null)
                    showGradientChart = false
                    showElevationChart = false
                },
                onRouteAppend = {
                    // We reuse the HISTORY screen to pick a tour to append
                    viewModel.setScreen(AppScreen.HISTORY)
                    // Note: We'll need to handle the selection logic in the tourHistoryScreen block
                    // By checking a state or passing a specific callback
                    viewModel.setIsAppending(true)
                },
                onShowGradientChart = { showGradientChart = true },
                onShowElevationChart = { showElevationChart = true },
                onShowActiveElevationChart = { showActiveElevationChart = true },
                onShowActiveSpeedChart = {
                    showActiveSpeedChart = true
                    Timber.i("showActiveSpeedChart = true")
                },
                onSearchClick = { showSearch = true },
                onPoiClick = { poi ->
                    viewModel.setTargetPosition(LatLong(poi.latitude, poi.longitude))
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(
                            message = poi.label,
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                onHistoryClick = { viewModel.setScreen(AppScreen.HISTORY) },
                onSettingsClick = { viewModel.setScreen(AppScreen.SETTINGS) },
                onCalculateRoute = { sLat, sLon, eLat, eLon ->
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()

                        val folder = uiState.selectedGraphHopperFolder
                        val locomotionKey = uiState.selectedLocomotionKey
                        if (folder != null) {
                            val desc =
                                resources.getString(Locomotion.fromKey(locomotionKey).descriptionRes)
                            snackbarHostState.showSnackbar(
                                message = "$folder $desc",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                    viewModel.calculateRoute(context, sLat, sLon, eLat, eLon)
                },
                onCalculateRoundtrip = { sLat, sLon, eLat, eLon ->
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()

                        val folder = uiState.selectedGraphHopperFolder
                        val locomotionKey = uiState.selectedLocomotionKey
                        if (folder != null) {
                            val desc =
                                resources.getString(Locomotion.fromKey(locomotionKey).descriptionRes)
                            snackbarHostState.showSnackbar(
                                message = "$folder $desc",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                    viewModel.calculateRoundtrip(context, sLat, sLon, eLat, eLon)
                }
            )
        },
        tourHistoryScreen = {
            TourHistoryScreen(
                db = viewModel.db,
                onTourSelected = { tour ->
                    if (uiState.isAppending) {
                        val currentPoints = uiState.loadedTrackPoints
                        viewModel.setLoadedTrackPoints(currentPoints + tour.routePoints)
                        viewModel.setIsAppending(false)
                    } else {
                        viewModel.setLoadedTrackPoints(tour.routePoints)
                        viewModel.setLoadedTrackName(tour.name)
                    }

                    tour.routePoints.firstOrNull()?.let { firstPoint ->
                        val latLong = LatLong(firstPoint.latitude, firstPoint.longitude)
                        viewModel.setTargetPosition(latLong)
                        Timber.i("First route point: $latLong")
                    }
                    viewModel.setScreen(AppScreen.MAP)
                },
                onClose = {
                    viewModel.setIsAppending(false)
                    viewModel.setScreen(AppScreen.MAP)
                },
                currentMapPosition = uiState.targetPosition
            )
        },
        settingsScreen = {
            SettingsScreen(
                repository = viewModel.getSettingsRepository(),
                onBack = { viewModel.setScreen(AppScreen.MAP) },
                onRegionChanged = {
                    viewModel.setRegion(viewModel.getSettingsRepository().getSelectedRegion())
                },
                onFollowGpsChanged = { enabled ->
                    viewModel.setFollowGps(enabled)
                },
                onKeepScreenOnChanged = { enabled ->
                    viewModel.setKeepScreenOn(enabled)
                },
                onThemeFileSelected = { uri ->
                    viewModel.importThemeFile(context, uri)
                },
                onThemeSelected = { themeId ->
                    viewModel.selectBuiltInTheme(themeId)
                },
                ghFolders = uiState.graphHopperFolders,
                selectedGhFolder = uiState.selectedGraphHopperFolder,
                onGhFolderSelected = { viewModel.selectGraphHopperFolder(it) },
                onGhFolderDeleted = { viewModel.deleteGraphHopperFolder(it) },
                onGhZipImported = { viewModel.importGraphHopperZip(context, it) },
                selectedLocomotionKey = uiState.selectedLocomotionKey,
                onLocomotionSelected = { viewModel.selectLocomotion(it) },
                mapFiles = uiState.mapFiles,
                selectedMapFileName = uiState.selectedMapFileName,
                onDownloadMap = { region ->
                    viewModel.selectMapFile(null)
                    viewModel.setRegion(region)
                    viewModel.setScreen(AppScreen.MAP)
                },
                onMapFileSelected = { viewModel.selectMapFile(it) },
                onMapImported = { viewModel.importMapFile(context, it) },
                onMapFileDeleted = {
                    val result = uiState.mapDir?.resolve(it)?.delete()
                    Timber.i("Map file deleted: $it $result")
                    if (result == true) {
                        viewModel.refreshMapFiles()
                    }
                }
            )
        },
        onSearchFinished = { latLong, address ->
            latLong?.let { viewModel.setTargetPosition(it) }
            // Pre-fill the POI name state in ViewModel or UI state
            viewModel.setPendingPoiAddress(address)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    currentScreen: AppScreen,
    isDownloading: Boolean,
    downloadMessage: String,
    downloadProgress: Float,
    mapFileExists: Boolean,
    loadedTrackPoints: List<RoutePoint>,
    snackbarHostState: SnackbarHostState,
    onMove: (LatLong?) -> Unit,
    onSearchFinished: (LatLong?, String?) -> Unit,
    mapViewContainer: @Composable () -> Unit,
    tourHistoryScreen: @Composable () -> Unit,
    settingsScreen: @Composable () -> Unit,
    loadedTrackName: String?,
    showGradientChart: Boolean,
    onDismissGradientChart: () -> Unit,
    showElevationChart: Boolean,
    showActiveSpeedChart: Boolean,
    showActiveElevationChart: Boolean,
    onDismissElevationChart: () -> Unit,
    onDismissActiveElevationChart: () -> Unit,
    targetPosition: LatLong? = null,
    activeTrackPoints: List<RoutePoint>,
    chartDataPoints: List<DataPoint>,
    chartTitleExtension: String?,
    onDismissActiveSpeedChart: () -> Unit,
    showSearch: Boolean,
    searchResults: List<GeocoderResult>,
    onSearchQueryChange: (String) -> Unit,
    onSearchDismiss: () -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
        // Ensure the scaffold itself accounts for system bars
        contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when (currentScreen) {
                AppScreen.MAP -> {
                    val scaffoldState = rememberBottomSheetScaffoldState()
                    BottomSheetScaffold(
                        scaffoldState = scaffoldState,
                        sheetPeekHeight = if (showGradientChart && loadedTrackPoints.isNotEmpty()) 120.dp
                            else if (showElevationChart && loadedTrackPoints.isNotEmpty()) 200.dp
                            else if (showActiveElevationChart && activeTrackPoints.isNotEmpty()) 200.dp
                            else if (showActiveSpeedChart && activeTrackPoints.isNotEmpty()) 200.dp
                            else 0.dp,
                        sheetContent = {
                            if (showGradientChart && loadedTrackPoints.isNotEmpty()) {
                                val routeEntity = remember(loadedTrackPoints) {
                                    RouteEntity(
                                        name = loadedTrackName ?: "Calculated Route",
                                        kmlString = loadedTrackPoints.toKmlString(loadedTrackName)
                                    )
                                }

                                GradientChart(
                                    routeEntity = routeEntity,
                                    moveMap = { latLng ->
                                        onMove(latLng?.let { LatLong(it.latitude, it.longitude) })
                                    },
                                    onDismiss = onDismissGradientChart
                                )
                            }
                            if (showElevationChart && loadedTrackPoints.isNotEmpty()) {
                                ElevationChart(
                                    dataPoints = chartDataPoints,
                                    titleExtension = chartTitleExtension,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    onPointSelected = {dataPoint ->
                                        dataPoint?.let { onMove(LatLong(dataPoint.latitude, dataPoint.longitude)) }
                                    },
                                    onClose = onDismissElevationChart,
                                    currentLatLng = targetPosition?.let { LatLng(it.latitude, it.longitude) }
                                )
                            }
                            if (showActiveSpeedChart && activeTrackPoints.isNotEmpty()) {
                                SpeedChart(
                                    dataPoints = chartDataPoints,
                                    titleExtension = chartTitleExtension,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    onPointSelected = {dataPoint ->
                                        dataPoint?.let { onMove(LatLong(dataPoint.latitude, dataPoint.longitude)) }},
                                    onClose = onDismissActiveSpeedChart,
                                    currentLatLng = null //targetPosition?.let { LatLng(it.latitude, it.longitude) }
                                )
                            }
                            if (showActiveElevationChart && activeTrackPoints.isNotEmpty()) {
                                ElevationChart(
                                    dataPoints = chartDataPoints,
                                    titleExtension = chartTitleExtension,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    onPointSelected = {dataPoint ->
                                        dataPoint?.let { onMove(LatLong(dataPoint.latitude, dataPoint.longitude)) }},
                                    onClose = onDismissActiveElevationChart,
                                    currentLatLng = null //targetPosition?.let { LatLng(it.latitude, it.longitude) }
                                )
                            }
                            /*
                                                        Box(
                                                            Modifier
                                                                .fillMaxWidth()
                                                                .height(128.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text("Bottom Sheet Content")
                                                        }
                             */
                        },
                        modifier = Modifier.padding(innerPadding),
                        sheetSwipeEnabled = true
                    ) { mapPadding ->
                        Box(Modifier.padding(mapPadding)) {
                            mapViewContainer()
                        }
                    }
                }
                AppScreen.HISTORY -> tourHistoryScreen()
                AppScreen.SETTINGS -> settingsScreen()
            }

            if (isDownloading) {
                DownloadOverlay(downloadMessage, if (mapFileExists) 1F else downloadProgress)
            }

            if (showSearch) {
                GeocoderComponent(
                    results = searchResults,
                    onQueryChange = onSearchQueryChange,
                    onResultSelected = { result ->
                        Timber.i("Selected result: $result")
                        onSearchFinished(result.latLong, result.displayAddress)
                        onSearchDismiss()
                    },
                    onClose = onSearchDismiss
                )
            }
        }
    }
}

@Composable
fun MapViewContainer(
    uiState: MainUiState,
    context: Context,
    gpsLocation: RoutePoint?,
    stats: TourStatistics,
    onMove: (LatLong?) -> Unit,
    onZoomChanged: (Int) -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    searchAddressPreset: String? = null,
    onDismissSearchPreset: () -> Unit,
    onAddPoi: (String, String?, LatLong) -> Unit,
    onDeletePoi: (PoiEntity) -> Unit,
    onToggleFollowGps: () -> Unit,
    onSaveTrack: (String) -> Unit,
    onClearTrack: () -> Unit,
    onRouteAppend: () -> Unit,
    onShowGradientChart: () -> Unit,
    onShowElevationChart: () -> Unit,
    onShowActiveElevationChart: () -> Unit,
    onShowActiveSpeedChart: () -> Unit,
    onSearchClick: () -> Unit,
    onPoiClick: (PoiEntity) -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCalculateRoute: (Double, Double, Double, Double) -> Unit,
    onCalculateRoundtrip: (Double, Double, Double, Double) -> Unit
) {
    val mapFile = remember<File?>(uiState.currentRegion, uiState.selectedMapFileName) {
        uiState.mapDir?.let { dir ->
            val fileName = uiState.selectedMapFileName ?: uiState.currentRegion.fileName
            File(dir, fileName)
        }
    }
    val effectiveMapFileExists = remember(mapFile) { mapFile?.exists() == true }

    MapViewContainerContent(
        mapFileExists = effectiveMapFileExists,
        mapFile = mapFile,
        themeFile = uiState.themeFile,
        gpsLocation = gpsLocation,
        loadedTrackPoints = uiState.loadedTrackPoints,
        activeTrackPoints = uiState.activeTrackPoints,
        pois = uiState.pois,
        onMove = { latLong -> onMove(latLong) },
        onZoomChanged = { zoom -> onZoomChanged(zoom) },
        onPoiClick = onPoiClick,
        targetPosition = uiState.targetPosition,
        zoomLevel = uiState.zoomLevel,
        followGps = uiState.followGps,
        mapControls = {
            MapControls(
                isTrackingActive = uiState.isTrackingActive,
                currentLocation = gpsLocation,
                stats = stats,
                hasTrack = uiState.loadedTrackPoints.isNotEmpty(),
                loadedTrackName = uiState.loadedTrackName,
                followGps = uiState.followGps,
                mapCenter = uiState.targetPosition,
                pois = uiState.pois,
                context = context,
                searchAddressPreset = searchAddressPreset,
                onDismissSearchPreset = onDismissSearchPreset,
                onStartTracking = onStartTracking,
                onStopTracking = onStopTracking,
                onAddPoi = { label, desc ->
                    uiState.targetPosition?.let { onAddPoi(label, desc, it) }
                },
                onDeletePoi = onDeletePoi,
                onPoiClick = onPoiClick,
                onToggleFollowGps = onToggleFollowGps,
                onSaveTrack = onSaveTrack,
                onClearTrack = onClearTrack,
                onShowGradientChart = onShowGradientChart,
                onShowElevationChart = onShowElevationChart,
                onShowActiveElevationChart = onShowActiveElevationChart,
                onShowActiveSpeedChart = onShowActiveSpeedChart,
                onSearchClick = onSearchClick,
                omRouteAppend = onRouteAppend,
                onHistoryClick = onHistoryClick,
                onSettingsClick = onSettingsClick,
                onCalculateRoute = onCalculateRoute,
                onCalculateRoundtrip = onCalculateRoundtrip
            )
        }
    )
}

@Composable
fun MapViewContainerContent(
    mapFileExists: Boolean,
    mapFile: File?,
    themeFile: File?,
    gpsLocation: RoutePoint?,
    loadedTrackPoints: List<RoutePoint>,
    activeTrackPoints: List<RoutePoint>,
    pois: List<PoiEntity> = emptyList(),
    onMove: (LatLong) -> Unit,
    onZoomChanged: (Int) -> Unit,
    onPoiClick: (PoiEntity) -> Unit,
    targetPosition: LatLong?,
    zoomLevel: Int,
    followGps: Boolean,
    mapControls: @Composable () -> Unit
) {
    val mapViewReference = remember { mutableStateOf<MapView?>(null) }
    var isMoving by remember { mutableStateOf(false) }
    Timber.i("mapFile: ${mapFile?.path}")
    // Detect movement to show crosshair when not following GPS
    LaunchedEffect(targetPosition) {
        if (!followGps && targetPosition != null) {
            isMoving = true
            delay(1500.milliseconds) // Keep visible for 1.5s after last movement
            isMoving = false
        } else {
            isMoving = false
        }
    }

    // Move map when targetPosition changes externally
    LaunchedEffect(targetPosition) {
        targetPosition?.let { pos ->
            mapViewReference.value?.model?.mapViewPosition?.setCenter(pos)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val updateTargetPosition = {
            mapViewReference.value?.model?.mapViewPosition?.center?.let { onMove(it) }
        }

        MapsforgeMapView(
            mapFile = if (mapFileExists) mapFile else null,
            themeXmlFile = themeFile,
            currentLocation = gpsLocation,
            loadedTrackPoints = loadedTrackPoints,
            activeTrackPoints = activeTrackPoints,
            pois = pois,
            followGps = followGps,
            state = remember { 
                MapsforgeMapState(
                    initialZoom = zoomLevel,
                    initialCenter = targetPosition ?: LatLong(0.0, 0.0)
                ) 
            },
            onMapViewReady = { mv ->
                mapViewReference.value = mv
                updateTargetPosition()
            },
            onCenterChanged = onMove,
            onZoomChanged = onZoomChanged,
            onPoiClick = onPoiClick
        )

        // Crosshair overlay
        AnimatedVisibility(
            visible = isMoving,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            MapCrosshair()
        }

        // Zoom Buttons
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    mapViewReference.value?.model?.mapViewPosition?.zoomIn()
                    updateTargetPosition()
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.zoom_in))
            }
            SmallFloatingActionButton(
                onClick = {
                    mapViewReference.value?.model?.mapViewPosition?.zoomOut()
                    updateTargetPosition()
                }
            ) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.zoom_out))
            }
        }
        mapControls()
    }
}

@Composable
fun DownloadOverlay(message: String, progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                if (progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun MapControls(
    isTrackingActive: Boolean,
    currentLocation: RoutePoint?,
    stats: TourStatistics,
    hasTrack: Boolean,
    loadedTrackName: String?,
    mapCenter: LatLong?,
    followGps: Boolean,
    pois: List<PoiEntity>,
    searchAddressPreset: String? = null,
    onDismissSearchPreset: () -> Unit,
    context: Context,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onAddPoi: (String, String?) -> Unit,
    onDeletePoi: (PoiEntity) -> Unit,
    onPoiClick: (PoiEntity) -> Unit,
    onToggleFollowGps: () -> Unit,
    onSaveTrack: (String) -> Unit,
    onClearTrack: () -> Unit,
    omRouteAppend: () -> Unit,
    onShowGradientChart: () -> Unit,
    onShowElevationChart: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCalculateRoute: (Double, Double, Double, Double) -> Unit,
    onCalculateRoundtrip: (Double, Double, Double, Double) -> Unit,
    onShowActiveElevationChart: () -> Unit,
    onShowActiveSpeedChart: () -> Unit,
    onSearchClick: () -> Unit
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (locationGranted) {
            onStartTracking()
        }
    }

    var showAddPoiDialog by remember { mutableStateOf(false) }
    var showPoiListDialog by remember { mutableStateOf(false) }
    var showSaveTrackDialog by remember { mutableStateOf(false) }
    var showWeatherScreen by remember { mutableStateOf(false) }

    // Launch POI dialog if an address was preset from search
    LaunchedEffect(searchAddressPreset) {
        if (searchAddressPreset != null) {
            showAddPoiDialog = true
        }
    }

    if (showAddPoiDialog) {
        // Using a key for remember ensures the state updates if the preset changes
        var label by remember(searchAddressPreset) {
            mutableStateOf(searchAddressPreset ?: "")
        }
        AlertDialog(
            onDismissRequest = {
                showAddPoiDialog = false
                onDismissSearchPreset()
            },
            title = { Text("POI hinzufügen") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextField(
                        value = label,
                        onValueChange = { label = it },
                        placeholder = { Text("Name des POI") },
                        textStyle = MaterialTheme.typography.titleLarge
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (label.isNotBlank()) {
                        onAddPoi(label, null)
                        showAddPoiDialog = false
                    }
                }) {
                    Text("Hinzufügen")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddPoiDialog = false
                    onDismissSearchPreset()
                }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (showPoiListDialog) {
        PoiListDialog(
            pois = pois,
            mapLocation = mapCenter?.let { RoutePoint(it.latitude, it.longitude) },
            onDismiss = { showPoiListDialog = false },
            onPoiClick = onPoiClick,
            onDeletePoi = onDeletePoi,
            onCalculateRoute = { lat, lon ->
                (currentLocation?.let { LatLong(it.latitude, it.longitude) }
                    ?: mapCenter)?.let { start ->
                    onCalculateRoute(start.latitude, start.longitude, lat, lon)
                    showPoiListDialog = false
                }
            },
            onCalculateRoundtrip = { lat, lon ->
                (currentLocation?.let { LatLong(it.latitude, it.longitude) }
                    ?: mapCenter)?.let { start ->
                    onCalculateRoundtrip(start.latitude, start.longitude, lat, lon)
                    showPoiListDialog = false
                }
            },
            onShowWeather = { lat, lon ->
                (currentLocation?.let { LatLong(it.latitude, it.longitude) }
                    ?: mapCenter)?.let { start ->
                    showWeatherScreen = true
                    showPoiListDialog = false
                }
            }
        )
    }

    // Render WeatherScreen last among overlays to ensure it is on top
    if (showWeatherScreen) { // showWeather = false after map click
        val mapLocation = mapCenter?.let { RoutePoint(it.latitude, it.longitude) }
        mapLocation?.let { cp ->
            AlertDialog(
                onDismissRequest = { showWeatherScreen = false },
                confirmButton = {
                    TextButton(onClick = { showWeatherScreen = false }) {
                        Text(stringResource(R.string.action_close))
                    }
                },
                text = {
                    WeatherScreen(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .verticalScroll(rememberScrollState()),
                        latitude = cp.latitude,
                        longitude = cp.longitude
                    )
                }
            )
        }
    }
    if (showSaveTrackDialog) {
        val defaultName = remember {
            SimpleDateFormat("yyyy-MM-dd_HH:mm", Locale.getDefault()).format(Date())
        }
        var trackName by remember { mutableStateOf(loadedTrackName ?: defaultName) }
        AlertDialog(
            onDismissRequest = { showSaveTrackDialog = false },
            title = { Text("Route speichern") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = trackName,
                        onValueChange = { trackName = it },
                        placeholder = { Text("Name der Route") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (trackName.isNotBlank()) {
                        onSaveTrack(trackName)
                        showSaveTrackDialog = false
                    }
                }) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveTrackDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    MapControlsContent(
        isTrackingActive = isTrackingActive,
        followGps = followGps,
        hasTrack = hasTrack,
        stats = stats,
        onStartTracking = {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            val notGranted = permissions.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }

            if (notGranted.isEmpty()) {
                onStartTracking()
            } else {
                permissionLauncher.launch(notGranted.toTypedArray())
            }
        },
        onStopTracking = onStopTracking,
        onHistoryClick = onHistoryClick,
        onSettingsClick = onSettingsClick,
        onClearTrack = onClearTrack,
        onToggleFollowGps = onToggleFollowGps,
        onAddPoiClick = { showAddPoiDialog = true },
        onPoiListClick = { showPoiListDialog = true },
        onSaveTrackClick = { showSaveTrackDialog = true },
        onShowGradientChart = onShowGradientChart,
        onShowElevationChart = onShowElevationChart,
        onShowActiveElevationChart = onShowActiveElevationChart,
        onShowActiveSpeedChart = onShowActiveSpeedChart,
        onSearchClick = onSearchClick,
        omRouteAppend = omRouteAppend,
    )
}

@Composable
fun MapControlsContent(
    isTrackingActive: Boolean,
    followGps: Boolean,
    hasTrack: Boolean,
    stats: TourStatistics,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onClearTrack: () -> Unit,
    onToggleFollowGps: () -> Unit,
    onAddPoiClick: () -> Unit,
    onPoiListClick: () -> Unit,
    onSaveTrackClick: () -> Unit,
    onShowGradientChart: () -> Unit,
    omRouteAppend: () -> Unit,
    onShowElevationChart: () -> Unit,
    onShowActiveElevationChart: () -> Unit,
    onShowActiveSpeedChart: () -> Unit,
    onSearchClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Add navigation bar padding to the whole control layer to avoid overlap
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(modifier = Modifier
            .padding(5.dp)
            .align(Alignment.TopCenter)) {
            Button(onClick = {
                if (isTrackingActive) {
                    onStopTracking()
                } else {
                    onStartTracking()
                }
            }) {
                Text(if (isTrackingActive) stringResource(R.string.tracking_stop) else stringResource(R.string.tracking_start))
            }
            Spacer(modifier = Modifier.width(5.dp))
            Button(onClick = onHistoryClick) {
                Text(stringResource(R.string.menu_archive))
            }
            Spacer(modifier = Modifier.width(5.dp))
            Button(onClick = onSettingsClick) {
                Text(stringResource(R.string.menu_settings))
            }
        }

        // POI Dropdown Menu
        var showPoiMenu by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 50.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { showPoiMenu = true }
            ) {
                Icon(Icons.Default.Place, contentDescription = "POI Menü")
            }
            DropdownMenu(
                expanded = showPoiMenu,
                onDismissRequest = { showPoiMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("POI hinzufügen") },
                    onClick = {
                        showPoiMenu = false
                        onAddPoiClick()
                    },
                    leadingIcon = { Icon(Icons.Default.AddLocation, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("POI Liste") },
                    onClick = {
                        showPoiMenu = false
                        onPoiListClick()
                    },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("POI Suche") },
                    onClick = {
                        showPoiMenu = false
                        onSearchClick()
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
            }
        }

        // GPS Follow Toggle
        SmallFloatingActionButton(
            onClick = onToggleFollowGps,
            containerColor = if (followGps) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 50.dp)
        ) {
            Icon(
                imageVector = if (followGps) Icons.Default.MyLocation else Icons.Default.LocationDisabled,
                contentDescription = stringResource(R.string.action_follow_gps),
                tint = if (followGps) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Track Menu
        if (hasTrack) {
            var showTrackMenu by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 90.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { showTrackMenu = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Track Menü")
                }
                DropdownMenu(
                    expanded = showTrackMenu,
                    onDismissRequest = { showTrackMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete_track)) },
                        onClick = {
                            showTrackMenu = false
                            onClearTrack()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                    DropdownMenuItem(
                        text = { Text("Route speichern") },
                        onClick = {
                            showTrackMenu = false
                            onSaveTrackClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Gradient Monitor") },
                        onClick = {
                            onShowGradientChart()
                            showTrackMenu = false  },
                        leadingIcon = { Icon(Icons.Default.BarChart, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Elevation Monitor") },
                        onClick = {
                            onShowElevationChart()
                            showTrackMenu = false  },
                        leadingIcon = { Icon(Icons.Default.LineAxis, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Route Append") },
                        onClick = {
                            omRouteAppend()
                            showTrackMenu = false  },
                        leadingIcon = { Icon(Icons.Default.ExpandMore, contentDescription = null) }
                    )
                }
            }
        }

        if (isTrackingActive) {
            StatisticsOverlay(
                stats = stats,
                modifier = Modifier.align(Alignment.BottomCenter),
                onShowActiveElevationChart = {
                    onShowActiveElevationChart()
                }, onShowActiveSpeedChart = {
                    onShowActiveSpeedChart()
                    Timber.i("onShowActiveSpeedChart")
                }
            )
        }
    }
}

@Composable
fun MapCrosshair(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(40.dp)) {
        val strokeWidth = 2.dp.toPx()
        val color = Color.Black.copy(alpha = 0.7f)
        
        // Horizontal line
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = strokeWidth
        )
        // Vertical line
        drawLine(
            color = color,
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = strokeWidth
        )
        // Inner circle
        drawCircle(
            color = color,
            radius = 4.dp.toPx(),
            style = Stroke(width = strokeWidth)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MainScreenContent(
        currentScreen = AppScreen.MAP,
        isDownloading = false,
        downloadMessage = "Map loading...",
        downloadProgress = 0f,
        mapFileExists = true,
        loadedTrackPoints = listOf(
            RoutePoint(52.5200, 13.4050, 80.0),
            RoutePoint(52.5210, 13.4060, 85.0),
            RoutePoint(52.5220, 13.4070, 90.0)
        ),
        snackbarHostState = remember { SnackbarHostState() },
        onMove = {},
        mapViewContainer = {
            MapViewContainerContent(
                mapFileExists = true,
                mapFile = File("world.map"),
                themeFile = null,
                gpsLocation = RoutePoint(52.5200, 13.4050, 80.0),
                loadedTrackPoints = listOf(
                    RoutePoint(52.5200, 13.4050, 80.0),
                    RoutePoint(52.5210, 13.4060, 85.0),
                    RoutePoint(52.5220, 13.4070, 90.0)
                ),
                activeTrackPoints = emptyList(),
                onMove = {},
                onZoomChanged = {},
                onPoiClick = {},
                targetPosition = null,
                zoomLevel = 12,
                followGps = true,
                mapControls = {
                    MapControlsContent(
                        isTrackingActive = true,
                        followGps = true,
                        hasTrack = true,
                        stats = TourStatistics(
                            totalDistanceKm = 5.2,
                            currentSpeedKmh = 12.5,
                            elevationGainMeters = 45.0,
                            currentAltitudeMeters = 32.0
                        ),
                        onStartTracking = {},
                        onStopTracking = {},
                        onHistoryClick = {},
                        onSettingsClick = {},
                        onClearTrack = {},
                        onToggleFollowGps = {},
                        onAddPoiClick = {},
                        onPoiListClick = {},
                        onSaveTrackClick = {},
                        onShowGradientChart = {},
                        onShowElevationChart = {},
                        onShowActiveElevationChart = {},
                        onShowActiveSpeedChart = {},
                        onSearchClick = {},
                        omRouteAppend = {}
                    )
                }
            )
        },
        tourHistoryScreen = {},
        settingsScreen = {},
        loadedTrackName = "Loaded Track",
        showGradientChart = false,
        onDismissGradientChart = {},
        showElevationChart = false,
        onDismissElevationChart = { },
        onDismissActiveElevationChart = {},
        onDismissActiveSpeedChart = {},
        activeTrackPoints = emptyList(),
        showActiveElevationChart = false,
        showActiveSpeedChart = false,
        chartDataPoints = emptyList(),
        chartTitleExtension = "",
        showSearch = false,
        searchResults = emptyList(),
        onSearchQueryChange = {},
        onSearchDismiss = {},
        onSearchFinished = { _, _ -> }
    )
}
