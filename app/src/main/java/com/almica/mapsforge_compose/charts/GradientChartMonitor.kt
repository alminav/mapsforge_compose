package com.almica.mapsforge_compose.charts

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.almica.composecharts.charts.bar.BarChartAdjustableAnimation
import com.almica.mapsforge_compose.R
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import timber.log.Timber
import java.util.Locale
import kotlin.math.max

/**
 * Created by bytebeats on 2021/9/30 : 19:53
 * E-mail: happychinapc@gmail.com
 * Quote: Peasant. Educated. Worker
 */
/**
 * 17apr2026
 * liveSharedPreferences replaced by GpsViewModel Observer for time
 */
@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradientChartMonitor(
    routeName: String,
    barChartDataModel: GradientChartDataModel,
    offsetYByPercent: Float,
    homeIcon: ImageVector,
    animated: Boolean,
    result: (LatLng?) -> Unit,
) {
    val routeDisplay = remember(routeName) {
        if (routeName.length > 20) {
            routeName.substring(0..20) + Const.UC_THREEDOTS
        } else routeName
    }

    DisposableEffect(LocalLifecycleOwner.current) {
        onDispose {
            Timber.i("onDispose")
        }
    }

    Surface(
        modifier = Modifier.offsetYByPercent(offsetYByPercent)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            result(null)
                        }
                    ) {
                        Icon(
                            imageVector = homeIcon,
                            contentDescription = "Go back home"
                        )
                    }

                    if (barChartDataModel.barChartData.second != null)
                        Text(
                            text = "${barChartDataModel.barChartData.second}  $routeDisplay",
                            fontSize = 14.sp
                        )
                    else
                        Text(text = routeDisplay, fontSize = 14.sp)
                }

                val hasData = barChartDataModel.barChartData.first != null
                
                AnimatedVisibility(visible = !hasData) {
                    Timber.i("%s", stringResource(R.string.no_relevant_chart_data))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.no_relevant_chart_data),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                AnimatedVisibility(visible = hasData) {
                    val locationTime = remember { System.currentTimeMillis() }
                    GradientChartContent(
                        barChartDataModel = barChartDataModel,
                        locationTime = locationTime,
                        animated = animated
                    )
                }
            }
        }
    }
}

fun Int.format(digits: Int) = "%0${digits}d".format(Locale.ENGLISH,this)

@Composable
private fun GradientChartContent(barChartDataModel: GradientChartDataModel, locationTime: Long, animated: Boolean) {
    Column(
//        modifier = modifier.padding(
//            horizontal = Margin.horizontal,
//            vertical = Margin.verticalMedium
//        )
    ) {
        BarChartRow(barChartDataModel = barChartDataModel, locationTime, animated)
    }
}

@Composable
fun BarChartRow(barChartDataModel: GradientChartDataModel, locationTime: Long, animated: Boolean) {
    Column {
//        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
//            Text(text = barChartDataModel.barChartData.second)
//        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            barChartDataModel.barChartData.first?.let {
                //Timber.i( "${barChartDataModel.labelDrawer.drawLocation}")
                BarChartAdjustableAnimation(
                    barChartData = it,
                    labelDrawer = barChartDataModel.labelDrawer,
                    routePointer = barChartDataModel.routePointer,
                    locationTime = locationTime,
                    animated = animated
                )
            }
        }
    }
}

fun reducedLllhKmSteps(lllh: List<LatLngH>): List<LatLngH> {
    if (lllh.isEmpty()) return emptyList()
    val originalDistanceKm = (1 + (0.001 * calcDistMeter(lllh)).toInt())
    val stepWidth = max(1, lllh.size / originalDistanceKm)

    Timber.i("originalDistanceKm: $originalDistanceKm stepWidth: $stepWidth")

    val reducedListLatLngH = lllh.filterIndexed { index, _ ->
        index % stepWidth == 0
    }.take(originalDistanceKm)

    Timber.i("reduce: ${lllh.size} --> ${reducedListLatLngH.size}")
    return reducedListLatLngH
}

fun calcDistMeter(listLatLng: List<LatLngH>?): Double {
    return listLatLng?.zipWithNext { a, b ->
        SphericalUtil.computeDistanceBetween(a.latLng, b.latLng)
    }?.sum() ?: 0.0
}

@Preview(showBackground = true)
@Composable
fun GradientChartMonitorPreview() {
    val samplePoints = listOf(
        LatLngH(0.0, 0.0, 100.0),
        LatLngH(0.01, 0.01, 150.0)
    )
    val model = remember { GradientChartDataModel(samplePoints, 0, 1500.0) }
    
    RamaniTheme {
        GradientChartMonitor(
            routeName = "Sample Route",
            barChartDataModel = model,
            offsetYByPercent = 0f,
            homeIcon = Icons.Default.Home,
            animated = false,
            result = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BarChartRowPreview() {
    val sampleLllh = arrayListOf(
        LatLngH(0.0, 0.0, 100.0),
        LatLngH(0.01, 0.01, 150.0),
        LatLngH(0.02, 0.02, 120.0),
        LatLngH(0.03, 0.03, 180.0),
        LatLngH(0.04, 0.04, 160.0)
    )
    val model = remember { GradientChartDataModel(sampleLllh, 2, 5000.0) }
    val locationTime = remember { System.currentTimeMillis() }
    RamaniTheme {
        BarChartRow(
            barChartDataModel = model,
            locationTime = locationTime,
            animated = false
        )
    }
}
