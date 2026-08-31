package com.almica.mapsforge_compose

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong
import timber.log.Timber
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gpsLocation by viewModel.locationFlow.collectAsStateWithLifecycle(initialValue = null)
    val tourStats by viewModel.statsFlow.collectAsStateWithLifecycle()
    val isEcoActive by viewModel.isEcoMode.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
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
        snackbarHostState = snackbarHostState,
        mapViewContainer = {
            MapViewContainer(
                uiState = uiState,
                context = context,
                gpsLocation = gpsLocation,
                stats = tourStats,
                isEcoActive = isEcoActive,
                onMove = viewModel::setTargetPosition,
                onZoomChanged = viewModel::setZoomLevel,
                onStartTracking = { viewModel.startTracking(context) },
                onStopTracking = { viewModel.stopTracking(context) },
                onAddPoi = viewModel::addPoi,
                onDeletePoi = viewModel::deletePoi,
                onToggleFollowGps = { viewModel.setFollowGps(!uiState.followGps) },
                onClearTrack = { viewModel.setLoadedTrackPoints(emptyList()) },
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
                    viewModel.calculateRoute(context, sLat, sLon, eLat, eLon)
                }
            )
        },
        tourHistoryScreen = {
            TourHistoryScreen(
                db = viewModel.db,
                onTourSelected = { tour ->
                    viewModel.setLoadedTrackPoints(tour.routePoints)
                    tour.routePoints.firstOrNull()?.let { firstPoint ->
                        val latLong = LatLong(firstPoint.latitude, firstPoint.longitude)
                        viewModel.setTargetPosition(latLong)
                        Timber.i("First route point: $latLong")
                    }
                    viewModel.setScreen(AppScreen.MAP)
                },
                onClose = { viewModel.setScreen(AppScreen.MAP) },
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
                onThemeFileSelected = { uri ->
                    viewModel.importThemeFile(context, uri)
                },
                onResetTheme = {
                    viewModel.resetTheme()
                },
                onThemeSelected = { themeId ->
                    viewModel.selectBuiltInTheme(themeId)
                },
                currentThemeFile = uiState.themeFile
            )
        }
    )
}

@Composable
fun MainScreenContent(
    currentScreen: AppScreen,
    snackbarHostState: SnackbarHostState,
    mapViewContainer: @Composable () -> Unit,
    tourHistoryScreen: @Composable () -> Unit,
    settingsScreen: @Composable () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()) {
            when (currentScreen) {
                AppScreen.MAP -> mapViewContainer()
                AppScreen.HISTORY -> tourHistoryScreen()
                AppScreen.SETTINGS -> settingsScreen()
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
    isEcoActive: Boolean,
    onMove: (LatLong?) -> Unit,
    onZoomChanged: (Int) -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onAddPoi: (String, String?, LatLong) -> Unit,
    onDeletePoi: (PoiEntity) -> Unit,
    onToggleFollowGps: () -> Unit,
    onClearTrack: () -> Unit,
    onPoiClick: (PoiEntity) -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCalculateRoute: (Double, Double, Double, Double) -> Unit
) {
    val mapFile = remember<File?>(uiState.currentRegion) {
        uiState.externalFilesDir?.let { File(it, uiState.currentRegion.fileName) }
    }

    MapViewContainerContent(
        isDownloading = uiState.isDownloading,
        regionDisplayName = uiState.currentRegion.displayName,
        downloadProgress = uiState.downloadProgress,
        mapFileExists = uiState.mapFileExists,
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
                isEcoActive = isEcoActive,
                currentLocation = gpsLocation,
                stats = stats,
                hasTrack = uiState.loadedTrackPoints.isNotEmpty(),
                followGps = uiState.followGps,
                mapCenter = uiState.targetPosition,
                pois = uiState.pois,
                context = context,
                onStartTracking = onStartTracking,
                onStopTracking = onStopTracking,
                onAddPoi = { label, desc ->
                    uiState.targetPosition?.let { onAddPoi(label, desc, it) }
                },
                onDeletePoi = onDeletePoi,
                onPoiClick = onPoiClick,
                onToggleFollowGps = onToggleFollowGps,
                onClearTrack = onClearTrack,
                onHistoryClick = onHistoryClick,
                onSettingsClick = onSettingsClick,
                onCalculateRoute = onCalculateRoute
            )
        }
    )
}

@Composable
fun MapViewContainerContent(
    isDownloading: Boolean,
    regionDisplayName: String,
    downloadProgress: Float,
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
    val mapViewReference = remember { mutableStateOf<org.mapsforge.map.android.view.MapView?>(null) }
    var isMoving by remember { mutableStateOf(false) }

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

    if (isDownloading) {
        DownloadOverlay(regionDisplayName, if (mapFileExists) 1F else downloadProgress)
    } else {
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
}

@Composable
fun DownloadOverlay(regionName: String, progress: Float) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.map_loading_progress, regionName))
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
    }
}

@Composable
fun MapControls(
    isTrackingActive: Boolean,
    isEcoActive: Boolean,
    currentLocation: RoutePoint?,
    stats: TourStatistics,
    hasTrack: Boolean,
    mapCenter: LatLong?,
    followGps: Boolean,
    pois: List<PoiEntity>,
    context: Context,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onAddPoi: (String, String?) -> Unit,
    onDeletePoi: (PoiEntity) -> Unit,
    onPoiClick: (PoiEntity) -> Unit,
    onToggleFollowGps: () -> Unit,
    onClearTrack: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCalculateRoute: (Double, Double, Double, Double) -> Unit
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

    if (showAddPoiDialog) {
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPoiDialog = false },
            title = { Text("POI hinzufügen") },
            text = {
                TextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { Text("Name des POI") }
                )
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
                TextButton(onClick = { showAddPoiDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (showPoiListDialog) {
        //onCalculateRoute(52.2200, 10.4050, 52.3310, 10.4160)
        PoiListDialog(
            pois = pois,
            onDismiss = { showPoiListDialog = false },
            onPoiClick = onPoiClick,
            onDeletePoi = onDeletePoi,
            onCalculateRoute = { lat, lon ->
                (currentLocation?.let { LatLong(it.latitude, it.longitude) } ?: mapCenter)?.let { start ->
                    onCalculateRoute(start.latitude, start.longitude, lat, lon)
                    showPoiListDialog = false
                }
            }
        )
    }

    MapControlsContent(
        isTrackingActive = isTrackingActive,
        isEcoActive = isEcoActive,
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
        onPoiListClick = { showPoiListDialog = true }
    )
}

@Composable
fun MapControlsContent(
    isTrackingActive: Boolean,
    isEcoActive: Boolean,
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
    onPoiListClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
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

        if (hasTrack && !isTrackingActive) {
            SmallFloatingActionButton(
                onClick = onClearTrack,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete_track))
            }
        }

        if (isEcoActive) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier
                    .padding(top = 60.dp)
                    .align(Alignment.TopCenter)
            ) {
                Text(stringResource(R.string.eco_mode_active), modifier = Modifier.padding(8.dp))
            }
        }

        if (isTrackingActive) {
            StatisticsOverlay(
                stats = stats,
                modifier = Modifier.align(Alignment.BottomCenter)
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
        snackbarHostState = remember { SnackbarHostState() },
        mapViewContainer = {
            MapViewContainerContent(
                isDownloading = false,
                regionDisplayName = "Niedersachsen",
                downloadProgress = 0f,
                mapFileExists = true,
                mapFile = File("niedersachsen.map"),
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
                        isEcoActive = false,
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
                        onPoiListClick = {}
                    )
                }
            )
        },
        tourHistoryScreen = {},
        settingsScreen = {}
    )
}
