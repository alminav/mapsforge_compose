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
import androidx.compose.ui.Alignment
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
fun SpeedChart(
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

    Timber.i("SpeedChart: ${dataPoints.size}")
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
    val speeds = dataPoints.map { it.speedKmPerHour }
    val maxSpeed = speeds.maxOrNull() ?: 0f
    val minSpeed = speeds.minOrNull() ?: 0f
    val speedRange = (maxSpeed - minSpeed).coerceAtLeast(1f)
    val avgSpeed = 3600000 * dataPoints.last().distanceKm / (dataPoints.last().time - dataPoints.first().time)

    // Intervall-Berechnungen
    val intervalKm = when {
        maxDist <= 5 -> 1f
        maxDist <= 15 -> 2f
        maxDist <= 40 -> 5f
        maxDist <= 100 -> 10f
        else -> 20f
    }
    val intervalSpeed = when {
        speedRange <= 10 -> 2f
        speedRange <= 25 -> 5f
        speedRange <= 50 -> 10f
        speedRange <= 100 -> 20f
        else -> 50f
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

    val baseTitle = stringResource(R.string.speed_chart_title)
    val displayTitle = remember(baseTitle, titleExtension) {
        if (titleExtension != null) "$baseTitle $titleExtension" else baseTitle
    }

    val speedFormat = stringResource(R.string.stat_format_kmh)

    Column(modifier = modifier.padding(16.dp)) {
        // Obere Info-Zeile
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Text(text = stringResource(R.string.speed_chart_min, minSpeed), fontSize = 14.sp, color = labelColor, fontWeight = FontWeight.Bold)
            Text(text = stringResource(R.string.speed_chart_max, maxSpeed), fontSize = 14.sp, color = labelColor, fontWeight = FontWeight.Bold)
            Text(text = stringResource(R.string.speed_average, avgSpeed), fontSize = 14.sp, color = labelColor, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
                // Touch- und Drag-Gesten verarbeiten (kombiniert für bessere Performance und Konsistenz)
                .pointerInput(dataPoints) {
                    val yAxisWidth = 45.dp.toPx()
                    val chartWidth = size.width - yAxisWidth

                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown()
                            updateSelectedPoint(down.position.x, chartWidth, yAxisWidth)

                            drag(down.id) { change ->
                                updateSelectedPoint(change.position.x, chartWidth, yAxisWidth)
                                change.consume()
                            }

                            // Tooltip beim Loslassen ausblenden
                            selectedPoint = null
                            onPointSelected(null)
                        }
                    }
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
            val startSpeedLabel =
                ceil(minSpeed / intervalSpeed) * intervalSpeed - intervalSpeed
            var currentSpeed = startSpeedLabel
            while (currentSpeed <= maxSpeed) {
                if (currentSpeed >= minSpeed) {
                    val relativeSpeed = (currentSpeed - minSpeed) / speedRange
                    val y = chartHeight - (relativeSpeed * chartHeight)

                    drawLine(
                        color = gridColor,
                        start = Offset(yAxisWidth, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )

                    val speedText = "%.0f".format(currentSpeed) // simplified for Y axis
                    val textLayout = textMeasurer.measure(speedText, style = labelStyle)
                    val textY = y - (textLayout.size.height / 2)
                    val textX = yAxisWidth - textLayout.size.width - 6.dp.toPx()

                    drawText(
                        textMeasurer,
                        speedText,
                        style = labelStyle,
                        topLeft = Offset(textX.coerceAtLeast(0f), textY)
                    )
                }
                currentSpeed += intervalSpeed
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
                val relativeSpeed = (point.speedKmPerHour - minSpeed) / speedRange
                val y = chartHeight - (relativeSpeed * chartHeight)

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
                val relativeSpeed = (point.speedKmPerHour - minSpeed) / speedRange
                val indicatorY = chartHeight - (relativeSpeed * chartHeight)

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
                    "%.1f km | %.1f km/h".format(point.distanceKm, point.speedKmPerHour)
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
fun SpeedChartPreview() {
    val sampleDataPoints = listOf(
        DataPoint(0f, 100f, 0.0, 0.0, 0, 10f),
        DataPoint(1f, 150f, 0.0, 0.0, 0, 15f),
        DataPoint(2f, 120f, 0.0, 0.0, 0, 12f),
        DataPoint(3f, 180f, 0.0, 0.0, 0, 20f),
        DataPoint(4f, 140f, 0.0, 0.0, 0, 18f),
        DataPoint(5f, 160f, 0.0, 0.0, 0, 25f),
        DataPoint(6f, 110f, 0.0, 0.0, 0, 22f),
        DataPoint(7f, 130f, 0.0, 0.0, 0, 28f),
        DataPoint(8f, 170f, 0.0, 0.0, 0, 30f),
        DataPoint(9f, 190f, 0.0, 0.0, 0, 25f),
        DataPoint(10f, 150f, 0.0, 0.0, 0, 15f)
    )
    RamaniTheme {
        SpeedChart(
            dataPoints = sampleDataPoints,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            titleExtension = null
        )
    }
}
