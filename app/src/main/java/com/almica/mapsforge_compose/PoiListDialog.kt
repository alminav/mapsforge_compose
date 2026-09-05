package com.almica.mapsforge_compose

import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import com.almica.mapsforge_compose.charts.RamaniTheme
import timber.log.Timber
import kotlin.math.sqrt

enum class PoiSortOrder { NAME, DISTANCE }

@Composable
fun PoiListDialog(
    pois: List<PoiEntity>,
    onDismiss: () -> Unit,
    onPoiClick: (PoiEntity) -> Unit,
    onDeletePoi: (PoiEntity) -> Unit,
    onCalculateRoute: (Double, Double) -> Unit,
    onCalculateRoundtrip: (Double, Double) -> Unit,
    mapLocation: RoutePoint?,
    onShowWeather: (PoiEntity) -> Unit
) {
    val isPreview = LocalInspectionMode.current
    var sortOrder by remember { mutableStateOf(PoiSortOrder.NAME) }

    val sortedPois = remember(pois, sortOrder, mapLocation, isPreview) {
        when (sortOrder) {
            PoiSortOrder.NAME -> pois.sortedBy { it.label.lowercase() }
            PoiSortOrder.DISTANCE -> {
                if (mapLocation != null) {
                    pois.sortedBy { poi ->
                        if (isPreview) {
                            // Simple distance approximation for preview to avoid Location.distanceBetween issues
                            val dLat = poi.latitude - mapLocation.latitude
                            val dLon = poi.longitude - mapLocation.longitude
                            (dLat * dLat + dLon * dLon).toFloat()
                        } else {
                            val results = FloatArray(1)
                            Location.distanceBetween(
                                mapLocation.latitude, mapLocation.longitude,
                                poi.latitude, poi.longitude,
                                results
                            )
                            results[0]
                        }
                    }
                } else {
                    pois
                }
            }
        }
    }

    if (isPreview) {
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.poi_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                PoiListContent(
                    sortOrder = sortOrder,
                    onSortOrderChange = { sortOrder = it },
                    mapLocation = mapLocation,
                    sortedPois = sortedPois,
                    onPoiClick = onPoiClick,
                    onDismiss = onDismiss,
                    onDeletePoi = onDeletePoi,
                    onCalculateRoute = onCalculateRoute,
                    onCalculateRoundtrip = onCalculateRoundtrip,
                    onShowWeather = onShowWeather,
                    isPreview = isPreview
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.poi_dialog_title)) },
            text = {
                PoiListContent(
                    sortOrder = sortOrder,
                    onSortOrderChange = { sortOrder = it },
                    mapLocation = mapLocation,
                    sortedPois = sortedPois,
                    onPoiClick = onPoiClick,
                    onDismiss = onDismiss,
                    onDeletePoi = onDeletePoi,
                    onCalculateRoute = onCalculateRoute,
                    onCalculateRoundtrip = onCalculateRoundtrip,
                    onShowWeather = onShowWeather,
                    isPreview = isPreview
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

@Composable
private fun PoiListContent(
    sortOrder: PoiSortOrder,
    onSortOrderChange: (PoiSortOrder) -> Unit,
    mapLocation: RoutePoint?,
    sortedPois: List<PoiEntity>,
    onPoiClick: (PoiEntity) -> Unit,
    onDismiss: () -> Unit,
    onDeletePoi: (PoiEntity) -> Unit,
    onCalculateRoute: (Double, Double) -> Unit,
    onCalculateRoundtrip: (Double, Double) -> Unit,
    onShowWeather: (PoiEntity) -> Unit,
    isPreview: Boolean
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
            val isNameSelected = sortOrder == PoiSortOrder.NAME
            FilterChip(
                selected = isNameSelected,
                onClick = { onSortOrderChange(PoiSortOrder.NAME) },
                label = { Text(stringResource(R.string.poi_sort_name)) },
                leadingIcon = if (isNameSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null
            )
            val isDistanceSelected = sortOrder == PoiSortOrder.DISTANCE
            FilterChip(
                selected = isDistanceSelected,
                onClick = { onSortOrderChange(PoiSortOrder.DISTANCE) },
                label = { Text(stringResource(R.string.poi_sort_distance)) },
                leadingIcon = if (isDistanceSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null,
                enabled = mapLocation != null
            )
        }

        if (sortedPois.isEmpty()) {
            Text(stringResource(R.string.poi_list_empty))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
            ) {
                items(sortedPois) { poi ->
                    PoiListItem(
                        poi = poi,
                        onClick = {
                            onPoiClick(poi)
                            onDismiss()
                        },
                        onDelete = { onDeletePoi(poi) },
                        onCalculate = { onCalculateRoute(poi.latitude, poi.longitude) },
                        onRoundtrip = {
                            onCalculateRoundtrip(poi.latitude, poi.longitude)
                        },
                        distance = mapLocation?.let {
                            if (isPreview) {
                                // Simple distance approximation for preview
                                val dLat = (poi.latitude - it.latitude).toFloat() * 111000f
                                val dLon = (poi.longitude - it.longitude).toFloat() * 111000f
                                sqrt(dLat * dLat + dLon * dLon)
                            } else {
                                val results = FloatArray(1)
                                Location.distanceBetween(
                                    it.latitude, it.longitude,
                                    poi.latitude, poi.longitude,
                                    results
                                )
                                results[0]
                            }
                        }, onShowWeather = {
                            Timber.i("Showing weather for ${poi.label} at ${poi.latitude}, ${poi.longitude}")
                            onShowWeather(poi)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PoiListItem(
    poi: PoiEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCalculate: () -> Unit,
    onRoundtrip: () -> Unit,
    onShowWeather: () -> Unit,
    distance: Float? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onPrimary
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = poi.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val distText = if (distance != null) {
                    if (distance >= 1000) String.format(Locale.US, "%.1f km", distance / 1000f)
                    else String.format(Locale.US, "%.0f m", distance)
                } else {
                    null
                }
                val coords = String.format(Locale.US, "%.3f, %.3f", poi.latitude, poi.longitude)
                Text(
                    text = if (distText != null) "$distText • $coords" else coords,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 40.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onCalculate) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = stringResource(R.string.poi_action_navigate),
                    Modifier.scale(0.8f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRoundtrip) {
                Icon(
                    imageVector = Icons.Default.TripOrigin,
                    contentDescription = stringResource(R.string.poi_action_roundtrip),
                    Modifier.scale(0.8f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onShowWeather) {
                Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = stringResource(R.string.poi_action_weather),
                    Modifier.scale(0.8f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            //Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.poi_action_delete),
                    Modifier.scale(0.8f),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PoiListDialogPreview() {
    val samplePois = listOf(
        PoiEntity(id = 1, label = "Favorite Spot", latitude = 52.5200, longitude = 13.4050),
        PoiEntity(id = 2, label = "Mountain Peak", latitude = 47.2692, longitude = 11.4041),
        PoiEntity(id = 3, label = "Forest Refuge", latitude = 48.2082, longitude = 16.3738)
    )
    val sampleLocation = RoutePoint(latitude = 52.5200, longitude = 13.4050)

    RamaniTheme {
        PoiListDialog(
            pois = samplePois,
            onDismiss = {},
            onPoiClick = {},
            onDeletePoi = {},
            onCalculateRoute = { _, _ -> },
            onCalculateRoundtrip = { _, _ -> },
            onShowWeather = { _ -> },
            mapLocation = sampleLocation,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PoiListItemPreview() {
    val samplePoi = PoiEntity(
        id = 1,
        label = "Sample POI",
        latitude = 52.5200,
        longitude = 13.4050
    )

    RamaniTheme {
        PoiListItem(
            poi = samplePoi,
            onClick = {},
            onDelete = {},
            onCalculate = {},
            onRoundtrip = {},
            onShowWeather = {},
            distance = 1200f
        )
    }
}
