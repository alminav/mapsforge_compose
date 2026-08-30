package com.almica.mapsforge_compose

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    onBack: () -> Unit,
    onRegionChanged: () -> Unit,
    onFollowGpsChanged: () -> Unit = {},
    onThemeFileSelected: (Uri) -> Unit = {},
    onResetTheme: () -> Unit = {},
    onThemeSelected: (String) -> Unit = {},
    currentThemeFile: File? = null
) {
    BackHandler(onBack = onBack)
    var selectedId by remember { mutableStateOf(repository.getSelectedRegionId()) }
    var selectedThemeId by remember { mutableStateOf(repository.getSelectedThemeId()) }
    var altitudeCorrection by remember { mutableStateOf(repository.getAltitudeCorrection()) }
    var followGps by remember { mutableStateOf(repository.getFollowGps()) }
    var showAltitudeDialog by remember { mutableStateOf(false) }

    val themePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onThemeFileSelected(it) }
    }

    if (showAltitudeDialog) {
        var tempValue by remember { mutableStateOf(altitudeCorrection.toString()) }
        AlertDialog(
            onDismissRequest = { showAltitudeDialog = false },
            title = { Text("Höhenkorrektur") },
            text = {
                Column {
                    Text("Geben Sie den Korrekturwert in Metern ein (z.B. -48.0 für Geoid-Korrektur).")
                    TextField(
                        value = tempValue,
                        onValueChange = { tempValue = it },
                        modifier = Modifier.padding(top = 8.dp),
                        label = { Text("Korrektur (m)") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newVal = tempValue.toFloatOrNull() ?: 0f
                    repository.setAltitudeCorrection(newVal)
                    altitudeCorrection = newVal
                    showAltitudeDialog = false
                }) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAltitudeDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Schließen")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { showAltitudeDialog = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Manuelle Höhenkorrektur", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Aktueller Wert: $altitudeCorrection m", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Karte folgt GPS", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Die Karte wird automatisch auf Ihre aktuelle Position zentriert.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = followGps,
                        onCheckedChange = {
                            repository.setFollowGps(it)
                            followGps = it
                            onFollowGpsChanged()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Integriertes Theme wählen",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(RenderThemes.AVAILABLE_THEMES) { theme ->
                    FilterChip(
                        selected = selectedThemeId == theme.id && repository.getThemeFilePath() == null,
                        onClick = {
                            selectedThemeId = theme.id
                            onThemeSelected(theme.id)
                        },
                        label = { Text(theme.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Karten-Region wählen",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Hinweis: Beim Ändern der Region wird die neue Karte beim nächsten Start der Kartenansicht automatisch geladen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MapRegions.AVAILABLE_REGIONS) { region ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                repository.setSelectedRegionId(region.id)
                                selectedId = region.id
                                onRegionChanged()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (region.id == selectedId) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = region.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(text = region.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (region.id == selectedId) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Ausgewählt",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
