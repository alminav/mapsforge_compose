package com.almica.mapsforge_compose

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.almica.mapsforge_compose.externalData.MagentaCloud
import com.almica.mapsforge_compose.externalData.MagentaCloudDownloader
import com.almica.mapsforge_compose.gh.Const
import com.almica.mapsforge_compose.gh.GhHelper
import com.almica.mapsforge_compose.gh.RoundtripValuePickerDialog
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import androidx.compose.ui.platform.LocalResources

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    onBack: () -> Unit,
    onRegionChanged: () -> Unit,
    onFollowGpsChanged: (Boolean) -> Unit = {},
    onKeepScreenOnChanged: (Boolean) -> Unit = {},
    onThemeFileSelected: (Uri) -> Unit = {},
    onThemeSelected: (String) -> Unit = {},
    ghFolders: List<String> = emptyList(),
    selectedGhFolder: String? = null,
    onGhFolderSelected: (String) -> Unit = {},
    onGhFolderDeleted: (String) -> Unit = {},
    onGhZipImported: (Uri) -> Unit = {},
    onGhFoldersRefresh: () -> Unit = {},
    selectedLocomotionKey: String = "1.1",
    onLocomotionSelected: (String) -> Unit = {},
    mapFiles: List<String> = emptyList(),
    selectedMapFileName: String? = null,
    onMapFileSelected: (String?) -> Unit = {},
    onMapFileDeleted: (String) -> Unit = {},
    onMapImported: (Uri) -> Unit = {},
    hgtFiles: List<String> = emptyList(),
    selectedHgtFileName: String? = null,
    onHgtFileSelected: (String?) -> Unit = {},
    onHgtFileDeleted: (String) -> Unit = {},
    onHgtImported: (Uri) -> Unit = {},
    onDownloadMap: (MapRegion) -> Unit = {}
) {
    SettingsScreenContent(
        initialSelectedFileName = selectedMapFileName ?: repository.getSelectedRegion().fileName,
        initialSelectedHgtFileName = selectedHgtFileName ?: repository.getSelectedHgtFileName(),
        initialSelectedThemeId = repository.getSelectedThemeId(),
        initialAltitudeCorrection = repository.getAltitudeCorrection(),
        initialRoundtripFactor = repository.getRoundTripFactor(),
        initialFollowGps = repository.getFollowGps(),
        initialKeepScreenOn = repository.getKeepScreenOn(),
        themeFilePath = repository.getThemeFilePath(),
        ghFolders = ghFolders,
        selectedGhFolder = selectedGhFolder,
        selectedLocomotionKey = selectedLocomotionKey,
        mapFiles = mapFiles,
        selectedMapFileName = selectedMapFileName,
        hgtFiles = hgtFiles,
        selectedHgtFileName = selectedHgtFileName,
        onBack = onBack,
        onAltitudeCorrectionSaved = { repository.setAltitudeCorrection(it) },
        onFollowGpsToggled = {
            repository.setFollowGps(it)
            onFollowGpsChanged(it)
        },
        onKeepScreenOnToggled = {
            repository.setKeepScreenOn(it)
            onKeepScreenOnChanged(it)
        },
        onThemeFileSelected = onThemeFileSelected,
        onThemeSelected = onThemeSelected,
        onGhFolderSelected = onGhFolderSelected,
        onGhFolderDeleted = onGhFolderDeleted,
        onGhZipImported = onGhZipImported,
        onGhFoldersRefresh = onGhFoldersRefresh,
        onLocomotionSelected = onLocomotionSelected,
        onRoundtripFactorSaved = { repository.setRoundTripFactor(it) },
        onMapFileSelected = {
            repository.setSelectedMapFileName(it)
            onRegionChanged()
            onMapFileSelected(it)
        },
        onMapFileDeleted = onMapFileDeleted,
        onMapImported = onMapImported,
        onHgtFileSelected = {
            repository.setSelectedHgtFileName(it)
            onHgtFileSelected(it)
        },
        onHgtFileDeleted = onHgtFileDeleted,
        onHgtImported = onHgtImported,
        onDownloadMap = onDownloadMap
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    initialSelectedFileName: String?,
    initialSelectedHgtFileName: String?,
    initialSelectedThemeId: String,
    initialAltitudeCorrection: Float,
    initialFollowGps: Boolean,
    initialKeepScreenOn: Boolean,
    themeFilePath: String?,
    ghFolders: List<String>,
    selectedGhFolder: String?,
    selectedLocomotionKey: String,
    mapFiles: List<String>,
    selectedMapFileName: String?,
    hgtFiles: List<String>,
    selectedHgtFileName: String?,
    onBack: () -> Unit,
    onAltitudeCorrectionSaved: (Float) -> Unit,
    onFollowGpsToggled: (Boolean) -> Unit,
    onKeepScreenOnToggled: (Boolean) -> Unit,
    onThemeFileSelected: (Uri) -> Unit,
    onThemeSelected: (String) -> Unit,
    onGhFolderSelected: (String) -> Unit,
    onGhFolderDeleted: (String) -> Unit,
    onGhZipImported: (Uri) -> Unit,
    onGhFoldersRefresh: () -> Unit,
    onLocomotionSelected: (String) -> Unit,
    initialRoundtripFactor: Float,
    onRoundtripFactorSaved: (Float) -> Unit,
    onMapFileSelected: (String?) -> Unit,
    onMapFileDeleted: (String) -> Unit,
    onMapImported: (Uri) -> Unit,
    onHgtFileSelected: (String?) -> Unit,
    onHgtFileDeleted: (String) -> Unit,
    onHgtImported: (Uri) -> Unit,
    onDownloadMap: (MapRegion) -> Unit
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val downloader: MagentaCloudDownloader = remember { MagentaCloudDownloader(context) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadMessage by remember { mutableStateOf<String?>(null) }

    fun startDownload(
        fileName: String,
        link: String,
        onProcess: suspend (File) -> Unit
    ) {
        Timber.i("Start download of $fileName")
        scope.launch {
            try {
                isDownloading = true
                downloadMessage = resources.getString(R.string.download_starting)
                val cacheFile = File(context.cacheDir, fileName)
                val downloadedFile = downloader.downloadFile(link, cacheFile)

                if (downloadedFile != null) {
                    Timber.i("Download successful: ${downloadedFile.absolutePath}")
                    downloadMessage = resources.getString(R.string.download_success, downloadedFile.name)

                    try {
                        onProcess(downloadedFile)
                        onGhFoldersRefresh()
                    } catch (e: Exception) {
                        Timber.e(e, "Processing failed for $fileName")
                        downloadMessage = resources.getString(R.string.processing_failed)
                    } finally {
                        val bCleanup = downloadedFile.delete()
                        Timber.i("Cleanup: $bCleanup ${downloadedFile.path}")
                    }
                } else {
                    Timber.e("Download failed.")
                    downloadMessage = resources.getString(R.string.download_failed)
                }
            } finally {
                isDownloading = false
            }
        }
    }

    fun startGhzDownload(fileName: String, link: String) = startDownload(fileName, link) { file ->
        val ghRootDir = context.getExternalFilesDir(null)?.resolve(Const.GH_ROOT_FOLDER)
        if (ghRootDir != null) {
            GhHelper.unzipGhFile(context, Uri.fromFile(file), ghRootDir)
        }
    }

    fun startMapDownload(fileName: String, link: String) = startDownload(fileName, link) { file ->
        val mapRootDir = context.getExternalFilesDir(null)?.resolve(Const.MAPFOLDER)
        if (mapRootDir != null) {
            GhHelper.unzipFile(context, Uri.fromFile(file), mapRootDir, extensionFilter = ".map", flatten = true)
        }
    }

    fun startHgtDownload(fileName: String, link: String) = startDownload(fileName, link) { file ->
        val hgtRootDir = context.getExternalFilesDir(null)?.resolve(Const.HGT_FOLDER_NAME)
        if (hgtRootDir != null) {
            GhHelper.unzipFile(context, Uri.fromFile(file), hgtRootDir, flatten = true)
        }
    }

    var selectedThemeId by remember { mutableStateOf(initialSelectedThemeId) }
    var selectedGhFolderId by remember { mutableStateOf(selectedGhFolder) }
    var locomotionKey by remember { mutableStateOf(selectedLocomotionKey) }
    var altitudeCorrection by remember { mutableStateOf(initialAltitudeCorrection) }
    var roundtripFactor by remember { mutableStateOf(initialRoundtripFactor) }
    var followGps by remember { mutableStateOf(initialFollowGps) }
    var keepScreenOn by remember { mutableStateOf(initialKeepScreenOn) }
    var showAltitudeDialog by remember { mutableStateOf(false) }
    var showRoundtripDialog by remember { mutableStateOf(false) }
    var showMapSelectionDialog by remember { mutableStateOf(false) }
    var showHgtSelectionDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }

    val ghZipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onGhZipImported(it) }
    }

    val mapPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onMapImported(it) }
    }

    val hgtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onHgtImported(it) }
    }

    if (showMapSelectionDialog) {
        MapSelectionDialog(
            mapFiles = mapFiles,
            selectedMapFileName = selectedMapFileName,
            onDismissRequest = { showMapSelectionDialog = false },
            onMapSelected = {
                onMapFileSelected(it)
                //showMapSelectionDialog = false
            },
            onMapDeleted = onMapFileDeleted,
            onImportMap = {
                mapPickerLauncher.launch("*/*")
                showMapSelectionDialog = false
            }
        )
    }

    if (showHgtSelectionDialog) {
        HgtSelectionDialog(
            hgtFiles = hgtFiles,
            selectedHgtFileName = selectedHgtFileName,
            onDismissRequest = { showHgtSelectionDialog = false },
            onHgtSelected = {
                onHgtFileSelected(it)
            },
            onHgtDeleted = onHgtFileDeleted,
            onImportHgt = {
                hgtPickerLauncher.launch("*/*")
                showHgtSelectionDialog = false
            }
        )
    }

    if (showDownloadDialog) {
        DownloadMapDialog(
            onDismissRequest = { showDownloadDialog = false },
            onRegionSelected = { region ->
                onDownloadMap(region)
                showDownloadDialog = false
            }
        )
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

    if (isDownloading || downloadMessage != null) {
        AlertDialog(
            onDismissRequest = { if (!isDownloading) downloadMessage = null },
            title = { Text(if (isDownloading) "Download läuft" else "Download Status") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }
                    downloadMessage?.let { Text(it) }
                }
            },
            confirmButton = {
                if (!isDownloading) {
                    TextButton(onClick = { downloadMessage = null }) {
                        Text("OK")
                    }
                }
            }
        )
    }

    val pagerState = rememberPagerState(pageCount = { 3 })
    val tabs = listOf("Allgemein", "Karten", "Routing")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Einstellungen") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Schließen")
                        }
                    }
                )
                SecondaryTabRow(selectedTabIndex = pagerState.currentPage) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) { page ->
            when (page) {
                0 -> GeneralSettingsTab(
                    altitudeCorrection = altitudeCorrection,
                    onAltitudeClick = { showAltitudeDialog = true },
                    followGps = followGps,
                    onFollowGpsToggled = {
                        onFollowGpsToggled(it)
                        followGps = it
                    },
                    keepScreenOn = keepScreenOn,
                    onKeepScreenOnToggled = {
                        onKeepScreenOnToggled(it)
                        keepScreenOn = it
                    },
                    selectedHgtFileName = selectedHgtFileName,
                    onHgtSelectionClick = { showHgtSelectionDialog = true },
                    onHgtFileReset = { onHgtFileSelected(null) },
                    onDownloadHgt = {
                        name, link ->
                        Timber.i("Download HGT: $name $link")
                        startHgtDownload(name, link)
                    },
                    hgtFiles = hgtFiles
                )
                1 -> MapSettingsTab(
                    mapFiles = mapFiles,
                    selectedMapFileName = selectedMapFileName,
                    onMapSelectionClick = { showMapSelectionDialog = true },
                    onMapFileReset = { onMapFileSelected(null) },
                    onDownloadClick = { showDownloadDialog = true },
                    selectedThemeId = selectedThemeId,
                    themeFilePath = themeFilePath,
                    onThemeSelected = {
                        selectedThemeId = it
                        onThemeSelected(it)
                    },
                    onDownloadMap = { name, link ->
                        Timber.i("Download GHZ: $name $link")
                        startMapDownload(name, link) }
                )
                2 -> RoutingSettingsTab(
                    roundtripFactor = roundtripFactor,
                    onRoundtripClick = { showRoundtripDialog = true },
                    locomotionKey = locomotionKey,
                    onLocomotionSelected = {
                        locomotionKey = it
                        onLocomotionSelected(it)
                    },
                    ghFolders = ghFolders,
                    selectedGhFolderId = selectedGhFolderId,
                    onGhFolderSelected = {
                        selectedGhFolderId = it
                        onGhFolderSelected(it)
                    },
                    onGhFolderDeleted = onGhFolderDeleted,
                    onImportGhZip = { ghZipPickerLauncher.launch("*/*") },
                    onDownloadGhz = { name, link ->
                        Timber.i("Download GHZ: $name $link")
                        startGhzDownload(name, link) }
                )
            }
        }
    }
}

@Composable
fun GeneralSettingsTab(
    altitudeCorrection: Float,
    onAltitudeClick: () -> Unit,
    followGps: Boolean,
    onFollowGpsToggled: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnToggled: (Boolean) -> Unit,
    selectedHgtFileName: String?,
    onHgtSelectionClick: () -> Unit,
    onHgtFileReset: () -> Unit,
    onDownloadHgt: (String, String) -> Unit,
    hgtFiles: List<String>
) {
    val context = LocalContext.current
    val hgtItems by remember(hgtFiles) {
        derivedStateOf {
            val hgtRootFolder = File(context.getExternalFilesDir(null), Const.HGT_FOLDER_NAME)
            MagentaCloud.hgt
                .filterValues { it.isNotEmpty() }
                .toSortedMap()
                .map { (fileName, url) ->
                    val realFileName = fileName.removeSuffix(Const.ZIP_EXTENSION)
                        .replace(Const.HGT_FOLDER_NAME, "." + Const.HGT_FOLDER_NAME)
                    val hgtFile = File(hgtRootFolder, realFileName)
                    DownloadItem(
                        fileName = fileName,
                        downloadUrl = url,
                        isDownloaded = hgtFile.exists()
                    )
                }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAltitudeClick() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Höhenkorrektur", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Aktueller Wert: $altitudeCorrection m", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

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
                        onCheckedChange = onFollowGpsToggled
                    )
                }
            }
        }

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
                        Text(text = "Bildschirm eingeschaltet lassen", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Verhindert, dass der Bildschirm während der Nutzung dunkel wird.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = onKeepScreenOnToggled
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).clickable { onHgtSelectionClick() }) {
                        Text(text = "HGT Wahl", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (selectedHgtFileName != null) "Gewählt: $selectedHgtFileName" else "Keine HGT Datei gewählt",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (selectedHgtFileName != null) {
                        TextButton(onClick = onHgtFileReset) {
                            Text("Reset")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Verfügbare HGT-Downloads",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(hgtItems, key = { it.fileName }) { item ->
                        DownloadItemRow(
                            item = item,
                            onDownloadClick = { onDownloadHgt(item.fileName, item.downloadUrl) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MapSettingsTab(
    mapFiles: List<String>,
    selectedMapFileName: String?,
    onMapSelectionClick: () -> Unit,
    onMapFileReset: () -> Unit,
    onDownloadClick: () -> Unit,
    selectedThemeId: String,
    themeFilePath: String?,
    onThemeSelected: (String) -> Unit,
    onDownloadMap: (String, String) -> Unit
) {
    val mapsItems by remember(mapFiles) {
        derivedStateOf {
            MagentaCloud.maps
                .filterValues { it.isNotEmpty() }
                .toSortedMap()
                .map { (fileName, url) ->
                    val realFileName = fileName.removeSuffix(Const.ZIP_EXTENSION)
                    DownloadItem(
                        fileName = fileName,
                        downloadUrl = url,
                        isDownloaded = mapFiles.contains(realFileName)
                    )
                }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).clickable { onMapSelectionClick() }) {
                        Text(text = "Kartenwahl", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (selectedMapFileName != null) "Gewählt: $selectedMapFileName" else "Keine manuelle Karte gewählt",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDownloadClick) {
                            Text("Download")
                        }
                        if (selectedMapFileName != null) {
                            TextButton(onClick = onMapFileReset) {
                                Text("Reset")
                            }
                        }
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
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(RenderThemes.AVAILABLE_THEMES) { theme ->
                    val selected = selectedThemeId == theme.id && themeFilePath == null
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilterChip(
                            selected = selected,
                            onClick = { onThemeSelected(theme.id) },
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
                        val themePreview = RenderPreviews.AVAILABLE_PREVIEWS.find { it.id == theme.id }
                        if (themePreview != null) {
                            Image(
                                painter = painterResource(themePreview.imageResId),
                                contentDescription = null,
                                modifier = Modifier.size(120.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                text = "Verfügbare Map-Downloads",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(mapsItems, key = { it.fileName }) { item ->
                        DownloadItemRow(
                            item = item,
                            onDownloadClick = { onDownloadMap(item.fileName, item.downloadUrl) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoutingSettingsTab(
    roundtripFactor: Float,
    onRoundtripClick: () -> Unit,
    locomotionKey: String,
    onLocomotionSelected: (String) -> Unit,
    ghFolders: List<String>,
    selectedGhFolderId: String?,
    onGhFolderSelected: (String) -> Unit,
    onGhFolderDeleted: (String) -> Unit,
    onImportGhZip: () -> Unit,
    onDownloadGhz: (String, String) -> Unit
) {
// In RoutingSettingsTab
    val ghItems by remember(ghFolders) {
        derivedStateOf {
            MagentaCloud.gh
                .filterValues { it.isNotEmpty() }
                .toSortedMap()
                .map { (fileName, url) ->
                    val folderName = fileName.removeSuffix(Const.GHZ_EXTENSION)
                    DownloadItem(
                        fileName = fileName,
                        downloadUrl = url,
                        isDownloaded = ghFolders.contains(folderName)
                    )
                }
        }
    }
    var folderToDelete by remember { mutableStateOf<String?>(null) }

    if (folderToDelete != null) {
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(stringResource(R.string.delete_gh_folder_title)) },
            text = { Text(stringResource(R.string.delete_gh_folder_message, folderToDelete ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    folderToDelete?.let { onGhFolderDeleted(it) }
                    folderToDelete = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRoundtripClick() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(GhHelper.Locomotion.entries) { locomotion ->
                    val selected = locomotionKey == locomotion.key
                    FilterChip(
                        selected = selected,
                        onClick = { onLocomotionSelected(locomotion.key) },
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

        //item { Spacer(modifier = Modifier.height(8.dp)) }

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
                TextButton(onClick = onImportGhZip) {
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
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(ghFolders) { folder ->
                            val selected = selectedGhFolderId == folder
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onGhFolderSelected(folder) }
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = folder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    IconButton(onClick = { folderToDelete = folder }) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Löschen")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            Text(
                text = "Verfügbare Routing-Downloads",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(ghItems, key = { it.fileName }) { item ->
                        DownloadItemRow(
                            item = item,
                            onDownloadClick = { onDownloadGhz(item.fileName, item.downloadUrl) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadItemRow(
    item: DownloadItem,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !item.isDownloaded) { onDownloadClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.fileName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = if (item.isDownloaded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = if (item.isDownloaded) Icons.Default.Check else Icons.Default.Download,
            contentDescription = if (item.isDownloaded) "Downloaded" else "Download",
            tint = if (item.isDownloaded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun MapSelectionDialog(
    mapFiles: List<String>,
    selectedMapFileName: String?,
    onDismissRequest: () -> Unit,
    onMapSelected: (String) -> Unit,
    onMapDeleted: (String) -> Unit,
    onImportMap: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Karte wählen")
                TextButton(onClick = onImportMap) {
                    Text("Import")
                }
            }
        },
        text = {
            if (mapFiles.isEmpty()) {
                Text("Keine Karten im Ordner gefunden. Importieren Sie eine .map Datei.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mapFiles) { fileName ->
                        val isSelected = fileName == selectedMapFileName
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMapSelected(fileName) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            IconButton(
                                onClick = { onMapDeleted(fileName) },
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Löschen"
                                )
                            }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Schließen")
            }
        }
    )
}

@Composable
fun DownloadMapDialog(
    onDismissRequest: () -> Unit,
    onRegionSelected: (MapRegion) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Karte herunterladen") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MapRegions.AVAILABLE_REGIONS.filter { it.downloadUrl.isNotEmpty() }) { region ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRegionSelected(region) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = region.displayName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
fun HgtSelectionDialog(
    hgtFiles: List<String>,
    selectedHgtFileName: String?,
    onDismissRequest: () -> Unit,
    onHgtSelected: (String) -> Unit,
    onHgtDeleted: (String) -> Unit,
    onImportHgt: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("HGT wählen")
                TextButton(onClick = onImportHgt) {
                    Text("Import")
                }
            }
        },
        text = {
            if (hgtFiles.isEmpty()) {
                Text("Keine HGT Dateien gefunden.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(hgtFiles) { fileName ->
                        val isSelected = fileName == selectedHgtFileName
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onHgtSelected(fileName) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                IconButton(
                                    onClick = { onHgtDeleted(fileName) },
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Löschen"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Schließen")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    SettingsScreenContent(
        initialSelectedFileName = "world.map",
        initialSelectedHgtFileName = "N52E010.hgt",
        initialSelectedThemeId = "cruiser",
        initialAltitudeCorrection = -48.0f,
        initialRoundtripFactor = 0.5f,
        initialFollowGps = true,
        initialKeepScreenOn = false,
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
        mapFiles = listOf("niedersachsen.map", "berlin.map", "world.map"),
        selectedMapFileName = "world.map",
        hgtFiles = listOf("N52E010.hgt", "N53E009.hgt"),
        selectedHgtFileName = "N52E010.hgt",
        onBack = {},
        onAltitudeCorrectionSaved = {},
        onFollowGpsToggled = {},
        onKeepScreenOnToggled = {},
        onThemeFileSelected = {},
        onThemeSelected = {},
        onGhFolderSelected = {},
        onGhFolderDeleted = {},
        onGhZipImported = {},
        onGhFoldersRefresh = {},
        onLocomotionSelected = {},
        onRoundtripFactorSaved = {},
        onMapFileSelected = {},
        onMapFileDeleted = {},
        onMapImported = {},
        onHgtFileSelected = {},
        onHgtFileDeleted = {},
        onHgtImported = {},
        onDownloadMap = {}
    )
}

data class DownloadItem(
    val fileName: String,
    val downloadUrl: String,
    val isDownloaded: Boolean = false
)
