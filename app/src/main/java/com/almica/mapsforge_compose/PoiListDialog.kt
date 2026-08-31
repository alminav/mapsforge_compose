package com.almica.mapsforge_compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun PoiListDialog(
    pois: List<PoiEntity>,
    onDismiss: () -> Unit,
    onPoiClick: (PoiEntity) -> Unit,
    onDeletePoi: (PoiEntity) -> Unit,
    onCalculateRoute: (Double, Double) -> Unit,
    onCalculateRoundtrip: (Double, Double) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gespeicherte POIs") },
        text = {
            if (pois.isEmpty()) {
                Text("Keine POIs gespeichert.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                ) {
                    items(pois) { poi ->
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
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen")
            }
        }
    )
}

@Composable
fun PoiListItem(
    poi: PoiEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCalculate: () -> Unit,
    onRoundtrip: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column {
                Text(
                    text = poi.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = String.format(Locale.US, "%.5f, %.5f", poi.latitude, poi.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onCalculate) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = "POI Ziel",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRoundtrip) {
            Icon(
                imageVector = Icons.Default.TripOrigin,
                contentDescription = "POI Roundtrip",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "POI löschen",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
