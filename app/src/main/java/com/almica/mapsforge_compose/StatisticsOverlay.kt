package com.almica.mapsforge_compose

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatisticsOverlay(
    stats: TourStatistics,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasPressureSensor = remember(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem(
                label = stringResource(R.string.stat_label_distance),
                value = stringResource(R.string.stat_format_km, stats.totalDistanceKm)
            )
            StatItem(
                label = stringResource(R.string.stat_label_speed),
                value = stringResource(R.string.stat_format_kmh, stats.currentSpeedKmh)
            )
            if (hasPressureSensor) {
                StatItem(
                    label = stringResource(R.string.stat_label_ascent),
                    value = stringResource(R.string.stat_format_meters_plus, stats.elevationGainMeters)
                )
            }
            StatItem(
                label = stringResource(R.string.stat_label_altitude),
                value = stringResource(R.string.stat_format_meters, stats.currentAltitudeMeters)
            )
        }
    }
}

@Composable
fun RowScope.StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
