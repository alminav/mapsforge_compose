package com.almica.mapsforge_compose.gh

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almica.mapsforge_compose.R
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RoundtripValuePickerDialog(
    onDismissRequest: () -> Unit,
    onValueSelected: (Float) -> Unit,
    initialValue: Float = 0.5f,
    title: String
) {
    // Generiert exakt 10 Schritte von 0.1f bis 1.0f
    val steps = remember { List(10) { i -> ((i + 1) * 0.1f).roundToOneDecimal() } }

    // Findet den passenden Start-Index (Standard: 0.5)
    val initialIndex = remember(initialValue) {
        steps.indexOfFirst { it == initialValue.roundToOneDecimal() }.coerceAtLeast(0)
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    
    // Accompanist / M3 Fling behavior might not be available in this environment
    // Using default fling behavior if snap is not available or handled via LaunchedEffect
    // For this implementation, we will assume standard LazyColumn behavior or use a simple snap logic

    // Ermittelt den aktuell in der Mitte eingerasteten Wert
    val currentSelectedValue by remember {
        derivedStateOf {
            steps.getOrNull(listState.firstVisibleItemIndex) ?: 0.1f
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = { onValueSelected(currentSelectedValue) }) {
                Text(stringResource(id = R.string.accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(id = android.R.string.cancel))
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color.Green, CircleShape)
                )
                Icon(
                    imageVector = Icons.Outlined.CropFree,
                    contentDescription = null,
                    modifier = Modifier
                        .scale(0.1f + currentSelectedValue, 1f)
                        .rotate(45f)
                        .size(80.dp)
                        .padding(16.dp)
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color.Red, CircleShape)
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                // Visuelle Trennlinien für die mittlere Auswahlzone
//                Column(
//                    modifier = Modifier.height(50.dp),
//                    verticalArrangement = Arrangement.SpaceBetween
//                ) {
//                    HorizontalDivider(modifier = Modifier.fillMaxWidth())
//                    HorizontalDivider(modifier = Modifier.fillMaxWidth())
//                }

                // Wheel Picker via LazyColumn
                LazyColumn(
                    state = listState,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.height(150.dp)
                ) {
                    // Spacer oben (zentriert das erste Element "0.1")
                    item { Box(modifier = Modifier.height(50.dp)) }

                    items(steps.size) { index ->
                        val value = steps[index]
                        val isSelected = value == currentSelectedValue
                        if (isSelected) HorizontalDivider(modifier = Modifier.fillMaxWidth())
                        Box(
                            modifier = Modifier
                                .height(50.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f", value),
                                fontSize = if (isSelected) 22.sp else 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.Unspecified else Color.Gray
                            )
                        }
                        if (isSelected) HorizontalDivider(modifier = Modifier.fillMaxWidth())
                    }

                    // Spacer unten (zentriert das letzte Element "1.0")
                    item { Box(modifier = Modifier.height(50.dp)) }
                }
            }
        }
    )
}

// Verhindert IEEE 754 Float-Ungenauigkeiten (z.B. 0.30000004)
private fun Float.roundToOneDecimal(): Float {
    return (this * 10f).roundToInt() / 10f
}

@Preview(showBackground = true)
@Composable
fun RoundtripValuePickerDialogPreview() {
    MaterialTheme {
        RoundtripValuePickerDialog(
            onDismissRequest = {},
            onValueSelected = {},
            initialValue = 0.5f,
            title = "Select value"
        )
    }
}
