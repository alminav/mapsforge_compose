package com.almica.mapsforge_compose.charts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.LatLng
import timber.log.Timber
import java.util.Locale
import java.util.Locale.getDefault
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun GradientChart(
    routeEntity: RouteEntity,
    onDismiss: () -> Unit,
    moveMap: (LatLng?) -> Unit,
    viewModel: GradientChartViewModel = viewModel()
) {
    LaunchedEffect(routeEntity) {
        viewModel.loadRoute(routeEntity)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is GradientChartUiState.Loading -> {
            // Potentially show a loading indicator
        }
        is GradientChartUiState.Success -> {
            GradientChartWithSlider(
                state = state,
                onDismiss = onDismiss,
                moveMap = moveMap
            )
        }
    }
}

@Composable
fun GradientChartWithSlider(
    state: GradientChartUiState.Success,
    onDismiss: () -> Unit,
    moveMap: (LatLng?) -> Unit
) {
    val dataModel = state.dataModel

    val currentLatLng by remember(state.points, dataModel) {
        derivedStateOf {
            val idx = dataModel.sliderPosition.roundToInt().coerceIn(state.points.indices)
            if (state.points.isNotEmpty()) {
                val p = state.points[idx]
                LatLng(p.latitude, p.longitude)
            } else null
        }
    }

    val distanceLabel by remember(state.distances, dataModel) {
        derivedStateOf {
            val idx = dataModel.sliderPosition.roundToInt().coerceIn(state.distances.indices)
            if (state.distances.isNotEmpty()) {
                "${(state.distances[idx] / 1000.0).format(1)} km"
            } else "0.0 km"
        }
    }

    Column {
        val title = dataModel.barChartData.second?.let { "$it  ${state.name}" } ?: state.name

        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss"
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().align(Alignment.Center)
            )
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        BarChartRow(
            barChartDataModel = dataModel,
            locationTime = state.locationTime,
            animated = false
        )
        
        Column(modifier = Modifier.padding(start = 32.dp, end = 4.dp)) {
            Slider(
                value = dataModel.sliderPosition,
                onValueChange = {
                    Timber.i("sliderPosition: $it")
                    dataModel.sliderPosition = it
                    moveMap(currentLatLng)
                },
                onValueChangeFinished = {
                    Timber.i("sliderPosition: ${dataModel.sliderPosition}")
                    //result(currentLatLng)
                },
                steps = if (state.points.size > 1) state.points.size - 2 else 0,
                valueRange = 0f..if (state.points.isNotEmpty()) (state.points.size - 1).toFloat() else 0f
            )
            
            Text(
                text = distanceLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

fun Double.format(digits: Int) = "%.${digits}f".format(Locale.ENGLISH, this)
fun Double.formatDistM(bMetric: Boolean): String {
    var value = this
    var sUnit = "km"
    if (!bMetric) {
        value = Const.KM_TO_MILES * this
        sUnit = "mi"
    }

    return if (abs(value) < 1000) String.format(getDefault(), "%.0f%s", this, "m")
    else if (abs(value) < 10000) String.format(
        Locale.ENGLISH,
        "%.1f%s",
        value / 1000,
        sUnit
    )
    else if (abs(value) < 100000) String.format(
        Locale.ENGLISH,
        "%.1f%s",
        value / 1000,
        sUnit
    )
    else String.format(Locale.ENGLISH, "%.0f%s", value / 1000, sUnit)
}



@Preview(showBackground = true)
@Composable
fun GradientChartPreview() {
    val sampleRoute = RouteEntity(
        name = "Sample Route",
        region = "Sample Region",
        latitudeStart = -1.2833,
        longitudeStart = 36.8167,
        distance = 5000.0,
        kmlString = ""
    )
    RamaniTheme {
        GradientChart(
            routeEntity = sampleRoute,
            onDismiss = {},
            moveMap = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GradientChartWithSliderPreview() {
    val samplePoints = listOf(
        LatLngH(-1.2833, 36.8167, 1600.0),
        LatLngH(-1.2843, 36.8177, 1610.0),
        LatLngH(-1.2853, 36.8187, 1620.0),
        LatLngH(-1.2863, 36.8197, 1615.0),
        LatLngH(-1.2873, 36.8207, 1605.0)
    )
    val state = GradientChartUiState.Success(
        name = "Sample Route",
        points = samplePoints,
        distances = listOf(0.0, 150.0, 300.0, 450.0, 600.0),
        dataModel = GradientChartDataModel(samplePoints, -1, 0.6)
    )
    RamaniTheme {
        GradientChartWithSlider(
            state = state,
            onDismiss = {},
            moveMap = {}
        )
    }
}