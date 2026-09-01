package com.almica.mapsforge_compose.gh

import android.app.Activity
import android.content.Context
import android.icu.text.SimpleDateFormat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import com.almica.mapsforge_compose.R
import com.almica.mapsforge_compose.RoutePoint
import com.graphhopper.GraphHopper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Date
import java.util.Locale

object GhHelper {

    enum class Locomotion(
        val key: String,
        @DrawableRes val iconRes: Int,
        @StringRes val descriptionRes: Int,
        val vehicleEncoding: String,
        val weightingEncoding: String
    ) {
        PEDESTRIAN_SHORT(
            "0.0",
            R.drawable.ic_directions_walk_black_24dp,
            R.string.pedestrian,
            Const.Companion.VehicleEncoding.FOOT_ENCODING,
            Const.Companion.WeightingEncoding.SHORT_ENCODING
        ),
        BICYCLE_SHORT(
            "1.1",
            R.drawable.ic_directions_bike_black_24dp,
            R.string.bicycle_short,
            Const.Companion.VehicleEncoding.BIKE_ENCODING,
            Const.Companion.WeightingEncoding.SHORT_ENCODING
        ),
        BICYCLE_FAST(
            "1.0",
            R.drawable.ic_directions_bike_fast_black_24dp,
            R.string.bicycle_fast,
            Const.Companion.VehicleEncoding.BIKE_ENCODING,
            Const.Companion.WeightingEncoding.FAST_ENCODING
        ),
        CAR_SHORT(
            "2.1",
            R.drawable.ic_directions_car_black_24dp,
            R.string.car_short,
            Const.Companion.VehicleEncoding.CAR_ENCODING,
            Const.Companion.WeightingEncoding.SHORT_ENCODING
        ),
        CAR_FAST(
            "2.0",
            R.drawable.ic_directions_car_fast_black_24dp,
            R.string.car_fast,
            Const.Companion.VehicleEncoding.CAR_ENCODING,
            Const.Companion.WeightingEncoding.FAST_ENCODING
        ),
        AIRPLANE_SHORT(
            "3.1",
            R.drawable.ic_directions_airplane_24_black,
            R.string.airplane,
            Const.Companion.VehicleEncoding.AIRPLANE_ENCODING,
            Const.Companion.WeightingEncoding.SHORT_ENCODING
        );

        companion object {
            fun fromKey(key: String?): Locomotion = entries.find { it.key == key } ?: BICYCLE_SHORT
        }
    }

    private fun getGhFolder(context: Context, ghDefaultFolder: File?): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val ghFilePath = prefs.getString(Const.PREF_GH_FILEPATH, ghDefaultFolder?.path)
        if (ghFilePath != null) {
            return if (File(ghFilePath).exists()) {
                ghFilePath
            } else {
                Timber.e("$ghFilePath not found")
                null
            }
        }
        return null
    }

    fun getGhFilename(context: Context): String? {
        val ghFolder = getGhFolder(context, null)
        if (ghFolder != null) {
            val ghFile = File(ghFolder)
            if (ghFile.exists()) return ghFile.name
        }
        Timber.i("GH folder error $ghFolder")
        return null
    }

    fun getGhManager(context: Context, ghDefaultFolder: File?): GhManager? {
        val ghPath = getGhFolder(context, ghDefaultFolder)
        Timber.i("ghPath: $ghPath")
        return GhManager.getInstance(context, ghPath, mGhListener)
    }

    private fun getLocomotionKeyFromPref(context: Context): String? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString(
            context.getString(R.string.setting_locomotion),
            Const.DEFAULT_LOCOMOTION
        )
    }

    fun getVehicleIcon(context: Context, locomotionKey: String? = null): Int {
        val key = locomotionKey ?: getLocomotionKeyFromPref(context)
        return Locomotion.fromKey(key).iconRes
    }

    fun getVehicleDescription(context: Context, locomotionKey: String? = null): String {
        val key = locomotionKey ?: getLocomotionKeyFromPref(context)
        return context.getString(Locomotion.fromKey(key).descriptionRes)
    }

    val mGhListener: GhManager.InitListener = object : GhManager.InitListener {
        override fun completeOk(tmpHopp: GraphHopper?, context: Context) {
            Timber.i(context.getString(R.string.gh_load_ok, getGhFilename(context)))
        }

        override fun completeNok(msg: String?, context: Activity?) {
            context?.let {
                Timber.e(it.getString(R.string.gh_load_error, getGhFilename(it)))
            }
        }

        override fun progress(fileName: String?) {
            fileName?.let { Timber.i(it) }
        }

        override fun ghInitStarted(context: Context) {
            Timber.i(context.getString(R.string.gh_initialization, getGhFilename(context)))
        }
    }
    suspend fun ghCalc(
        context: Context,
        ghFolder: File?,
        startY: Double,
        startX: Double,
        stopY: Double,
        stopX: Double,
        roundTrip: Boolean = false,
        roundTripFactor: Float = 0.2f
    ): GhRouteResult = withContext(Dispatchers.IO) {
        val ghManager = getGhManager(context, ghFolder)
        if (ghManager == null) {
            Timber.i(context.getString(R.string.gh_not_initialzed))
            return@withContext GhRouteResult(
                points = emptyList(),
                name = "Gh.Error",
                success = false,
                ghInitError = true
            )
        }
        val timeFormat = SimpleDateFormat(Const.TIME_PATTERN_LONG, Locale.getDefault())
        val vehicle = ghManager.getVehicle(context)

        try {
            val ghResponse = if (roundTrip) {
                ghManager.startRoundTripRequest(context, startY, startX, stopY, stopX, roundTripFactor)
            } else {
                ghManager.startRequest(context, startY, startX, stopY, stopX)
            }

            if (ghResponse.hasErrors()) {
                val errorMsg = ghResponse.errors.joinToString { it.message ?: "Unknown GH error" }
                Timber.e("GH Request Errors: $errorMsg")
                return@withContext GhRouteResult(
                    points = listOf(RoutePoint(startY, startX), RoutePoint(stopY, stopX)),
                    name = "Gh.Error",
                    success = false,
                    errorMessage = errorMsg
                )
            }

            val ghPoints = ghResponse.points
            val lllh = List(ghPoints.size()) { i ->
                RoutePoint(
                    ghPoints.getLatitude(i),
                    ghPoints.getLongitude(i),
                    ghPoints.getElevation(i)
                )
            }.ifEmpty {
                listOf(RoutePoint(startY, startX), RoutePoint(stopY, stopX))
            }

            val name = vehicle?.firstOrNull()?.let { "${Const.GH_TAG}.$it.${timeFormat.format(Date())}" }
                ?: "${Const.GH_TAG}.${timeFormat.format(Date())}"

            GhRouteResult(
                points = lllh,
                name = name,
                success = true,
                ghInitError = ghManager.hasInitError()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error during route calculation")
            GhRouteResult(
                points = listOf(RoutePoint(startY, startX), RoutePoint(stopY, stopX)),
                name = "Gh.Error",
                success = false,
                errorMessage = e.message
            )
        }
    }
}

data class GhRouteResult(
    val points: List<RoutePoint>,
    val name: String,
    val success: Boolean,
    val ghInitError: Boolean = false,
    val errorMessage: String? = null
)
