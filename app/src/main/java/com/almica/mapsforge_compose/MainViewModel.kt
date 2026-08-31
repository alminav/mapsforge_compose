package com.almica.mapsforge_compose

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mapsforge.core.model.LatLong
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

data class MainUiState(
    val currentScreen: AppScreen = AppScreen.MAP,
    val currentRegion: MapRegion,
    val isDownloading: Boolean = true,
    val downloadProgress: Float = 0f,
    val mapFileExists: Boolean = false,
    val themeFile: File? = null,
    val isTrackingActive: Boolean = false,
    val loadedTrackPoints: List<RoutePoint> = emptyList(),
    val activeTrackPoints: List<RoutePoint> = emptyList(),
    val pois: List<PoiEntity> = emptyList(),
    val followGps: Boolean = true,
    val targetPosition: LatLong? = null,
    val zoomLevel: Int = 12,
    val externalFilesDir: File? = null
)

class MainViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    val db: TourDatabase,
    val poiDb: PoiDatabase,
    private val externalFilesDir: File?
) : AndroidViewModel(application) {

    private val themeDir = externalFilesDir?.resolve("themes")
    private var saveJob: Job? = null

    private val _uiState = MutableStateFlow(
        MainUiState(
            currentRegion = settingsRepository.getSelectedRegion(),
            followGps = settingsRepository.getFollowGps(),
            mapFileExists = externalFilesDir?.resolve(settingsRepository.getSelectedRegion().fileName)?.exists() ?: false,
            externalFilesDir = externalFilesDir,
            themeFile = getThemeFile(),
            targetPosition = if (settingsRepository.getLastLatitude() != 0.0) {
                LatLong(settingsRepository.getLastLatitude(), settingsRepository.getLastLongitude())
            } else null,
            zoomLevel = settingsRepository.getLastZoom()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val locationFlow = TrackingService.locationFlow
    val statsFlow = TrackingService.statsFlow
    val isEcoMode = TrackingService.isEcoMode

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
                    downloadMapAndTheme(region)
                }
        }

        // Observe POIs
        viewModelScope.launch {
            poiDb.poiDao().getAllPois().collect { poiList ->
                _uiState.update { it.copy(pois = poiList) }
            }
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
        val externalDir = externalFilesDir ?: return
        val mapFile = externalDir.resolve(region.fileName)

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0f) }
            
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
                    themeFile = downloadedTheme
                )
            }
        }
    }

    fun setScreen(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun setRegion(region: MapRegion) {
        _uiState.update { it.copy(currentRegion = region) }
    }

    fun setFollowGps(enabled: Boolean) {
        settingsRepository.setFollowGps(enabled)
        _uiState.update { it.copy(followGps = enabled) }
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
            delay(500)
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
            val file = withContext(Dispatchers.IO) {
                try {
                    val targetDir = externalFilesDir?.resolve("themes/custom") ?: return@withContext null
                    targetDir.mkdirs()
                    val fileName = "custom_theme.xml"
                    val targetFile = File(targetDir, fileName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (targetFile.exists()) targetFile else null
                } catch (e: Exception) {
                    Timber.e(e, "Failed to import theme file")
                    null
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
    
    fun getExternalFilesDir() = externalFilesDir
    
    fun getSettingsRepository() = settingsRepository
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
