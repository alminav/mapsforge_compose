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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
    if (dataPoints.isEmpty()) {
        Text(
            text = stringResource(R.string.elevation_chart_no_data),
            modifier = modifier.padding(16.dp)
        )
        return
    }

    // Interactive state (selected point)
    var selectedPoint by remember { mutableStateOf<DataPoint?>(null) }

    // Sync selectedPoint with external LatLng
    LaunchedEffect(currentLatLng, dataPoints) {
        if (currentLatLng != null) {
            selectedPoint = dataPoints.minByOrNull { point ->
                MapUtils.calculateHaversineDistance(currentLatLng, LatLng(point.latitude, point.longitude))
            }
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember(labelColor) { TextStyle(color = labelColor, fontSize = 10.sp) }
    val tooltipStyle = remember { TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }

    // Data metrics calculations - Remembered to avoid redundant work
    val metrics = remember(dataPoints) {
        val lastPoint = dataPoints.last()
        val firstPoint = dataPoints.first()
        val maxDist = lastPoint.distanceKm
        val speeds = dataPoints.map { it.speedKmPerHour }
        val maxSpeed = speeds.maxOrNull() ?: 0f
        val minSpeed = speeds.minOrNull() ?: 0f
        val speedRange = (maxSpeed - minSpeed).coerceAtLeast(1f)
        val timeDiff = (lastPoint.time - firstPoint.time).toDouble()
        val avgSpeed = if (timeDiff > 0) 3600000.0 * maxDist / timeDiff else 0.0

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

        ChartMetrics(maxDist, maxSpeed, minSpeed, speedRange, avgSpeed.toFloat(), intervalKm, intervalSpeed)
    }

    // Binary search to find nearest point to touch
    fun updateSelectedPoint(touchX: Float, chartWidth: Float, yAxisWidth: Float) {
        if (chartWidth <= 0f || dataPoints.isEmpty()) return
        val relativeX = (touchX - yAxisWidth).coerceIn(0f, chartWidth)
        val targetKm = (relativeX / chartWidth) * metrics.maxDist

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
                    if (abs(p1.distanceKm - targetKm) < abs(p2.distanceKm - targetKm)) insertionPoint - 1 else insertionPoint
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

    Column(modifier = modifier.padding(16.dp)) {
        // Header
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

        // Quick Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem(stringResource(R.string.speed_chart_min, metrics.minSpeed), labelColor)
            StatItem(stringResource(R.string.speed_chart_max, metrics.maxSpeed), labelColor)
            StatItem(stringResource(R.string.speed_average, metrics.avgSpeed), labelColor)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(dataPoints, metrics) {
                    val yAxisWidthPx = 45.dp.toPx()
                    val chartWidthPx = size.width - yAxisWidthPx

                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown()
                            updateSelectedPoint(down.position.x, chartWidthPx, yAxisWidthPx)

                            drag(down.id) { change ->
                                updateSelectedPoint(change.position.x, chartWidthPx, yAxisWidthPx)
                                change.consume()
                            }

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

            // 1. Y-Axis & Grid
            val startSpeedLabel = ceil(metrics.minSpeed / metrics.intervalSpeed) * metrics.intervalSpeed - metrics.intervalSpeed
            var currentSpeed = startSpeedLabel
            while (currentSpeed <= metrics.maxSpeed) {
                if (currentSpeed >= metrics.minSpeed) {
                    val relativeSpeed = (currentSpeed - metrics.minSpeed) / metrics.speedRange
                    val y = chartHeight - (relativeSpeed * chartHeight)

                    drawLine(
                        color = gridColor,
                        start = Offset(yAxisWidth, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )

                    val speedText = "%.0f".format(currentSpeed)
                    val textLayout = textMeasurer.measure(speedText, style = labelStyle)
                    drawText(
                        textMeasurer,
                        speedText,
                        style = labelStyle,
                        topLeft = Offset(
                            (yAxisWidth - textLayout.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                            y - (textLayout.size.height / 2)
                        )
                    )
                }
                currentSpeed += metrics.intervalSpeed
            }

            // 2. X-Axis & Grid
            val numberOfLabels = ceil(metrics.maxDist / metrics.intervalKm).toInt()
            for (i in 0..numberOfLabels) {
                val currentKm = i * metrics.intervalKm
                if (currentKm <= metrics.maxDist) {
                    val x = yAxisWidth + ((currentKm / metrics.maxDist) * chartWidth)

                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, chartHeight),
                        strokeWidth = 1.dp.toPx()
                    )

                    val labelText = "${currentKm.toInt()} km"
                    val textLayout = textMeasurer.measure(labelText, style = labelStyle)
                    drawText(
                        textMeasurer,
                        labelText,
                        style = labelStyle,
                        topLeft = Offset(
                            (x - (textLayout.size.width / 2)).coerceIn(yAxisWidth, width - textLayout.size.width),
                            chartHeight + 6.dp.toPx()
                        )
                    )
                }
            }

            // 3. Graph Path
            val strokePath = Path()
            val gradientPath = Path()

            dataPoints.forEachIndexed { index, point ->
                val x = yAxisWidth + ((point.distanceKm / metrics.maxDist) * chartWidth)
                val relativeSpeed = (point.speedKmPerHour - metrics.minSpeed) / metrics.speedRange
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
                gradientPath.lineTo(yAxisWidth + chartWidth, chartHeight)
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

            // 4. Indicator & Tooltip
            selectedPoint?.let { point ->
                val indicatorX = (yAxisWidth + ((point.distanceKm / metrics.maxDist) * chartWidth)).coerceIn(yAxisWidth, width)
                val relativeSpeed = (point.speedKmPerHour - metrics.minSpeed) / metrics.speedRange
                val indicatorY = chartHeight - (relativeSpeed * chartHeight)

                drawLine(
                    color = indicatorColor.copy(alpha = 0.6f),
                    start = Offset(indicatorX, 0f),
                    end = Offset(indicatorX, chartHeight),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(color = indicatorColor.copy(alpha = 0.3f), radius = 8.dp.toPx(), center = Offset(indicatorX, indicatorY))
                drawCircle(color = indicatorColor, radius = 4.dp.toPx(), center = Offset(indicatorX, indicatorY))

                val tooltipText = "%.1f km | %.1f km/h".format(point.distanceKm, point.speedKmPerHour)
                val textLayout = textMeasurer.measure(tooltipText, style = tooltipStyle)
                val px = 8.dp.toPx()
                val py = 4.dp.toPx()
                val boxW = textLayout.size.width + (px * 2)
                val boxH = textLayout.size.height + (py * 2)
                
                val boxX = (indicatorX - (boxW / 2)).coerceIn(yAxisWidth, width - boxW)
                val boxY = (indicatorY - boxH - 12.dp.toPx()).coerceAtLeast(4.dp.toPx())

                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.75f),
                    topLeft = Offset(boxX, boxY),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                drawText(textMeasurer, tooltipText, style = tooltipStyle, topLeft = Offset(boxX + px, boxY + py))
            }
        }
    }
}

@Composable
private fun StatItem(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = color,
        fontWeight = FontWeight.Bold
    )
}

private data class ChartMetrics(
    val maxDist: Float,
    val maxSpeed: Float,
    val minSpeed: Float,
    val speedRange: Float,
    val avgSpeed: Float,
    val intervalKm: Float,
    val intervalSpeed: Float
)

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
