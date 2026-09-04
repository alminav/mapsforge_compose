package com.almica.mapsforge_compose

import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Address
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.almica.mapsforge_compose.gh.Const
import com.almica.mapsforge_compose.gh.GhHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import android.os.Build
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mapsforge.core.model.LatLong
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.time.Duration.Companion.milliseconds

data class MainUiState(
    val currentScreen: AppScreen = AppScreen.MAP,
    val currentRegion: MapRegion,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val mapFileExists: Boolean = false,
    val themeFile: File? = null,
    val isTrackingActive: Boolean = false,
    val loadedTrackPoints: List<RoutePoint> = emptyList(),
    val loadedTrackName: String? = null,
    val activeTrackPoints: List<RoutePoint> = emptyList(),
    val pois: List<PoiEntity> = emptyList(),
    val followGps: Boolean = true,
    val targetPosition: LatLong? = null,
    val zoomLevel: Int = 12,
    val externalFilesDir: File? = null,
    val mapDir: File? = null,
    val mapFiles: List<String> = emptyList(),
    val selectedMapFileName: String? = null,
    val graphHopperFolders: List<String> = emptyList(),
    val selectedGraphHopperFolder: String? = null,
    val selectedLocomotionKey: String = "1.1",
    val roundTripFactor: Float = 0.5f,
    val downloadMessage: String? = null,
    val isAppending: Boolean = false,
    val keepScreenOn: Boolean = false,
    val pendingPoiAddress: String? = null
)

class MainViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    val db: TourDatabase,
    val poiDb: PoiDatabase,
    private val externalFilesDir: File?
) : AndroidViewModel(application) {

    private val themeDir = externalFilesDir?.resolve("themes")
    private val mapDir = externalFilesDir?.resolve(Const.MAPFOLDER)
    private val ghRootDir = externalFilesDir?.resolve(Const.GH_ROOT_FOLDER)
    private var saveJob: Job? = null

    private val _uiState = MutableStateFlow(
        MainUiState(
            currentRegion = settingsRepository.getSelectedRegion(),
            followGps = settingsRepository.getFollowGps(),
            mapFileExists = externalFilesDir?.resolve(settingsRepository.getSelectedRegion().fileName)?.exists() ?: false,
            externalFilesDir = externalFilesDir,
            mapDir = mapDir,
            themeFile = getThemeFile(),
            targetPosition = if (settingsRepository.getLastLatitude() != 0.0) {
                LatLong(settingsRepository.getLastLatitude(), settingsRepository.getLastLongitude())
            } else null,
            zoomLevel = settingsRepository.getLastZoom(),
            mapFiles = getMapFilesList(),
            selectedMapFileName = settingsRepository.getSelectedMapFileName(),
            graphHopperFolders = getGraphHopperFoldersList(),
            selectedGraphHopperFolder = settingsRepository.getGraphHopperFolder(),
            selectedLocomotionKey = settingsRepository.getLocomotionKey(),
            roundTripFactor = settingsRepository.getRoundTripFactor(),
            keepScreenOn = settingsRepository.getKeepScreenOn()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val locationFlow = TrackingService.locationFlow
    val statsFlow = TrackingService.statsFlow

    init {
        // Observe location changes to update active track points
        viewModelScope.launch {
            locationFlow.collect {
                _uiState.update { state ->
                    state.copy(activeTrackPoints = TrackingService.currentTrackPoints.toList())
                }
            }
        }

        // Trigger download when region changes
        viewModelScope.launch {
            _uiState.map { it.currentRegion }
                .distinctUntilChanged()
                .collect { region ->
                    Timber.d("Collector: currentRegion changed to ${region.displayName}")
                    // The collector will handle automated downloads when region changes
                    // setRegion handles manual/explicit triggers
                    val state = _uiState.value
                    if (state.selectedMapFileName == null || state.selectedMapFileName == region.fileName) {
                        downloadMapAndTheme(region)
                    }
                }
        }

        // Observe POIs
        viewModelScope.launch {
            poiDb.poiDao().getAllPois().collect { poiList ->
                _uiState.update { it.copy(pois = poiList) }
            }
        }

        viewModelScope.launch {
            copyWorldMapIfMissing()
        }
    }

    private fun getThemeFile(): File? {
        settingsRepository.getThemeFilePath()?.let { path ->
            val file = File(path)
            if (file.exists()) {
                Timber.i("Using custom theme file: ${file.path}")
                return file
            }
        }
        
        val selectedTheme = settingsRepository.getSelectedTheme()
        return themeDir?.resolve(selectedTheme.relativePath)
    }

    private fun downloadMapAndTheme(region: MapRegion) {
        Timber.d("downloadMapAndTheme called for region: ${region.displayName}")
        if (_uiState.value.isDownloading) {
            Timber.d("Download already in progress, skipping")
            return
        }
        val mapDir = mapDir ?: run {
            Timber.w("mapDir is null, cannot download map")
            return
        }
        val mapFile = mapDir.resolve(region.fileName)
        Timber.d("Target map file: ${mapFile.absolutePath}")
        
        // Set downloading state immediately to avoid race conditions
        _uiState.update { it.copy(
            isDownloading = true,
            downloadProgress = 0f,
            downloadMessage = getApplication<Application>().getString(R.string.map_loading_progress, region.displayName)
        ) }

        viewModelScope.launch {
            try {
                // Perform file check in background
                val isValid = withContext(Dispatchers.IO) { MapDownloader.isMapFileValid(mapFile) }
                _uiState.update { it.copy(mapFileExists = isValid) }

                val mapSuccess = MapDownloader.downloadMapIfMissing(
                    urlStr = region.downloadUrl,
                    targetFile = mapFile
                ) { progress ->
                    _uiState.update { it.copy(downloadProgress = progress) }
                }
                
                themeDir?.let { ThemeDownloader.extractThemesIfMissing(getApplication(), it) }
                val downloadedTheme = getThemeFile()
                Timber.i("Theme file exists: ${downloadedTheme?.path}")

                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        mapFileExists = mapSuccess,
                        themeFile = downloadedTheme,
                        mapFiles = getMapFilesList() // Refresh map files after potential download
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error in download process")
                _uiState.update { it.copy(isDownloading = false) }
            }
        }
    }

    private suspend fun copyWorldMapIfMissing() {
        withContext(Dispatchers.IO) {
            try {
                val targetDir = mapDir ?: return@withContext
                val worldMapFile = File(targetDir, "world.map")
                if (!MapDownloader.isMapFileValid(worldMapFile)) {
                    targetDir.mkdirs()
                    getApplication<Application>().assets.open("world.map").use { input ->
                        FileOutputStream(worldMapFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Timber.i("Copied world.map from assets to ${worldMapFile.absolutePath}")
                }
            } catch (e: Exception) {
                // Fail silently if asset is not present or copy fails, as it's an optional background map
                Timber.w("Could not copy world.map from assets: ${e.message}")
            }
        }
    }

    fun setScreen(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun setIsAppending(isAppending: Boolean) {
        _uiState.update { it.copy(isAppending = isAppending) }
    }

    fun setRegion(region: MapRegion) {
        Timber.d("setRegion: ${region.displayName}")
        _uiState.update { it.copy(currentRegion = region) }
        
        // Ensure download is checked even if region didn't change (e.g. manual trigger)
        val state = _uiState.value
        Timber.d("Checking if download needed. selectedMapFileName: ${state.selectedMapFileName}, region file: ${region.fileName}")
        
        // Manual call to downloadMapAndTheme should be safe because it checks isDownloading
        downloadMapAndTheme(region)
    }

    fun selectMapFile(fileName: String?) {
        settingsRepository.setSelectedMapFileName(fileName)
        _uiState.update { it.copy(selectedMapFileName = fileName) }
    }

    private fun getMapFilesList(): List<String> {
        return mapDir?.listFiles { file ->
            file.isFile && file.extension.equals("map", ignoreCase = true)
        }?.map { it.name }?.sorted() ?: emptyList()
    }

    fun refreshMapFiles() {
        _uiState.update { it.copy(
            mapFiles = getMapFilesList(),
            graphHopperFolders = getGraphHopperFoldersList()
        ) }
    }

    fun importMapFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                isDownloading = true,
                downloadProgress = -1f,
                downloadMessage = getApplication<Application>().getString(R.string.map_loading_progress, "Import…")
            ) }
            withContext(Dispatchers.IO) {
                try {
                    val rootDir = mapDir ?: return@withContext
                    rootDir.mkdirs()

                    val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst()) cursor.getString(nameIndex) else null
                    } ?: UUID.randomUUID().toString().let { if (it.endsWith(".map")) it else "$it.map" }

                    _uiState.update { it.copy(downloadMessage = getApplication<Application>().getString(R.string.map_loading_progress, fileName)) }

                    if (fileName.lowercase().endsWith(".zip")) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            ZipInputStream(input).use { zipInput ->
                                var entry = zipInput.nextEntry
                                while (entry != null) {
                                    if (!entry.isDirectory && entry.name.lowercase().endsWith(".map")) {
                                        val entryName = File(entry.name).name
                                        val targetFile = File(rootDir, entryName)
                                        FileOutputStream(targetFile).use { output ->
                                            zipInput.copyTo(output)
                                        }
                                        Timber.i("Extracted map to ${targetFile.absolutePath}")
                                    }
                                    zipInput.closeEntry()
                                    entry = zipInput.nextEntry
                                }
                            }
                        }
                    } else {
                        val targetFile = File(rootDir, fileName)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Timber.i("Imported map to ${targetFile.absolutePath}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to import map file")
                } finally {
                    _uiState.update { it.copy(isDownloading = false, mapFiles = getMapFilesList()) }
                }
            }
        }
    }

    fun setFollowGps(enabled: Boolean) {
        settingsRepository.setFollowGps(enabled)
        _uiState.update { it.copy(followGps = enabled) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        settingsRepository.setKeepScreenOn(enabled)
        _uiState.update { it.copy(keepScreenOn = enabled) }
    }

    fun setTargetPosition(position: LatLong?) {
        _uiState.update { it.copy(targetPosition = position) }
        scheduleSave()
    }

    fun setZoomLevel(zoom: Int) {
        _uiState.update { it.copy(zoomLevel = zoom) }
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500.milliseconds)
            val state = _uiState.value
            state.targetPosition?.let {
                settingsRepository.setLastLatitude(it.latitude)
                settingsRepository.setLastLongitude(it.longitude)
            }
            settingsRepository.setLastZoom(state.zoomLevel)
            Timber.d("Persisted map position and zoom")
        }
    }

    fun setLoadedTrackPoints(points: List<RoutePoint>) {
        _uiState.update { it.copy(loadedTrackPoints = points) }
    }

    fun setLoadedTrackName(name: String?) {
        _uiState.update { it.copy(loadedTrackName = name) }
    }

    fun saveCurrentTrack(name: String) {
        Timber.i("Saving track: $name")
        val points = _uiState.value.loadedTrackPoints
        if (points.isEmpty()) return

        viewModelScope.launch {
            val stats = TrackStatsCalculator.calculateStats(points)
            val newTour = TourEntity(
                name = name,
                timestamp = System.currentTimeMillis(),
                totalDistanceKm = stats.totalDistanceKm,
                elevationGainMeters = stats.elevationGainMeters,
                routePoints = points
            )
            db.tourDao().insertTour(newTour)
            Timber.i("Saved track: $name with ${points.size} points")
        }
    }

    fun setThemeFile(file: File?) {
        settingsRepository.setThemeFilePath(file?.absolutePath)
        _uiState.update { it.copy(themeFile = getThemeFile()) }
    }

    fun selectBuiltInTheme(themeId: String) {
        settingsRepository.setSelectedThemeId(themeId)
        settingsRepository.setThemeFilePath(null) // Clear custom theme when selecting built-in
        _uiState.update { it.copy(themeFile = getThemeFile()) }
    }

    fun importThemeFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) cursor.getString(nameIndex) else null
            } ?: "Theme"

            _uiState.update { it.copy(
                isDownloading = true,
                downloadProgress = -1f,
                downloadMessage = getApplication<Application>().getString(R.string.theme_import_progress, fileName)
            ) }
            val file = withContext(Dispatchers.IO) {
                try {
                    val targetDir = externalFilesDir?.resolve("themes/custom") ?: return@withContext null
                    targetDir.mkdirs()
                    val targetFileName = "custom_theme.xml"
                    val targetFile = File(targetDir, targetFileName)
                    
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (targetFile.exists()) targetFile else null
                } catch (e: Exception) {
                    Timber.e(e, "Failed to import theme file")
                    null
                } finally {
                    _uiState.update { it.copy(isDownloading = false) }
                }
            }
            setThemeFile(file)
        }
    }

    fun resetTheme() {
        setThemeFile(null)
        // Trigger redownload/reselect of default theme
        downloadMapAndTheme(_uiState.value.currentRegion)
    }

    fun startTracking(context: Context) {
        context.startService(Intent(context, TrackingService::class.java))
        _uiState.update { it.copy(isTrackingActive = true) }
    }

    fun stopTracking(context: Context) {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = "ACTION_STOP_TRACKING"
        }
        context.startService(intent)
        _uiState.update { it.copy(isTrackingActive = false) }
        Timber.i("Tracking stopped")
    }

    fun addPoi(label: String, description: String?, latLong: LatLong) {
        viewModelScope.launch {
            val poi = PoiEntity(
                label = label,
                description = description,
                latitude = latLong.latitude,
                longitude = latLong.longitude
            )
            poiDb.poiDao().insertPoi(poi)
        }
    }

    fun deletePoi(poi: PoiEntity) {
        viewModelScope.launch {
            poiDb.poiDao().deletePoi(poi)
        }
    }

    private fun getGraphHopperFoldersList(): List<String> {
        return ghRootDir?.listFiles { file -> file.isDirectory }?.map { it.name }?.sorted() ?: emptyList()
    }

    fun selectGraphHopperFolder(folderName: String?) {
        settingsRepository.setGraphHopperFolder(folderName)
        _uiState.update { it.copy(selectedGraphHopperFolder = folderName) }
    }

    fun deleteGraphHopperFolder(folderName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val folder = ghRootDir?.resolve(folderName)
                    if (folder?.exists() == true) {
                        folder.deleteRecursively()
                        Timber.i("Deleted GraphHopper folder: $folderName")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to delete GraphHopper folder: $folderName")
                }
            }
            if (settingsRepository.getGraphHopperFolder() == folderName) {
                selectGraphHopperFolder(null)
            }
            refreshMapFiles()
        }
    }

    fun selectLocomotion(key: String) {
        settingsRepository.setLocomotionKey(key)
        _uiState.update { it.copy(selectedLocomotionKey = key) }
    }

    fun setRoundTripFactor(factor: Float) {
        settingsRepository.setRoundTripFactor(factor)
        _uiState.update { it.copy(roundTripFactor = factor) }
    }

    fun importGraphHopperZip(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                isDownloading = true,
                downloadProgress = -1f,
                downloadMessage = getApplication<Application>().getString(R.string.gh_import_progress, "GHZ")
            ) }
            withContext(Dispatchers.IO) {
                try {
                    val rootDir = ghRootDir ?: return@withContext
                    rootDir.mkdirs()

                    val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst()) cursor.getString(nameIndex) else null
                    } ?: UUID.randomUUID().toString()
                    
                    _uiState.update { it.copy(downloadMessage = getApplication<Application>().getString(R.string.gh_import_progress, fileName)) }
                    
                    val folderName = fileName.substringBeforeLast(".")
                    val targetDir = File(rootDir, folderName)
                    targetDir.mkdirs()

                    context.contentResolver.openInputStream(uri)?.use { input ->
                        ZipInputStream(input).use { zipInput ->
                            var entry = zipInput.nextEntry
                            while (entry != null) {
                                val newFile = File(targetDir, entry.name)
                                if (entry.isDirectory) {
                                    newFile.mkdirs()
                                } else {
                                    newFile.parentFile?.mkdirs()
                                    FileOutputStream(newFile).use { fos ->
                                        zipInput.copyTo(fos)
                                    }
                                }
                                zipInput.closeEntry()
                                entry = zipInput.nextEntry
                            }
                        }
                    }
                    Timber.i("Extracted GHZ to ${targetDir.absolutePath}")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to import GraphHopper GHZ")
                } finally {
                    _uiState.update { it.copy(isDownloading = false, graphHopperFolders = getGraphHopperFoldersList()) }
                }
            }
        }
    }

    private val _searchResults = MutableStateFlow<List<GeocoderResult>>(emptyList())
    val searchResults: StateFlow<List<GeocoderResult>> = _searchResults.asStateFlow()

    fun searchAddress(query: String) {
        if (query.length < 3) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(getApplication())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: List<Address>) {
                            val results = addresses.map { address ->
                                GeocoderResult(
                                    address.getAddressLine(0) ?: "",
                                    LatLong(address.latitude, address.longitude)
                                )
                            }
                            _searchResults.value = results
                        }
                        override fun onError(errorMessage: String?) {
                            Timber.e("Geocoding error: $errorMessage")
                            _searchResults.value = emptyList()
                        }
                    })
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(query, 5)
                    val results = addresses?.map { address ->
                        GeocoderResult(address.getAddressLine(0) ?: "", LatLong(address.latitude, address.longitude))
                    } ?: emptyList()
                    _searchResults.value = results
                }
            } catch (e: Exception) {
                Timber.e(e, "Geocoding failed")
                _searchResults.value = emptyList()
            }
        }
    }
    
    fun setPendingPoiAddress(address: String?) {
        _uiState.update { it.copy(pendingPoiAddress = address) }
    }

    fun getExternalFilesDir() = externalFilesDir
    
    fun getSettingsRepository() = settingsRepository
    fun calculateRoute(context: Context, startLat: Double, startLon: Double, stopLat: Double, stopLon: Double) {
        val folderName = settingsRepository.getGraphHopperFolder() ?: "n52e0103d"
        val ghFolder = ghRootDir?.resolve(folderName)
        viewModelScope.launch {
            val result = GhHelper.ghCalc(
                context,
                ghFolder,
                startLat,
                startLon,
                stopLat,
                stopLon
            )
            if (result.success) {
                setLoadedTrackPoints(result.points)
                setLoadedTrackName(result.name)
            }
        }
    }
    fun calculateRoundtrip(context: Context, startLat: Double, startLon: Double, stopLat: Double, stopLon: Double) {
        val folderName = settingsRepository.getGraphHopperFolder() ?: "n52e0103d"
        val ghFolder = ghRootDir?.resolve(folderName)
        viewModelScope.launch {
            val result = GhHelper.ghCalc(
                context,
                ghFolder,
                startLat,
                startLon,
                stopLat,
                stopLon,
                true, settingsRepository.getRoundTripFactor()
            )
            if (result.success) {
                setLoadedTrackPoints(result.points)
                setLoadedTrackName(result.name)
            }
        }
    }
}

class MainViewModelFactory(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val db: TourDatabase,
    private val poiDb: PoiDatabase,
    private val externalFilesDir: File?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, settingsRepository, db, poiDb, externalFilesDir) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
