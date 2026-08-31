package com.almica.mapsforge_compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
            MainScreen(viewModel)
        }
    }
}
