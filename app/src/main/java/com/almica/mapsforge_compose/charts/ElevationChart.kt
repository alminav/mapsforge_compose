package com.almica.mapsforge_compose.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
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
    modifier: Modifier = Modifier,
    dataPoints: List<DataPoint>,
    titleExtension: String?,
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
    val labelStyle = remember(labelColor) { TextStyle(color = labelColor, fontSize = 10.sp) }
    val tooltipStyle = remember { TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }

    val metersFormat = stringResource(R.string.stat_format_meters)

    // Daten-Extrema
    val extrema = remember(dataPoints) {
        val maxDist = dataPoints.lastOrNull()?.distanceKm ?: 0f
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

        object {
            val maxDist = maxDist
            val maxElev = maxElev
            val minElev = minElev
            val elevRange = elevRange
            val intervalKm = intervalKm
            val intervalElev = intervalElev
        }
    }

    val maxDist = extrema.maxDist
    val maxElev = extrema.maxElev
    val minElev = extrema.minElev
    val elevRange = extrema.elevRange
    val intervalKm = extrema.intervalKm
    val intervalElev = extrema.intervalElev

    val density = LocalDensity.current
    // Pre-calculate Y labels and determine needed width
    val yAxisInfo = remember(extrema, metersFormat, labelStyle, textMeasurer, density) {
        val labels = mutableListOf<Triple<Float, String, TextLayoutResult>>()
        val startElevLabel = ceil(minElev / intervalElev) * intervalElev - intervalElev
        var currentElev = startElevLabel
        var maxWidth = 0f
        while (currentElev <= maxElev) {
            if (currentElev >= minElev) {
                val text = metersFormat.format(currentElev)
                val layout = textMeasurer.measure(text, style = labelStyle)
                labels.add(Triple(currentElev, text, layout))
                maxWidth = maxOf(maxWidth, layout.size.width.toFloat())
            }
            currentElev += intervalElev
        }
        val padding = with(density) { 12.dp.toPx() }
        labels to (maxWidth + padding)
    }
    val yAxisLabels = yAxisInfo.first
    val yAxisWidthPx = yAxisInfo.second

    val xAxisLabels = remember(extrema, intervalKm, labelStyle, textMeasurer) {
        val labels = mutableListOf<Pair<Float, TextLayoutResult>>()
        val numberOfLabels = ceil(maxDist / intervalKm).toInt()
        for (i in 0..numberOfLabels) {
            val currentKm = i * intervalKm
            if (currentKm <= maxDist) {
                val labelText = "${currentKm.toInt()} km"
                val textLayout = textMeasurer.measure(labelText, style = labelStyle)
                labels.add(currentKm to textLayout)
            }
        }
        labels
    }

    // Hilfsfunktion: Findet den Datenpunkt, der am nächsten an der berührten X-Distanz liegt (optimiert mit Binary Search)
    fun updateSelectedPoint(touchX: Float, chartWidth: Float, yAxisWidth: Float) {
        if (chartWidth <= 0f || dataPoints.isEmpty()) return
        val relativeX = (touchX - yAxisWidth).coerceIn(0f, chartWidth)
        val targetKm = (relativeX / chartWidth) * maxDist

        val index = dataPoints.binarySearch { it.distanceKm.compareTo(targetKm) }
        val nearestIndex = if (index >= 0) {
            index
        } else {
            val insertionPoint = -(index + 1)
            when {
                insertionPoint == 0 -> 0
                insertionPoint >= dataPoints.size -> dataPoints.size - 1
                else -> {
                    val p1 = dataPoints[insertionPoint - 1]
                    val p2 = dataPoints[insertionPoint]
                    if (abs(p1.distanceKm - targetKm) < abs(p2.distanceKm - targetKm)) {
                        insertionPoint - 1
                    } else {
                        insertionPoint
                    }
                }
            }
        }
        selectedPoint = dataPoints[nearestIndex]
        onPointSelected(selectedPoint)
    }

    val baseTitle = stringResource(R.string.elevation_chart_title)
    val displayTitle = remember(baseTitle, titleExtension) {
        if (titleExtension != null) "$baseTitle $titleExtension" else baseTitle
    }

    Column(modifier = modifier.padding(16.dp)) {
        // Obere Info-Zeile
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = displayTitle,
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
                // Touch- und Drag-Gesten verarbeiten (kombiniert für bessere Performance und Konsistenz)
                .pointerInput(dataPoints, yAxisWidthPx) {
                    val chartWidth = size.width - yAxisWidthPx

                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown()
                            updateSelectedPoint(down.position.x, chartWidth, yAxisWidthPx)

                            drag(down.id) { change ->
                                updateSelectedPoint(change.position.x, chartWidth, yAxisWidthPx)
                                change.consume()
                            }
                            // Selection stays until next touch
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height

            val xAxisHeight = 24.dp.toPx()

            val chartWidth = width - yAxisWidthPx
            val chartHeight = height - xAxisHeight

            // ==========================================
            // 1. Y-ACHSE & HORIZONTALE LINIEN
            // ==========================================
            yAxisLabels.forEach { (elev, text, textLayout) ->
                val relativeElev = (elev - minElev) / elevRange
                val y = chartHeight - (relativeElev * chartHeight)

                drawLine(
                    color = gridColor,
                    start = Offset(yAxisWidthPx, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )

                val textY = y - (textLayout.size.height.toFloat() / 2)
                val textX = yAxisWidthPx - textLayout.size.width.toFloat() - 6.dp.toPx()

                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(textX.coerceAtLeast(0f), textY)
                )
            }

            // ==========================================
            // 2. X-ACHSE & VERTIKALE LINIEN
            // ==========================================
            xAxisLabels.forEach { (currentKm, textLayout) ->
                val x = yAxisWidthPx + ((currentKm / maxDist) * chartWidth)

                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, chartHeight),
                    strokeWidth = 1.dp.toPx()
                )

                val textX = x - (textLayout.size.width.toFloat() / 2)
                val correctedX = textX.coerceIn(yAxisWidthPx, width - textLayout.size.width.toFloat())

                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(correctedX, chartHeight + 6.dp.toPx())
                )
            }

            // ==========================================
            // 3. GRAPH-PFADE (KONTUR & GRADIENT)
            // ==========================================
            val strokePath = Path()
            val gradientPath = Path()

            if (dataPoints.isNotEmpty()) {
                dataPoints.forEachIndexed { index, point ->
                    val x = yAxisWidthPx + ((point.distanceKm / maxDist) * chartWidth)
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

                val lastX = yAxisWidthPx + chartWidth
                gradientPath.lineTo(lastX, chartHeight)
                gradientPath.lineTo(yAxisWidthPx, chartHeight)
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
                val indicatorX = yAxisWidthPx + ((point.distanceKm / maxDist) * chartWidth)
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
                boxX = boxX.coerceIn(yAxisWidthPx, width - boxWidth)
                // Tooltip-Hintergrundkarte zeichnen
                drawRect(
                    color = Color.Black.copy(alpha = 0.75f),
                    topLeft = Offset(boxX, boxY),
                    size = Size(boxWidth, boxHeight)
                )


                // Text in die Tooltip-Box schreiben
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(boxX + paddingX, boxY + paddingY)
                )
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
            titleExtension = null
        )
    }
}

