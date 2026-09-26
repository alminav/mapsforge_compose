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
import com.almica.mapsforge_compose.gh.Const
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LineAxis
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Tour
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import com.almica.mapsforge_compose.externalData.MagentaCloud
import com.almica.mapsforge_compose.gh.GhHelper.Locomotion
import com.almica.mapsforge_compose.gh.HgtReader.Companion.getTileName
import com.almica.mapsforge_compose.gh.RoundtripValuePickerDialog
import com.almica.mapsforge_compose.gh.getTileRect
import com.almica.mapsforge_compose.weather.WeatherScreen
import kotlinx.coroutines.Dispatchers
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
                onToggleFollowGps = {
                    viewModel.setFollowGps(!uiState.followGps)
                    if (!uiState.followGps) {
                        val lastGpsPosition = uiState.activeTrackPoints.lastOrNull()?.let { LatLong(it.latitude, it.longitude) }
                        viewModel.setTargetPosition(lastGpsPosition)
                    }
                },
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
                onRouteReverse = {
                    viewModel.reverseLoadedTrack()
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
                onSaveScreenshotToTour = { bitmap, name ->
                    if (name != null) {
                        //viewModel.saveScreenshotToTour(bitmap, name)
                        viewModel.saveScreenshotToTourDatabase(bitmap, name)
                    }
                },
                onCalculateRoundtrip = { sLat, sLon, eLat, eLon ->
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        viewModel.setRoundtripPending(true, sLat, sLon, eLat, eLon)

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
                },
                onScreenshotSaved = { msg ->
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(
                            message = msg,
                            duration = SnackbarDuration.Short
                        )
                    }
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
                currentMapPosition = uiState.targetPosition,
                onSrtmRefresh = {
                    Timber.i("Refresh SRTM files")
                    viewModel.refreshMapFiles()
                }
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
                onGhFoldersRefresh = { viewModel.refreshMapFiles() },
                selectedLocomotionKey = uiState.selectedLocomotionKey,
                onLocomotionSelected = { viewModel.selectLocomotion(it) },
                mapFiles = uiState.mapFiles,
                selectedMapFileName = uiState.selectedMapFileName,
                hgtFiles = uiState.hgtFiles,
                selectedHgtFileName = uiState.selectedHgtFileName,
                onDownloadMap = { region ->
                    viewModel.selectMapFile(null)
                    viewModel.setRegion(region)
                    viewModel.setScreen(AppScreen.MAP)
                },
                onMapFileSelected = { viewModel.selectMapFile(it) },
                onMapImported = { viewModel.importMapFile(context, it) },
                onMapFileDeleted = { fileName ->
                    scope.launch(Dispatchers.IO) {
                        val result = uiState.mapDir?.resolve(fileName)?.delete()
                        Timber.i("Map file deleted: $fileName $result")
                        if (result == true) {
                            viewModel.refreshMapFiles()
                        }
                    }
                },
                onHgtFileSelected = { viewModel.selectHgtFile(it) },
                onHgtImported = { viewModel.importHgtFile(context, it) },
                onHgtFileDeleted = { fileName ->
                    scope.launch(Dispatchers.IO) {
                        val result = uiState.externalFilesDir?.resolve(Const.HGT_FOLDER_NAME)?.resolve(fileName)?.delete()
                        Timber.i("HGT file deleted: $fileName $result")
                        if (result == true) {
                            viewModel.refreshMapFiles()
                        }
                    }
                },
                isDownloading = uiState.isDownloading,
                downloadMessage = uiState.downloadMessage,
                onDownloadGhzClick = viewModel::startGhzDownload,
                onDownloadMapClick = viewModel::startMapDownload,
                onDownloadHgtClick = viewModel::startHgtDownload
            )
        },
        onSearchFinished = { latLong, address ->
            latLong?.let { viewModel.setTargetPosition(it) }
            // Pre-fill the POI name state in ViewModel or UI state
            viewModel.setPendingPoiAddress(address)
        },
    )

    if (uiState.roundtripPending) {
        RoundtripValuePickerDialog(
            onDismissRequest = { viewModel.setRoundtripPending(false) },
            onValueSelected = { factor ->
                viewModel.setRoundTripFactor(factor)
                viewModel.setRoundtripPending(false)
                viewModel.calculateRoundtrip(
                    context,
                    uiState.pendingRoundtripStartLat,
                    uiState.pendingRoundtripStartLon,
                    uiState.pendingRoundtripStopLat,
                    uiState.pendingRoundtripStopLon,
                    uiState.pendingRoundtripVehicle
                )
            },
            initialValue = uiState.roundTripFactor,
            title = "Roundtrip Factor"
        )
    }
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
                DownloadOverlay(downloadMessage, -1f)
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
    onRouteReverse: () -> Unit,
    onShowGradientChart: () -> Unit,
    onShowElevationChart: () -> Unit,
    onShowActiveElevationChart: () -> Unit,
    onShowActiveSpeedChart: () -> Unit,
    onSearchClick: () -> Unit,
    onPoiClick: (PoiEntity) -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCalculateRoute: (Double, Double, Double, Double) -> Unit,
    onCalculateRoundtrip: (Double, Double, Double, Double) -> Unit,
    onScreenshotSaved: (String) -> Unit,
    onSaveScreenshotToTour: (android.graphics.Bitmap, String?) -> Unit
) {
    val mapFile = remember(uiState.currentRegion, uiState.selectedMapFileName) {
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
        loadedTrackName = uiState.loadedTrackName,
        distanceMarkers = uiState.distanceMarkers + uiState.activeDistanceMarkers,
        pois = uiState.pois,
        onMove = { latLong -> onMove(latLong) },
        onZoomChanged = { zoom -> onZoomChanged(zoom) },
        onPoiClick = onPoiClick,
        onFollowGpsChanged = { enabled ->
            if (!enabled && uiState.followGps) {
                onToggleFollowGps()
            }
        },
        targetPosition = uiState.targetPosition,
        zoomLevel = uiState.zoomLevel,
        followGps = uiState.followGps,
        onAddPoi = { label, desc, latLong -> onAddPoi(label, desc, latLong) },
        onScreenshotSaved = onScreenshotSaved,
        onSaveScreenshotToTour = onSaveScreenshotToTour,
        mapControls = {
            MapControls(
                isTrackingActive = uiState.isTrackingActive,
                currentLocation = gpsLocation,
                stats = stats,
                hasTrack = uiState.loadedTrackPoints.isNotEmpty(),
                loadedTrackName = uiState.loadedTrackName,
                mapCenter = uiState.targetPosition,
                followGps = uiState.followGps,
                pois = uiState.pois,
                searchAddressPreset = searchAddressPreset,
                onDismissSearchPreset = onDismissSearchPreset,
                context = context,
                onStartTracking = onStartTracking,
                onStopTracking = onStopTracking,
                onAddPoi = { label, desc ->
                    uiState.targetPosition?.let {
                        onAddPoi(label, desc, it)
                    }
                },
                onDeletePoi = onDeletePoi,
                onPoiClick = onPoiClick,
                onToggleFollowGps = onToggleFollowGps,
                onSaveTrack = onSaveTrack,
                onClearTrack = onClearTrack,
                onRouteAppend = onRouteAppend,
                onRouteReverse = onRouteReverse,
                onShowGradientChart = onShowGradientChart,
                onShowElevationChart = onShowElevationChart,
                onHistoryClick = onHistoryClick,
                onSettingsClick = onSettingsClick,
                onCalculateRoute = onCalculateRoute,
                onCalculateRoundtrip = onCalculateRoundtrip,
                onShowActiveElevationChart = onShowActiveElevationChart,
                onShowActiveSpeedChart = onShowActiveSpeedChart,
                onSearchClick = onSearchClick
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
    distanceMarkers: List<DistanceMarker> = emptyList(),
    pois: List<PoiEntity> = emptyList(),
    loadedTrackName: String? = null,
    onMove: (LatLong) -> Unit,
    onZoomChanged: (Int) -> Unit,
    onPoiClick: (PoiEntity) -> Unit,
    onFollowGpsChanged: (Boolean) -> Unit,
    targetPosition: LatLong?,
    zoomLevel: Int,
    followGps: Boolean,
    onAddPoi: ((String, String?, LatLong) -> Unit)? = null,
    onScreenshotSaved: (String) -> Unit = {},
    onSaveScreenshotToTour: (android.graphics.Bitmap, String?) -> Unit = { _, _ -> },
    mapControls: @Composable () -> Unit
) {
    val context = LocalContext.current
    val mapViewReference = remember { mutableStateOf<MapView?>(null) }
    val scope = rememberCoroutineScope()
    var isMoving by remember { mutableStateOf(false) }
    var showCrosshairMenu by remember { mutableStateOf(false) }
    var showCrosshairAddPoiDialog by remember { mutableStateOf(false) }
    var selectedWeatherPoi by rememberSaveable { mutableStateOf<PoiEntity?>(null) }
    var isTakingScreenshot by remember { mutableStateOf(false) }
    //Timber.i("mapFile: ${mapFile?.path}")
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
        val mapState = remember {
            MapsforgeMapState(
                initialZoom = zoomLevel,
                initialCenter = targetPosition ?: LatLong(0.0, 0.0)
            )
        }

        // Sync external changes to mapState (e.g. from search or GPS follow)
        // We use a key to identify if the change is from an external source or just a scroll?
        // Actually, just checking if it's different enough might suffice.
        LaunchedEffect(targetPosition, zoomLevel) {
            if (targetPosition != null && targetPosition != mapState.center) {
                mapState.center = targetPosition
            }
            if (zoomLevel != mapState.zoomLevel) {
                mapState.zoomLevel = zoomLevel
            }
        }

        MapsforgeMapView(
            mapFile = if (mapFileExists) mapFile else null,
            themeXmlFile = themeFile,
            currentLocation = gpsLocation,
            loadedTrackPoints = loadedTrackPoints,
            activeTrackPoints = activeTrackPoints,
            distanceMarkers = distanceMarkers,
            pois = pois,
            followGps = followGps,
            state = mapState,
            onMapViewReady = { mv ->
                mapViewReference.value = mv
            },
            onCenterChanged = onMove,
            onZoomChanged = onZoomChanged,
            onPoiClick = onPoiClick,
            onFollowGpsChanged = onFollowGpsChanged,
            onMapMoved = {
                isMoving = true
            }
        )

        // Crosshair overlay
        Box(modifier = Modifier.align(Alignment.Center)) {
            AnimatedVisibility(
                visible = isMoving,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                MapCrosshair(
                    onClick = { showCrosshairMenu = true }
                )
            }
            DropdownMenu(
                expanded = showCrosshairMenu,
                onDismissRequest = { showCrosshairMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Screenshot speichern") },
                    onClick = {
                        showCrosshairMenu = false
                        isTakingScreenshot = true
                        scope.launch {
                            delay(50.milliseconds) // brief delay to allow zoom buttons to disappear
                            val mapView = mapViewReference.value
                            if (mapView != null) {
                                captureMapViewAdvanced(mapView) { bitmap ->
                                    isTakingScreenshot = false
                                    bitmap?.let { nonNullBitmap ->
                                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                        val fileName = loadedTrackName ?: "screenshot_${timeStamp}"

                                        // Additionally store in the TourDatabase if a track is active/loaded
                                        if (loadedTrackName != null || activeTrackPoints.isNotEmpty()) {
                                            onSaveScreenshotToTour(nonNullBitmap, loadedTrackName)
                                        } else
                                            saveBitmapToGallery(context, nonNullBitmap, fileName)

                                        onScreenshotSaved(if (loadedTrackName != null)
                                            "Screenshot für $loadedTrackName gespeichert" else
                                            "Screenshot in Gallery gespeichert")
                                    }
                                }
                            } else {
                                isTakingScreenshot = false
                            }
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                )
                if (targetPosition != null && onAddPoi != null) {
                    DropdownMenuItem(
                        text = { Text("POI hinzufügen") },
                        onClick = {
                            showCrosshairMenu = false
                            showCrosshairAddPoiDialog = true
                        },
                        leadingIcon = { Icon(Icons.Default.AddLocation, contentDescription = null) }
                    )
                }
                if (targetPosition != null) {
                    DropdownMenuItem(
                        text = { Text("Weather") },
                        onClick = {
                            showCrosshairMenu = false
                            selectedWeatherPoi = PoiEntity(label = "Weather", latitude = targetPosition.latitude, longitude = targetPosition.longitude)
                        },
                        leadingIcon = { Icon(Icons.Default.CloudQueue, contentDescription = null) }
                    )
                }
            }

            if (showCrosshairAddPoiDialog && targetPosition != null && onAddPoi != null) {
                var poiLabel by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showCrosshairAddPoiDialog = false },
                    title = { Text("POI hinzufügen") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            TextField(
                                value = poiLabel,
                                onValueChange = { poiLabel = it },
                                placeholder = { Text("Name des POI") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (poiLabel.isNotBlank()) {
                                onAddPoi(poiLabel, null, targetPosition)
                                showCrosshairAddPoiDialog = false
                            }
                        }) {
                            Text("Hinzufügen")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCrosshairAddPoiDialog = false }) {
                            Text("Abbrechen")
                        }
                    }
                )
            }
        }

        // Zoom Buttons
        if (!isTakingScreenshot) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        val newZoom = (mapState.zoomLevel + 1).coerceAtMost(22)
                        onZoomChanged(newZoom)
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.zoom_in))
                }
                SmallFloatingActionButton(
                    onClick = {
                        val newZoom = (mapState.zoomLevel - 1).coerceAtLeast(3)
                        onZoomChanged(newZoom)
                    }
                ) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.zoom_out))
                }
            }
        }

        selectedWeatherPoi?.let { poi ->
            AlertDialog(
                onDismissRequest = { selectedWeatherPoi = null },
                confirmButton = {
                    TextButton(onClick = { selectedWeatherPoi = null }) {
                        Text(stringResource(R.string.action_close))
                    }
                },
                title = {
                    Text(
                        poi.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                text = {
                    WeatherScreen(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .verticalScroll(rememberScrollState()),
                        latitude = poi.latitude,
                        longitude = poi.longitude
                    )
                }
            )
        }

        mapControls()
    }
}

@Composable
fun DownloadOverlay(message: String, progress: Float) {
    Timber.i("DownloadOverlay: $message $progress")
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
    onRouteAppend: () -> Unit,
    onRouteReverse: () -> Unit,
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
    val mainViewModel: MainViewModel = viewModel()
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
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
    var selectedWeatherPoi by rememberSaveable { mutableStateOf<PoiEntity?>(null) }
    var showMapState: String? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    // Launch POI dialog if an address was preset from search
    LaunchedEffect(searchAddressPreset) {
        if (searchAddressPreset != null) {
            showAddPoiDialog = true
        }
    }

    LaunchedEffect(showMapState) {
        val stateName = showMapState ?: return@LaunchedEffect
        val tileCenter = getTileRect(stateName)?.center
        val center = tileCenter?.let { LatLong(it.latitude, it.longitude) }
        mainViewModel.updateViewport(center, 8)
    }

    var currentMapCenter = mapCenter
    if (showMapState != null) {
        val stateTileName = showMapState!!
        val tileCenter = getTileRect(stateTileName)?.center
        currentMapCenter = tileCenter?.let { LatLong(it.latitude, it.longitude) }

        val mapFile = uiState.mapFiles.find { it.startsWith(stateTileName, ignoreCase = true) }
        val hgtFile = uiState.hgtFiles.find { it.startsWith(stateTileName, ignoreCase = true) }
        val ghFolder = uiState.graphHopperFolders.find { it.startsWith(stateTileName, ignoreCase = true) }

        AlertDialog(
            onDismissRequest = { showMapState = null },
            title = { Text("Map-Info: $stateTileName", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapStateItem(
                        label = "Mapsforge (.map)",
                        fileName = mapFile,
                        isAvailable = mapFile != null,
                        cloudLink = MagentaCloud.maps["${stateTileName.lowercase()}.map.zip"],
                        isDownloading = uiState.isDownloading && uiState.downloadMessage?.contains(stateTileName, ignoreCase = true) == true && uiState.downloadMessage?.contains(".map") == true,
                        onDownloadClick = {
                            Timber.i("Download map $stateTileName $it")
                            mainViewModel.startMapDownload("${stateTileName.lowercase()}.map.zip", it)
                        },
                        downloadProgress = if (uiState.isDownloading &&
                            uiState.downloadMessage?.contains(stateTileName, ignoreCase = true) == true &&
                            uiState.downloadMessage?.contains(".map") == true) uiState.downloadProgress else -1f,
                        onDeleteClick = { fileName ->
                            Timber.i("Delete map $fileName")
                            scope.launch(Dispatchers.IO) {
                                File(uiState.mapDir, fileName).delete()
                                mainViewModel.refreshMapFiles()
                            }
                        }
                    )
                    MapStateItem(
                        label = "GraphHopper (Routing)",
                        fileName = ghFolder,
                        isAvailable = ghFolder != null,
                        cloudLink = MagentaCloud.gh["${stateTileName.lowercase()}3d.ghz"],
                        onDownloadClick = {
                            Timber.i("Download gh $stateTileName $it")
                            mainViewModel.startGhzDownload("${stateTileName.lowercase()}3d.ghz", it)
                        },
                        isDownloading = uiState.isDownloading && uiState.downloadMessage?.contains(stateTileName, ignoreCase = true) == true && uiState.downloadMessage?.contains(".ghz") == true,
                        downloadProgress = if (uiState.isDownloading &&
                            uiState.downloadMessage?.contains(stateTileName, ignoreCase = true) == true &&
                            uiState.downloadMessage?.contains(".ghz") == true) uiState.downloadProgress else -1f,
                        onDeleteClick = { fileName ->
                            Timber.i("Delete gh $fileName")
                            scope.launch(Dispatchers.IO) {
                                File(uiState.ghDir, fileName).deleteRecursively()
                                mainViewModel.refreshMapFiles()
                            }
                        }
                    )
                    MapStateItem(
                        label = "SRTM (.hgt)",
                        fileName = hgtFile,
                        isAvailable = hgtFile != null,
                        cloudLink = MagentaCloud.hgt["${stateTileName.lowercase()}hgt.zip"],
                        onDownloadClick = {
                            Timber.i("Download hgt $stateTileName $it")
                            mainViewModel.startHgtDownload("${stateTileName.lowercase()}hgt.zip", it)
                        },
                        isDownloading = uiState.isDownloading && uiState.downloadMessage?.contains(stateTileName, ignoreCase = true) == true && uiState.downloadMessage?.contains("hgt") == true,
                        downloadProgress = if (uiState.isDownloading &&
                            uiState.downloadMessage?.contains(stateTileName, ignoreCase = true) == true &&
                            uiState.downloadMessage?.contains("hgt") == true) uiState.downloadProgress else -1f,
                        onDeleteClick = { fileName ->
                            Timber.i("Delete hgt $fileName")
                            scope.launch(Dispatchers.IO) {
                                File(uiState.hgtDir, fileName).delete()
                                mainViewModel.refreshMapFiles()
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showMapState = null }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
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
            mapLocation = currentMapCenter?.let { RoutePoint(it.latitude, it.longitude) },
            onDismiss = { showPoiListDialog = false },
            onPoiClick = onPoiClick,
            onDeletePoi = onDeletePoi,
            onCalculateRoute = { lat, lon, vehicle ->
                (currentLocation?.let { LatLong(it.latitude, it.longitude) }
                    ?: currentMapCenter)?.let { start ->
                    mainViewModel.selectLocomotion(vehicle.key)
                    onCalculateRoute(start.latitude, start.longitude, lat, lon)
                    showPoiListDialog = false
                }
            },
            onCalculateRoundtrip = { lat, lon, vehicle ->
                (currentLocation?.let { LatLong(it.latitude, it.longitude) }
                    ?: currentMapCenter)?.let { start ->
                    mainViewModel.selectLocomotion(vehicle.key)
                    onCalculateRoundtrip(start.latitude, start.longitude, lat, lon)
                    showPoiListDialog = false
                }
            },
            onShowWeather = { poi ->
                selectedWeatherPoi = poi
                showPoiListDialog = false
            }, onConfirm = {vehicle ->
                mainViewModel.selectLocomotion(vehicle.key)
            }
        )
    }

    // Render WeatherScreen last among overlays to ensure it is on top
    selectedWeatherPoi?.let { poi ->
        Timber.i("Show weather for ${poi.label}")
        AlertDialog(
            onDismissRequest = { selectedWeatherPoi = null },
            confirmButton = {
                TextButton(onClick = { selectedWeatherPoi = null }) {
                    Text(stringResource(R.string.action_close))
                }
            },
            title = {
                Text(
                    poi.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                WeatherScreen(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .verticalScroll(rememberScrollState()),
                    latitude = poi.latitude,
                    longitude = poi.longitude
                )
            }
        )
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
        onRouteAppend = onRouteAppend,
        onRouteReverse = onRouteReverse,
        mapCenter = currentMapCenter,
        onMagentaMap = {
            showMapState = it
            Timber.i("onMagentaMap: $it") },
        onNavigateToTarget = {
            (currentLocation?.let { LatLong(it.latitude, it.longitude) }
                ?: currentMapCenter)?.let { start ->
                val target = uiState.loadedTrackPoints.last()
                onCalculateRoute(start.latitude, start.longitude, target.latitude, target.longitude)
                showPoiListDialog = false
            }
        }
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
    onRouteAppend: () -> Unit,
    onRouteReverse: () -> Unit,
    onShowElevationChart: () -> Unit,
    onShowActiveElevationChart: () -> Unit,
    onShowActiveSpeedChart: () -> Unit,
    onSearchClick: () -> Unit,
    mapCenter: LatLong?,
    onMagentaMap: (String?) -> Unit,
    onNavigateToTarget: () -> Unit
) {
    val tileName = getTileName(mapCenter?.latitude ?: 0.0, mapCenter?.longitude ?: 0.0)
    //Timber.i("tileName: $tileName")
    val magentaMap = MagentaCloud.maps[tileName.lowercase()+".map.zip"]
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Add navigation bar padding to the whole control layer to avoid overlap
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
        ) {
            Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Button(onClick = {
                    if (isTrackingActive) {
                        onStopTracking()
                    } else {
                        onStartTracking()
                    }
                }) {
                    Text(if (isTrackingActive) stringResource(R.string.tracking_stop) else stringResource(R.string.tracking_start))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(onClick = onHistoryClick) {
                    Text(stringResource(R.string.menu_archive))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(onClick = onSettingsClick) {
                    Text(stringResource(R.string.menu_settings))
                }
            }
        }

        // POI Dropdown Menu
        var showPoiMenu by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 60.dp)
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
                if (hasTrack) {
                    DropdownMenuItem(
                        text = { Text("Navigate to target") },
                        onClick = {
                            showPoiMenu = false
                            onNavigateToTarget()
                        },
                        leadingIcon = { Icon(Icons.Default.Tour, contentDescription = null) }
                    )
                }
                if (magentaMap != null) {
                    DropdownMenuItem(
                        text = { Text(tileName.lowercase()) },
                        onClick = {
                            showPoiMenu = false
                            onMagentaMap(tileName.lowercase())
                        },
                        leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) }
                    )
                }
            }
        }

        // GPS Follow Toggle
        SmallFloatingActionButton(
            onClick = onToggleFollowGps,
            containerColor = if (followGps) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 60.dp)
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
                            onRouteAppend()
                            showTrackMenu = false  },
                        leadingIcon = { Icon(Icons.Default.ExpandMore, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Route Reverse") },
                        onClick = {
                            onRouteReverse()
                            showTrackMenu = false  },
                        leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null) }
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
fun MapStateItem(
    label: String,
    fileName: String?,
    isAvailable: Boolean,
    cloudLink: String? = null,
    isDownloading: Boolean = false,
    downloadProgress: Float = -1f,
    onDownloadClick: (String) -> Unit = {},
    onDeleteClick: (String) -> Unit = {}
) {
    val (stateText, stateColor) = when {
        isAvailable -> "Found" to Color(0xFF4CAF50) // Green
        cloudLink != null -> "Available" to Color(0xFF2196F3) // Blue (Material Info)
        else -> "Not Available" to Color(0xFFF44336) // Red
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.lerp(
                MaterialTheme.colorScheme.surfaceVariant,
                Color.Black,
                0.1f
            )
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                if (fileName != null) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        //color = Color.Gray
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 12.dp, end = 4.dp)
            ) {
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = stateColor
                )
                if (isAvailable && fileName != null) {
                    IconButton(onClick = {
                        onDeleteClick(fileName)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (cloudLink != null) {
                    if (isDownloading) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val progressModifier = Modifier.size(24.dp)
                            val strokeWidth = 2.dp
                            if (downloadProgress >= 0f) {
                                CircularProgressIndicator(
                                    progress = { downloadProgress.coerceIn(0f, 1f) },
                                    modifier = progressModifier,
                                    strokeWidth = strokeWidth
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = progressModifier,
                                    strokeWidth = strokeWidth
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = {
                            onDownloadClick(cloudLink)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MapCrosshair(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Canvas(
        modifier = modifier
            .size(40.dp)
            .clickable { onClick() }
    ) {
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
                distanceMarkers = emptyList(),
                loadedTrackName = "Loaded Track",
                onMove = {},
                onZoomChanged = {},
                onPoiClick = {},
                onFollowGpsChanged = {},
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
                        onRouteAppend = {},
                        onRouteReverse = {},
                        onShowElevationChart = {},
                        onShowActiveElevationChart = {},
                        onShowActiveSpeedChart = {},
                        onSearchClick = {},
                        mapCenter = LatLong(52.5200, 13.4050),
                        onMagentaMap = {},
                        onNavigateToTarget = {}
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
