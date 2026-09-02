package com.almica.mapsforge_compose.charts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.almica.composecharts.charts.bar.BarChartData
import com.almica.composecharts.charts.bar.render.label.SimpleLabelDrawer
import com.google.maps.android.SphericalUtil
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.absoluteValue

private const val logtag = "GradientChartDataModel"
/**
 * Created by bytebeats on 2021/9/30 : 19:39
 * E-mail: happychinapc@gmail.com
 * Quote: Peasant. Educated. Worker
 */
class GradientChartDataModel(
    lllh: List<LatLngH>,
    var routePointer: Int,
    routeDistance: Double
) {
    private var colors = mutableListOf(
        Color(0XFFF44336),
        Color(0XFFE91E63),
        Color(0XFF9C27B0),
        Color(0XFF673AB7),
        Color(0XFF3F51B5),
        Color(0XFF03A9F4),
        Color(0XFF009688),
        Color(0XFFCDDC39),
        Color(0XFFFFC107),
        Color(0XFFFF5722),
        Color(0XFF795548),
        Color(0XFF9E9E9E),
        Color(0XFF607D8B)
    )

    var labelDrawer by mutableStateOf(SimpleLabelDrawer(drawLocation = SimpleLabelDrawer.DrawLocation.XAxis))
        //private set
    var sliderPosition by mutableStateOf(0f)
    var barChartData by mutableStateOf(generateGradientChart(lllh, routePointer, routeDistance))
    val bars: List<BarChartData.Bar>
        get() = barChartData.first!!.bars

    internal fun removeBar() {
        barChartData.first?.let {
            barChartData = Pair(it.copy(bars = bars.toMutableList().apply {
                val lastBar = bars.last()
                colors.add(lastBar.color)
                remove(lastBar)
            }), barChartData.second)
        }
    }
/*
    private fun randomValue(): Float = Random.nextInt(25, 125).toFloat()
    private fun randomColor(): Color {
        val idx = Random.nextInt(colors.size)
        return colors.removeAt(idx)
    }
 */
}

fun generateGradientChart(
    lllh: List<LatLngH>,
    routePointer: Int,
    routeDistance: Double
) : Pair<BarChartData?, String?> {
    Timber.i("generateGradientChart routePointer:$routePointer lllh.size:${lllh.size}")
    var maxAltitude = -Double.MAX_VALUE
    val bars = ArrayList<BarChartData.Bar>()
    var ascent = 0.0
    var descent = 0.0
    var gradientMinValue = Double.MAX_VALUE
    var gradientMaxValue = Double.MIN_VALUE
    var sumDist = 0.0
    for (i in 1 until lllh.size) {
        if (lllh[i-1].altitude > maxAltitude)
            maxAltitude = lllh[i-1].altitude
        val dist = SphericalUtil.computeDistanceBetween(
            lllh[i].latLng,
            lllh[i - 1].latLng)
        var gradient = 0.0
        val deltaH: Double =
            lllh[i].altitude - lllh[i - 1].altitude
        if (deltaH < 0) {
            descent += deltaH.toInt().toDouble()
        } else {
            ascent += deltaH.toInt().toDouble()
        }
        if (dist > 0) gradient = 100 * deltaH / dist
        sumDist += dist
        //val iColor = Math.round(abs(gradient) / 2.5).toInt()
        val c = interpolateColor((0.1 * abs(gradient)).toFloat())
        //Timber.i("lllh altitude: $i ${lllh[i].altitude.toFloat()}")
        val bar = BarChartData.Bar(
            // 20aug2026 sliderposition is adjusted to routePointer
            label = //if ((i-1)==routePointer) Const.UC_ARROW_UP else
                if (i == (0.5*lllh.size).toInt()) {
                val midLabel = routeDistance.formatDistM(true)
                midLabel
            } else
                { "" },

            value = lllh[i].altitude.toFloat(), // gradient.toFloat(),
            color = Color(c)
        )
        bars.add(bar)
        if (gradientMinValue > gradient) gradientMinValue = gradient
        if (gradientMaxValue < gradient) gradientMaxValue = gradient
    }
    if (maxAltitude > 0) {
        val sAscentDescent: String = java.lang.String.format(
            "%s%s %s%s",
            Const.GRADIENT_UP_UC,
            ascent.formatDistM( true),
            Const.GRADIENT_DOWN_UC,
            descent.absoluteValue.formatDistM(true)
        )
        val maxBarValue: Float = bars.maxOf { it.value }
        Timber.i( " maxBarValue: $maxBarValue")
        return Pair(BarChartData(bars = bars), sAscentDescent)
    }
    return Pair(null, null)
}
fun interpolateColor(ratio: Float): Int {
    val colors = intArrayOf(
        android.graphics.Color.rgb(102, 225, 0),  // green
        android.graphics.Color.rgb(255, 0, 0) // red
    )
    val alpha = ((android.graphics.Color.alpha(colors[1]) - android.graphics.Color.alpha(
        colors[0]
    )) * ratio + android.graphics.Color.alpha(colors[0])).toInt()

    val hsv1 = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        android.graphics.Color.red(colors[0]), android.graphics.Color.green(
            colors[0]
        ), android.graphics.Color.blue(colors[0]), hsv1
    )
    val hsv2 = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        android.graphics.Color.red(colors[1]), android.graphics.Color.green(
            colors[1]
        ), android.graphics.Color.blue(colors[1]), hsv2
    )

    // adjust so that the shortest path on the color wheel will be taken
    if (hsv1[0] - hsv2[0] > 180) {
        hsv2[0] += 360f
    } else if (hsv2[0] - hsv1[0] > 180) {
        hsv1[0] += 360f
    }

    // Interpolate using calculated ratio
    val result = FloatArray(3)
    for (i in 0..2) {
        result[i] = (hsv2[i] - hsv1[i]) * (ratio) + hsv1[i]
    }

    return android.graphics.Color.HSVToColor(alpha, result)
}

