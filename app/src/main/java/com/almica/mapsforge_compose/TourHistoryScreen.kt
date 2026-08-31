package com.almica.mapsforge_compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mapsforge.core.model.LatLong
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalResources
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Polyline
import com.almica.mapsforge_compose.TourUtils.simplifyToTargetCount

enum class TourSortOption {
    DATE_DESC, NAME_ASC, DISTANCE_DESC, DISTANCE_ASC, PROXIMITY_ASC
}

@Composable
fun TourHistoryScreen(
    db: TourDatabase,
    onTourSelected: (TourEntity) -> Unit,
    onClose: () -> Unit,
    currentMapPosition: LatLong? = null
) {
    BackHandler(onBack = onClose)

    val tourList by db.tourDao().getAllTours().collectAsState(initial = emptyList())
    var currentSortOption by remember { mutableStateOf(TourSortOption.NAME_ASC) }

    val sortedTourList = remember(tourList, currentSortOption) {
        when (currentSortOption) {
            TourSortOption.DATE_DESC -> tourList.sortedByDescending { it.timestamp }
            TourSortOption.NAME_ASC -> tourList.sortedBy { it.name?.lowercase() ?: "" }
            TourSortOption.DISTANCE_DESC -> tourList.sortedByDescending { it.totalDistanceKm }
            TourSortOption.DISTANCE_ASC -> tourList.sortedBy { it.totalDistanceKm }
            TourSortOption.PROXIMITY_ASC -> {
                if (currentMapPosition == null) tourList
                else tourList.sortedBy { tour ->
                    val startPoint = tour.routePoints.firstOrNull()
                    if (startPoint != null) TrackStatsCalculator.calculateDistanceKm(currentMapPosition, LatLong(startPoint.latitude, startPoint.longitude))
                    else Double.MAX_VALUE
                }
            }
        }
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    var tourToExport by remember { mutableStateOf<TourEntity?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml"),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    val tour = tourToExport ?: return@launch
                    try {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                                KmlUtils.exportToKml(tour, outputStream)
                            }
                        }
                        snackbarHostState.showSnackbar(resources.getString(R.string.tour_export_success))
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(resources.getString(R.string.tour_export_error))
                    }
                }
            }
        }
    )

    val importKmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    isImporting = true
                    try {
                        val importResult = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(it)?.use { inputStream ->
                                KmlUtils.importFromKml(inputStream)
                            }
                        }
                        if (importResult != null && importResult.points.isNotEmpty()) {
                            val points = importResult.points
                            val latLongs = points.map { LatLong(it.latitude, it.longitude) }
                            var totalDist = 0.0
                            for (i in 0 until latLongs.size - 1) {
                                totalDist += TrackStatsCalculator.calculateDistanceKm(latLongs[i], latLongs[i+1])
                            }
                            val newTour = TourEntity(
                                name = importResult.name,
                                timestamp = System.currentTimeMillis(),
                                totalDistanceKm = totalDist,
                                elevationGainMeters = 0.0,
                                routePoints = points
                            )
                            withContext(Dispatchers.IO) {
                                db.tourDao().insertTour(newTour)
                            }
                            snackbarHostState.showSnackbar(resources.getString(R.string.tour_import_success))
                        } else {
                            snackbarHostState.showSnackbar(resources.getString(R.string.tour_import_error))
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(resources.getString(R.string.tour_import_error))
                    } finally {
                        isImporting = false
                    }
                }
            }
        }
    )

    val importGpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    isImporting = true
                    try {
                        val importResult = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(it)?.use { inputStream ->
                                GpxUtils.importFromGpx(inputStream)
                            }
                        }
                        if (importResult != null && importResult.points.isNotEmpty()) {
                            val points = importResult.points
                            val latLongs = points.map { LatLong(it.latitude, it.longitude) }
                            var totalDist = 0.0
                            for (i in 0 until latLongs.size - 1) {
                                totalDist += TrackStatsCalculator.calculateDistanceKm(latLongs[i], latLongs[i+1])
                            }
                            val newTour = TourEntity(
                                name = importResult.name,
                                timestamp = System.currentTimeMillis(),
                                totalDistanceKm = totalDist,
                                elevationGainMeters = 0.0,
                                routePoints = points
                            )
                            withContext(Dispatchers.IO) {
                                db.tourDao().insertTour(newTour)
                            }
                            snackbarHostState.showSnackbar(resources.getString(R.string.tour_import_success))
                        } else {
                            snackbarHostState.showSnackbar(resources.getString(R.string.tour_import_error))
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(resources.getString(R.string.tour_import_error))
                    } finally {
                        isImporting = false
                    }
                }
            }
        }
    )

    var showImportMenu by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showImportMenu = true }
                ) {
                    Icon(imageVector = Icons.Default.FileUpload, contentDescription = stringResource(R.string.tour_menu_import))
                }
                DropdownMenu(
                    expanded = showImportMenu,
                    onDismissRequest = { showImportMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Import KML") },
                        onClick = {
                            showImportMenu = false
                            importKmlLauncher.launch(arrayOf("application/vnd.google-earth.kml+xml", "application/xml", "text/xml", "*/*"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Import GPX") },
                        onClick = {
                            showImportMenu = false
                            importGpxLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*"))
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.tour_close_description))
                }
                Text(stringResource(R.string.tour_history_title), style = MaterialTheme.typography.headlineMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tour_sort_by_date)) },
                                onClick = {
                                    currentSortOption = TourSortOption.DATE_DESC
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tour_sort_by_name)) },
                                onClick = {
                                    currentSortOption = TourSortOption.NAME_ASC
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tour_sort_by_distance)) },
                                onClick = {
                                    currentSortOption = TourSortOption.DISTANCE_ASC
                                    showSortMenu = false
                                }
                            )
                            if (currentMapPosition != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.tour_sort_by_proximity)) },
                                    onClick = {
                                        currentSortOption = TourSortOption.PROXIMITY_ASC
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val toursToDelete = tourList.filter { it.routePoints.size < 20 }
                                    toursToDelete.forEach { db.tourDao().deleteTour(it) }
                                    if (toursToDelete.isNotEmpty()) {
                                        snackbarHostState.showSnackbar(
                                            "Removed ${toursToDelete.size} short tours (< 20 points)"
                                        )
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.CleaningServices, contentDescription = "Remove short tours")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (sortedTourList.isEmpty()) {
                Text(stringResource(R.string.tour_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sortedTourList) { tour ->
                        TourHistoryItem(
                            tour = tour,
                            onClick = { onTourSelected(tour) },
                            onDelete = {
                                scope.launch {
                                    db.tourDao().deleteTour(tour)
                                }
                            },
                        onRename = { newName ->
                            scope.launch {
                                db.tourDao().updateTour(tour.copy(name = newName))
                            }
                        }, onExportKml = {
                                val exportName = if (tour.name == null) "tour_${tour.id}.kml" else
                                    if (tour.name.endsWith(".kml")) tour.name else "${tour.name}.kml"
                                tourToExport = tour
                                //exportLauncher.launch("tour_${tour.id}.kml")
                                exportLauncher.launch(exportName)
                            },
                            onSimplify = {
                                val simplifiedPoints = tour.routePoints.simplifyToTargetCount(512)
                                scope.launch {
                                    db.tourDao().updateTour(tour.copy(routePoints = simplifiedPoints))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (isImporting) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.tour_import_started))
                }
            }
        }
    }
}

@Composable
fun TourHistoryItem(
    tour: TourEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onExportKml: () -> Unit,
    onSimplify: () -> Unit
) {
    val dateString = remember(tour.timestamp) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(tour.timestamp))
    }

    var expanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (!tour.name.isNullOrEmpty()) {
                    Text(text = tour.name, style = MaterialTheme.typography.titleMedium)
                    Text(text = dateString, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(text = dateString, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.tour_distance, tour.totalDistanceKm))
                    //Text(text = stringResource(R.string.tour_elevation, tour.elevationGainMeters))
                    Text(text = stringResource(R.string.tour_points, tour.routePoints.size))
                }
            }

            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tour_menu_edit_name)) },
                        onClick = {
                            expanded = false
                            showRenameDialog = true
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tour_menu_export)) },
                        onClick = {
                            expanded = false
                            onExportKml()
                        },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) }
                    )
                    if (tour.routePoints.size > 512) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tour_menu_simplify)) },
                            onClick = {
                                expanded = false
                                onSimplify()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Polyline,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tour_menu_delete)) },
                        onClick = {
                            expanded = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(tour.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.tour_menu_edit_name)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.tour_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(newName)
                    showRenameDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TourHistoryItemPreview() {
    TourHistoryItem(
        tour = TourEntity(
            id = 1,
            name = "Wanderung am Brocken",
            timestamp = 1724925600000L,
            totalDistanceKm = 12.5,
            elevationGainMeters = 340.0,
            routePoints = listOf(RoutePoint(52.5200, 13.4050, 80.0),
                RoutePoint(52.5210, 13.4060, 90.0)
            )
        ),
        onClick = {},
        onDelete = {},
        onRename = {},
        onExportKml = {},
        onSimplify = {}
    )
}
