package com.almica.mapsforge_compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val settingsRepository = SettingsRepository(this)
        val db = TourDatabase.getDatabase(this)
        val externalFilesDir = getExternalFilesDir(null)

        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModelFactory(settingsRepository, db, externalFilesDir)
            )
            MainScreen(viewModel)
        }
    }
}
