package com.almica.mapsforge_compose

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val settingsRepository = SettingsRepository(this)
        val tourDb = TourDatabase.getDatabase(this)
        val poiDb = PoiDatabase.getDatabase(this)
        val externalFilesDir = getExternalFilesDir(null)

        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModelFactory(application, settingsRepository, tourDb, poiDb, externalFilesDir)
            )
            
            val uiState by viewModel.uiState.collectAsState()
            
            LaunchedEffect(uiState.keepScreenOn) {
                if (uiState.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            
            MainScreen(viewModel)
        }
    }
}
