package com.almica.mapsforge_compose.charts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.LatLng
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

@Composable
fun ElevationScreen(file: File, onPointSelected: (DataPoint?) -> Unit, onClose: () -> Unit,
                    viewModel: ElevationViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    val chartData by produceState<List<DataPoint>>(initialValue = persistentListOf(), key1 = file) {
        value = withContext(Dispatchers.IO) {
            try {
                file.inputStream().use { inputStream ->
                    val points = when {
                        file.name.endsWith(".kml", ignoreCase = true) -> KmlParser.parseInputStream(inputStream)
                        file.name.endsWith(".gpx", ignoreCase = true) -> GpxParser.parseInputStream(inputStream)
                        else -> emptyList()
                    }
                    points.toImmutableList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                persistentListOf()
            }
        }
    }

    LaunchedEffect(chartData) {
        if (chartData.isNotEmpty()) {
            viewModel.setRouteData(chartData)
        }
    }

    ElevationScreenContent(chartData, onPointSelected, onClose, uiState)
}

@Composable
fun ElevationScreen(kmlData: String, onPointSelected: (DataPoint?) -> Unit, onClose: () -> Unit,
                    viewModel: ElevationViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val chartData by produceState<List<DataPoint>>(initialValue = persistentListOf(), key1 = kmlData) {
        value = withContext(Dispatchers.IO) {
            try {
                kmlData.byteInputStream().use { inputStream ->
                    KmlParser.parseInputStream(inputStream).toImmutableList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                persistentListOf()
            }
        }
    }

    LaunchedEffect(chartData) {
        if (chartData.isNotEmpty()) {
            viewModel.setRouteData(chartData)
        }
    }

    ElevationScreenContent(chartData, onPointSelected, onClose, uiState)
}

@Composable
private fun ElevationScreenContent(
    chartData: List<DataPoint>,
    onPointSelected: (DataPoint?) -> Unit,
    onClose: () -> Unit,
    uiState: ElevationUiState
) {
    Timber.d("ElevationScreenContent: ${uiState.latLng}")
    Box(modifier = Modifier.fillMaxWidth()) {
        ElevationChart(
            dataPoints = chartData,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            onPointSelected = onPointSelected,
            onClose = {onClose()},
            currentLatLng = uiState.latLng
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ElevationScreenContentPreview() {
    val sampleDataPoints = listOf(
        DataPoint(0f, 100f, 0.0, 0.0),
        DataPoint(1f, 150f, 0.0, 0.0),
        DataPoint(2f, 120f, 0.0, 0.0),
        DataPoint(3f, 180f, 0.0, 0.0),
        DataPoint(4f, 140f, 0.0, 0.0),
        DataPoint(5f, 160f, 0.0, 0.0),
        DataPoint(6f, 110f, 0.0, 0.0),
        DataPoint(7f, 130f, 0.0, 0.0),
        DataPoint(8f, 170f, 0.0, 0.0),
        DataPoint(9f, 190f, 0.0, 0.0),
        DataPoint(10f, 150f, 0.0, 0.0)
    )
    val sampleUiState = ElevationUiState.Loading(
        latLng = LatLng(0.0, 0.0)
    )
    RamaniTheme {
        ElevationScreenContent(
            chartData = sampleDataPoints,
            onPointSelected = {},
            onClose = {},
            uiState = sampleUiState
        )
    }
}
