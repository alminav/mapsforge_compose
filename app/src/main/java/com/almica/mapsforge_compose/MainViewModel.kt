package com.almica.mapsforge_compose

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong
import timber.log.Timber
import java.io.File

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
    val followGps: Boolean = true,
    val targetPosition: LatLong? = null,
    val externalFilesDir: File? = null
)

class MainViewModel(
    private val settingsRepository: SettingsRepository,
    val db: TourDatabase,
    private val externalFilesDir: File?
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            currentRegion = settingsRepository.getSelectedRegion(),
            followGps = settingsRepository.getFollowGps(),
            mapFileExists = externalFilesDir?.let { File(it, settingsRepository.getSelectedRegion().fileName).exists() } ?: false,
            externalFilesDir = externalFilesDir
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
    }

    private fun downloadMapAndTheme(region: MapRegion) {
        if (externalFilesDir == null) return

        val mapFile = File(externalFilesDir, region.fileName)
        val themeDir = File(externalFilesDir, "themes")

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0f) }
            
            val mapSuccess = MapDownloader.downloadMapIfMissing(
                urlStr = region.downloadUrl,
                targetFile = mapFile
            ) { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
            
            val downloadedTheme = ThemeDownloader.downloadThemeIfMissing(themeDir)

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
    }

    fun setLoadedTrackPoints(points: List<RoutePoint>) {
        _uiState.update { it.copy(loadedTrackPoints = points) }
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
    
    fun getExternalFilesDir() = externalFilesDir
    
    fun getSettingsRepository() = settingsRepository
}

class MainViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val db: TourDatabase,
    private val externalFilesDir: File?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(settingsRepository, db, externalFilesDir) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
