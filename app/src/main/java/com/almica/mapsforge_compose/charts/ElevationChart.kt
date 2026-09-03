package com.almica.mapsforge_compose.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.LatLng
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.ceil
import com.almica.mapsforge_compose.R

@Composable
fun ElevationChart(
    dataPoints: List<DataPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    gradientStartColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
    gradientEndColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.0f),
    gridColor: Color = Color.LightGray.copy(alpha = 0.3f),
    labelColor: Color = Color.Gray,
    indicatorColor: Color = MaterialTheme.colorScheme.secondary,
    onClose: () -> Unit = {},
    onPointSelected: (DataPoint?) -> Unit = {},
    currentLatLng: LatLng? = null
) {

    Timber.i("ElevationChart: ${dataPoints.size}")
    if (dataPoints.isEmpty()) {
        Text(stringResource(R.string.elevation_chart_no_data), modifier = modifier.padding(16.dp))
        return
    }

    // State für die Interaktion (ausgewählter Punkt)
    var selectedPoint by remember { mutableStateOf<DataPoint?>(null) }
    // Calculate nearest DataPoint to currentLatLng and notify parent
    LaunchedEffect(currentLatLng, dataPoints) {
        if (currentLatLng != null) {
            val nearest = dataPoints.minByOrNull { point ->
                MapUtils.calculateHaversineDistance(currentLatLng,
                    LatLng(point.latitude, point.longitude))
            }
            selectedPoint = nearest
            Timber.i("selectedPoint: ${selectedPoint?.distanceKm}")
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val tooltipStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)

    // Daten-Extrema
    val maxDist = dataPoints.last().distanceKm
    val elevations = dataPoints.map { it.elevationMeters }
    val maxElev = elevations.maxOrNull() ?: 0f
    val minElev = elevations.minOrNull() ?: 0f
    val elevRange = (maxElev - minElev).coerceAtLeast(1f)

    // Intervall-Berechnungen
    val intervalKm = when {
        maxDist <= 5 -> 1f
        maxDist <= 15 -> 2f
        maxDist <= 40 -> 5f
        maxDist <= 100 -> 10f
        else -> 20f
    }
    val intervalElev = when {
        elevRange <= 150 -> 20f
        elevRange <= 400 -> 50f
        elevRange <= 1000 -> 100f
        elevRange <= 2000 -> 200f
        else -> 500f
    }

    // Hilfsfunktion: Findet den Datenpunkt, der am nächsten an der berührten X-Distanz liegt
    fun updateSelectedPoint(touchX: Float, chartWidth: Float, yAxisWidth: Float) {
        if (chartWidth <= 0f) return
        // Berechne die relative Distanz in km aus der X-Touch-Koordinate
        val relativeX = (touchX - yAxisWidth).coerceIn(0f, chartWidth)
        val targetKm = (relativeX / chartWidth) * maxDist

        // Finde den Eintrag mit der geringsten Distanzdifferenz
        selectedPoint = dataPoints.minByOrNull { abs(it.distanceKm - targetKm) }
        onPointSelected(selectedPoint)
    }

    Column(modifier = modifier.padding(16.dp)) {
        // Obere Info-Zeile
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.elevation_chart_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, tint = labelColor)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = stringResource(R.string.elevation_chart_min, minElev.toInt()), fontSize = 14.sp, color = labelColor, fontWeight = FontWeight.Bold)
            Text(text = stringResource(R.string.elevation_chart_max, maxElev.toInt()), fontSize = 14.sp, color = labelColor, fontWeight = FontWeight.Bold)
            Text(text = stringResource(R.string.elevation_chart_total, maxDist), fontSize = 14.sp, color = labelColor, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
                // Touch- und Drag-Gesten verarbeiten
                .pointerInput(dataPoints) {
                    val yAxisWidth = 45.dp.toPx()
                    val chartWidth = size.width - yAxisWidth

                    detectTapGestures(
                        onPress = { offset ->
                            updateSelectedPoint(offset.x, chartWidth, yAxisWidth)
                            tryAwaitRelease()
                            selectedPoint = null // Tooltip beim Loslassen ausblenden
                            onPointSelected(null)
                        }
                    )
                }
                .pointerInput(dataPoints) {
                    val yAxisWidth = 45.dp.toPx()
                    val chartWidth = size.width - yAxisWidth

                    detectDragGestures(
                        onDragStart = { offset: Offset ->
                            updateSelectedPoint(
                                offset.x,
                                chartWidth,
                                yAxisWidth
                            )
                        },
                        onDrag = { change: PointerInputChange, _ ->
                            updateSelectedPoint(
                                change.position.x,
                                chartWidth,
                                yAxisWidth
                            )
                        },
                        onDragEnd = {
                            selectedPoint = null
                            onPointSelected(null)
                        },
                        onDragCancel = {
                            selectedPoint = null
                            onPointSelected(null)
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height

            val yAxisWidth = 45.dp.toPx()
            val xAxisHeight = 24.dp.toPx()

            val chartWidth = width - yAxisWidth
            val chartHeight = height - xAxisHeight

            // ==========================================
            // 1. Y-ACHSE & HORIZONTALE LINIEN
            // ==========================================
            val startElevLabel =
                ceil(minElev / intervalElev) * intervalElev - intervalElev
            var currentElev = startElevLabel
            while (currentElev <= maxElev) {
                if (currentElev >= minElev) {
                    val relativeElev = (currentElev - minElev) / elevRange
                    val y = chartHeight - (relativeElev * chartHeight)

                    drawLine(
                        color = gridColor,
                        start = Offset(yAxisWidth, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )

                    val elevText = "${currentElev.toInt()} m"
                    val textLayout = textMeasurer.measure(elevText, style = labelStyle)
                    val textY = y - (textLayout.size.height / 2)
                    val textX = yAxisWidth - textLayout.size.width - 6.dp.toPx()

                    drawText(
                        textMeasurer,
                        elevText,
                        style = labelStyle,
                        topLeft = Offset(textX.coerceAtLeast(0f), textY)
                    )
                }
                currentElev += intervalElev
            }

            // ==========================================
            // 2. X-ACHSE & VERTIKALE LINIEN
            // ==========================================
            val numberOfLabels = ceil(maxDist / intervalKm).toInt()
            for (i in 0..numberOfLabels) {
                val currentKm = i * intervalKm
                if (currentKm <= maxDist) {
                    val x = yAxisWidth + ((currentKm / maxDist) * chartWidth)

                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, chartHeight),
                        strokeWidth = 1.dp.toPx()
                    )

                    val labelText = "${currentKm.toInt()} km"
                    val textLayout = textMeasurer.measure(labelText, style = labelStyle)
                    val textX = x - (textLayout.size.width / 2)
                    val correctedX = textX.coerceIn(yAxisWidth, width - textLayout.size.width)

                    drawText(
                        textMeasurer,
                        labelText,
                        style = labelStyle,
                        topLeft = Offset(correctedX, chartHeight + 6.dp.toPx())
                    )
                }
            }

            // ==========================================
            // 3. GRAPH-PFADE (KONTUR & GRADIENT)
            // ==========================================
            val strokePath = Path()
            val gradientPath = Path()

            dataPoints.forEachIndexed { index, point ->
                val x = yAxisWidth + ((point.distanceKm / maxDist) * chartWidth)
                val relativeElev = (point.elevationMeters - minElev) / elevRange
                val y = chartHeight - (relativeElev * chartHeight)

                if (index == 0) {
                    strokePath.moveTo(x, y)
                    gradientPath.moveTo(x, y)
                } else {
                    strokePath.lineTo(x, y)
                    gradientPath.lineTo(x, y)
                }
            }

            if (dataPoints.isNotEmpty()) {
                val lastX = yAxisWidth + chartWidth
                gradientPath.lineTo(lastX, chartHeight)
                gradientPath.lineTo(yAxisWidth, chartHeight)
                gradientPath.close()

                drawPath(
                    path = gradientPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(gradientStartColor, gradientEndColor),
                        startY = 0f,
                        endY = chartHeight
                    )
                )
                drawPath(path = strokePath, color = lineColor, style = Stroke(width = 1.dp.toPx()))
            }

            // ==========================================
            // 4. INTERAKTIVER INDIKATOR & TOOLTIP
            // ==========================================
            selectedPoint?.let { point ->
                // Berechne X/Y-Koordinaten des gewählten Punkts auf dem Canvas
                val indicatorX = yAxisWidth + ((point.distanceKm / maxDist) * chartWidth)
                val relativeElev = (point.elevationMeters - minElev) / elevRange
                val indicatorY = chartHeight - (relativeElev * chartHeight)

                // Vertikale Indikatorlinie zeichnen
                drawLine(
                    color = indicatorColor.copy(alpha = 0.6f),
                    start = Offset(indicatorX, 0f),
                    end = Offset(indicatorX, chartHeight), strokeWidth = 2.0.dp.toPx()
                )
                // Kreispunkt auf der Linie zeichnen (Äußerer Ring + Kern)
                drawCircle(
                    color = indicatorColor.copy(alpha = 0.3f),
                    radius = 8.dp.toPx(),
                    center = Offset(indicatorX, indicatorY)
                )
                drawCircle(
                    color = indicatorColor,
                    radius = 4.dp.toPx(),
                    center = Offset(indicatorX, indicatorY)
                )
                // Pop-up-Box (Tooltip) Text vorbereiten
                val tooltipText =
                    "%.1f km | ${point.elevationMeters.toInt()}m".format(point.distanceKm)
                val textLayout = textMeasurer.measure(tooltipText, style = tooltipStyle)
                val paddingX = 8.dp.toPx()
                val paddingY = 4.dp.toPx()
                val boxWidth = textLayout.size.width + (paddingX * 2)
                val boxHeight = textLayout.size.height + (paddingY * 2)
                // Tooltip-Position berechnen (leicht versetzt oberhalb des Fingers, damit er lesbar bleibt)
                var boxX = indicatorX - (boxWidth / 2)
                val boxY = (indicatorY - boxHeight - 12.dp.toPx()).coerceAtLeast(4.dp.toPx())
                // Sicherstellen, dass die Box nicht links oder rechts aus dem Canvas herauswandert
                boxX = boxX.coerceIn(yAxisWidth, width - boxWidth)
                // Tooltip-Hintergrundkarte zeichnen
                drawRect(
                    color = Color.Black.copy(alpha = 0.75f),
                    topLeft = Offset(boxX, boxY),
                    size = Size(boxWidth, boxHeight)
                )


// Text in die Tooltip-Box schreiben
                drawText(textMeasurer = textMeasurer,text = tooltipText,style = tooltipStyle,topLeft = Offset(boxX + paddingX, boxY + paddingY))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ElevationChartPreview() {
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
    RamaniTheme {
        ElevationChart(
            dataPoints = sampleDataPoints,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        )
    }
}

