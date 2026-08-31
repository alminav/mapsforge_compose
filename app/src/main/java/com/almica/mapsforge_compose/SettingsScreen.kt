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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.almica.mapsforge_compose.gh.GhHelper
import com.almica.mapsforge_compose.gh.RoundtripValuePickerDialog
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    onBack: () -> Unit,
    onRegionChanged: () -> Unit,
    onFollowGpsChanged: (Boolean) -> Unit = {},
    onThemeFileSelected: (Uri) -> Unit = {},
    onThemeSelected: (String) -> Unit = {},
    ghFolders: List<String> = emptyList(),
    selectedGhFolder: String? = null,
    onGhFolderSelected: (String) -> Unit = {},
    onGhZipImported: (Uri) -> Unit = {},
    selectedLocomotionKey: String = "1.1",
    onLocomotionSelected: (String) -> Unit = {}
) {
    SettingsScreenContent(
        initialSelectedRegionId = repository.getSelectedRegionId(),
        initialSelectedThemeId = repository.getSelectedThemeId(),
        initialAltitudeCorrection = repository.getAltitudeCorrection(),
        initialRoundtripFactor = repository.getRoundTripFactor(),
        initialFollowGps = repository.getFollowGps(),
        themeFilePath = repository.getThemeFilePath(),
        ghFolders = ghFolders,
        selectedGhFolder = selectedGhFolder,
        selectedLocomotionKey = selectedLocomotionKey,
        onBack = onBack,
        onAltitudeCorrectionSaved = { repository.setAltitudeCorrection(it) },
        onFollowGpsToggled = {
            repository.setFollowGps(it)
            onFollowGpsChanged(it)
        },
        onRegionSelected = { regionId ->
            repository.setSelectedRegionId(regionId)
            onRegionChanged()
        },
        onThemeFileSelected = onThemeFileSelected,
        onThemeSelected = onThemeSelected,
        onGhFolderSelected = onGhFolderSelected,
        onGhZipImported = onGhZipImported,
        onLocomotionSelected = onLocomotionSelected,
        onRoundtripFactorSaved = { repository.setRoundTripFactor(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    initialSelectedRegionId: String,
    initialSelectedThemeId: String,
    initialAltitudeCorrection: Float,
    initialFollowGps: Boolean,
    themeFilePath: String?,
    ghFolders: List<String>,
    selectedGhFolder: String?,
    selectedLocomotionKey: String,
    onBack: () -> Unit,
    onAltitudeCorrectionSaved: (Float) -> Unit,
    onFollowGpsToggled: (Boolean) -> Unit,
    onRegionSelected: (String) -> Unit,
    onThemeFileSelected: (Uri) -> Unit,
    onThemeSelected: (String) -> Unit,
    onGhFolderSelected: (String) -> Unit,
    onGhZipImported: (Uri) -> Unit,
    onLocomotionSelected: (String) -> Unit,
    initialRoundtripFactor: Float,
    onRoundtripFactorSaved: (Float) -> Unit,
) {
    BackHandler(onBack = onBack)
    var selectedId by remember { mutableStateOf(initialSelectedRegionId) }
    var selectedThemeId by remember { mutableStateOf(initialSelectedThemeId) }
    var selectedGhFolderId by remember { mutableStateOf(selectedGhFolder) }
    var locomotionKey by remember { mutableStateOf(selectedLocomotionKey) }
    var altitudeCorrection by remember { mutableStateOf(initialAltitudeCorrection) }
    var roundtripFactor by remember { mutableStateOf(initialRoundtripFactor) }
    var followGps by remember { mutableStateOf(initialFollowGps) }
    var showAltitudeDialog by remember { mutableStateOf(false) }
    var showRoundtripDialog by remember { mutableStateOf(false) }

    val themePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onThemeFileSelected(it) }
    }

    val ghZipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onGhZipImported(it) }
    }

    if (showRoundtripDialog) {
        RoundtripValuePickerDialog(
            initialValue = roundtripFactor,
            onDismissRequest = { showRoundtripDialog = false },
            onValueSelected = {
                Timber.i("Selected roundtrip factor: $it")
                showRoundtripDialog = false
                roundtripFactor = it
                onRoundtripFactorSaved(it)
            },
            title = stringResource(R.string.roundtrip_factor)
        )
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
                    onAltitudeCorrectionSaved(newVal)
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
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAltitudeDialog = true },
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
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
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
                                onFollowGpsToggled(it)
                                followGps = it
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRoundtripDialog = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Roundtrip Faktor", style = MaterialTheme.typography.bodyLarge)
                            Text(text = "Aktueller Wert: $roundtripFactor", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            item {
                Text(
                    text = "Fortbewegungsmittel",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(GhHelper.Locomotion.entries) { locomotion ->
                        val selected = locomotionKey == locomotion.key
                        FilterChip(
                            selected = selected,
                            onClick = {
                                locomotionKey = locomotion.key
                                onLocomotionSelected(locomotion.key)
                            },
                            label = { Text(stringResource(locomotion.descriptionRes)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(locomotion.iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            },
                            trailingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GraphHopper Routing-Daten",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { ghZipPickerLauncher.launch("*/*") }) {
                        Text("Import GHZ")
                    }
                }
            }

            if (ghFolders.isEmpty()) {
                item {
                    Text(
                        text = "Keine Routing-Daten gefunden. Importieren Sie eine GHZ-Datei.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (ghFolders.size > 5) {
                items(ghFolders) { folder ->
                    val selected = selectedGhFolderId == folder
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedGhFolderId = folder
                                onGhFolderSelected(folder)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = folder, style = MaterialTheme.typography.bodyLarge)
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(ghFolders) { folder ->
                            val selected = selectedGhFolderId == folder
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedGhFolderId = folder
                                    onGhFolderSelected(folder)
                                },
                                label = { Text(folder) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Text(
                    text = "Integriertes Theme wählen",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(RenderThemes.AVAILABLE_THEMES) { theme ->
                        val selected = selectedThemeId == theme.id && themeFilePath == null
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedThemeId = theme.id
                                onThemeSelected(theme.id)
                            },
                            label = { Text(theme.displayName) },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Text(
                    text = "Karten-Region wählen",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                Text(
                    text = "Hinweis: Beim Ändern der Region wird die neue Karte beim nächsten Start der Kartenansicht automatisch geladen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            items(MapRegions.AVAILABLE_REGIONS) { region ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onRegionSelected(region.id)
                            selectedId = region.id
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

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    SettingsScreenContent(
        initialSelectedRegionId = "niedersachsen",
        initialSelectedThemeId = "cruiser",
        initialAltitudeCorrection = -48.0f,
        initialRoundtripFactor = 0.5f,
        initialFollowGps = true,
        themeFilePath = null,
        ghFolders = listOf(
            "germany_hamburg", 
            "germany_berlin", 
            "germany_munich", 
            "germany_cologne", 
            "germany_frankfurt", 
            "n52e0103d"
        ),
        selectedGhFolder = "n52e0103d",
        selectedLocomotionKey = "1.1",
        onBack = {},
        onAltitudeCorrectionSaved = {},
        onFollowGpsToggled = {},
        onRegionSelected = {},
        onThemeFileSelected = {},
        onThemeSelected = {},
        onGhFolderSelected = {},
        onGhZipImported = {},
        onLocomotionSelected = {},
        onRoundtripFactorSaved = {}
    )
}
