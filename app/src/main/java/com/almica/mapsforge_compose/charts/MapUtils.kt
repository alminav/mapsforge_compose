package com.almica.mapsforge_compose.charts

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.net.Uri
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.text.Charsets.UTF_8

object MapUtils {
    private const val EARTH_RADIUS_KM = 6371.0  // Average radius of the Earth

    /**
     * Calculates the shortest distance between two points on Earth
     * using the Haversine formula.
     *
     * Haversine accounts for Earth's curvature.
     *
     * @param start starting coordinate (latitude, longitude)
     * @param end ending coordinate
     * @return distance in kilometers (Double)
     */
    fun calculateHaversineDistance(start: LatLng, end: LatLng): Double {
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLon = Math.toRadians(end.longitude - start.longitude)

        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)

        val a = sin(dLat / 2).pow(2.0) +
                sin(dLon / 2).pow(2.0) * cos(lat1) * cos(lat2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    fun getBearing(start: LatLng, end: LatLng): Float {
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        val lat2 = Math.toRadians(end.latitude)
        val lon2 = Math.toRadians(end.longitude)

        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        return Math.toDegrees(atan2(y, x)).toFloat()
    }

    fun lerpAngle(start: Float, end: Float, fraction: Float): Float {
        var delta = (end - start + 360) % 360
        if (delta > 180) delta -= 360
        return (start + delta * fraction + 360) % 360
    }

    /**
     * Cubic easing function for smooth animation transitions.
     *
     * Provides a gradual start (ease-in), fast middle, and gradual stop (ease-out),
     * which looks more natural than linear motion.
     *
     * @param t the normalized time or progress (range 0.0 to 1.0)
     * @return eased value also in range [0, 1]
     */
    fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) {
            4 * t * t * t
        } else {
            1 - (-2 * t + 2).let { it * it * it } / 2
        }
    }

    //maps.googleapis.com/maps/api/elevation/json?locations=enc:gfo}EtohhUxD@bAxJmGF&key=...
    // locations=40.714728,-73.998672
    suspend fun gmsElevationService(
        context: Context,
        locations: String
    ): List<LatLngH> = withContext(Dispatchers.IO) {
        val apiKey = ManifestUtils.getApiKeyFromManifest(context) ?: return@withContext emptyList()

        val uri = Uri.Builder().scheme("https")
            .authority("maps.googleapis.com")
            .appendPath("maps")
            .appendPath("api")
            .appendPath("elevation")
            .appendPath("json")
            .appendQueryParameter("locations", locations)
            .appendQueryParameter("key", apiKey)
            .build()

        Timber.i("elevationUrl: $uri")

        try {
            val jsonBuffer = getUrlContent(uri.toString())
            val json = String(jsonBuffer, UTF_8)
            val eleData = Gson().fromJson(json, ElevationResultsObject::class.java)

            eleData?.elevationResults?.mapNotNull { result ->
                val location = result.location ?: return@mapNotNull null
                LatLngH(
                    location.latitude ?: 0.0,
                    location.longitude ?: 0.0,
                    result.elevation ?: 0.0
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Error in gmsElevationService")
            emptyList()
        }
    }
    /*
        https://maps.googleapis.com/maps/api/directions/json?origin=52.32531,10.37146&destination=52.33621,10.32585&mode=bicycling&key=...
        driving (default) indicates standard driving directions or distance using the road network.
        walking requests walking directions or distance via pedestrian paths & sidewalks (where available).
        bicycling requests bicycling directions or distance via bicycle paths & preferred streets (where available).
     */

    fun gmsDirectionsService(context: Context, start: LatLng, stop: LatLng, mode: String, alternatives: Boolean,
                             finished: (lllh: List<LatLngH>, name: String, success: Boolean) -> Unit) {
        val apiKey = ManifestUtils.getApiKeyFromManifest(context)
        val timeFormat =
            SimpleDateFormat(Const.TIME_PATTERN_LONG, Locale.getDefault())
        val name = Const.GMS_TAG + "." + timeFormat.format(Date())
        val uri = Uri.Builder().scheme("https")
            .authority("maps.googleapis.com")
            .appendPath("maps")
            .appendPath("api")
            .appendPath("directions")
            .appendPath("json")
            .appendQueryParameter("origin", "${start.latitude},${start.longitude}")
            .appendQueryParameter("destination", "${stop.latitude},${stop.longitude}")
            .appendQueryParameter("mode", mode)
            .appendQueryParameter("alternatives", alternatives.toString())
            .appendQueryParameter("key", apiKey)
        val directionsUrl = uri.build()
        Timber.i("directionsUrl: $directionsUrl")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tempFile = File(context.cacheDir, "$name${Const.TXT_EXT}")
                downloadFile(directionsUrl.toString(), tempFile)

                val json = tempFile.readText(UTF_8)
                val routeData = Gson().fromJson(json, RoutesObject::class.java)
                val routes = routeData.routes
                Timber.i("routeData.routes size: ${routes?.size}")

                if (!routes.isNullOrEmpty()) {
                    val lllhResult = if (routes.size > 1) {
                        val encoded0 = routes[0].overview_polyline?.points
                        val lllh0 = gmsElevationService(context, "enc:$encoded0")

                        val encoded1 = routes[1].overview_polyline?.points
                        val lllh1 = gmsElevationService(context, "enc:$encoded1").reversed()

                        lllh0 + lllh1
                    } else {
                        val encoded0 = routes[0].overview_polyline?.points
                        gmsElevationService(context, "enc:$encoded0")
                    }

                    withContext(Dispatchers.Main) {
                        Timber.i("lllhResult size: ${lllhResult.size}")
                        finished(lllhResult, name, true)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in gmsDirectionsService")
                withContext(Dispatchers.Main) {
                    finished(emptyList(), "", false)
                }
            }
        }
    }

    /*
    https://developers.google.com/maps/documentation/places/web-service/place-details?hl=de#GetPlaceRequest
        addressComponents
        addressDescriptor*
        adrFormatAddress
        formattedAddress
        location
        plusCode
        postalAddress
        shortFormattedAddress
        types
        viewport
     */
    // alternative web-service method for fetchPlaceRequest Api
    // works on doogee
    fun downloadPoiInfo(
        context: Context,
        name: String,
        latLng: LatLng,
        placeId: String,
        result: (PoiInfo?) -> Unit
    ) {
        val apiKey = ManifestUtils.getApiKeyFromManifest(context)
        //val placesUrl = "https://places.googleapis.com/v1/places/${poi.placeId}?fields=id,displayName,addressDescriptor&${apiKey}"
        val uri = Uri.Builder().scheme("https")
            .authority("places.googleapis.com")
            .appendPath("v1")
            .appendPath("places")
            .appendPath(placeId)
            .appendQueryParameter("fields", "id,displayName,location,formattedAddress,photos")
            .appendQueryParameter("key", apiKey)
        val placesUrl = uri.build()
        Timber.i("placesUrl: $placesUrl")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                //shows something in the UI - progressBar
                Timber.i( "CoroutineScope")
                withContext(Dispatchers.IO) {
                    Timber.i( "withContext")
                    val tempFile = File(context.cacheDir, "$placeId${Const.TXT_EXT}")
                    val bytesCount = downloadFile(placesUrl.toString(), tempFile)
                    //tempFile.writeBytes(bytes)
                    Timber.i( "${tempFile.path} bytesCount: $bytesCount")
                    val inputStream = tempFile.inputStream()
                    val size = inputStream.available()
                    val buffer = ByteArray(size)
                    inputStream.read(buffer)
                    inputStream.close()
                    val json = String(buffer, charset = UTF_8)
                    val gson = Gson()
                    val data = gson.fromJson(json, PlaceObject::class.java)
                    if (data != null) {
                        Timber.i("formattedAddress: ${data.formattedAddress}")
                        // To get requestData
                        val photos = data.photos
                        photos?.forEach { photo ->
                            Timber.i("${photo.googleMapsUri}")
                        }
                        withContext(Dispatchers.Main) {
                            result(
                                PoiInfo(
                                    name,
                                    latLng,
                                    data.formattedAddress ?: "",
                                    data.photos?.getOrNull(0)?.googleMapsUri ?: ""
                                )
                            )
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            result(null)
                        }
                    }
                }
            } catch (e: IOException) {
                e.message?.let {
                    Timber.e("${Thread.currentThread().stackTrace[2].lineNumber} $it")
                }
            }
        }
    }
    data class PlaceObject(
        @SerializedName("googleMapsUri") val googleMapsUri: String? = null,
        @SerializedName("formattedAddress") val formattedAddress: String? = null,
        @SerializedName("photos") val photos: List<PhotoObject>? = null
    )

    data class PhotoObject(
        @SerializedName("googleMapsUri") val googleMapsUri: String? = null,
    )

    data class PoiInfo(val name: String, val latLng: LatLng, val formattedAddress: String, val googleMapsUri: String)

    // // works on doogee WITH connection.connectTimeout = 700 24sez2024
    private fun downloadFile(urlString: String, tempFile: File): Long {
        Timber.i("tempFile: ${tempFile.path}")
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 700 // important for doogee
        Timber.i("connection.connectTimeout: ${connection.connectTimeout}")
        Timber.i("launched connection.connect")
        connection.connect()
        Timber.i("finished connection.connect")

        var total: Long = 0
        try {
            connection.inputStream.use { input ->
                val bufferedInput = BufferedInputStream(input, 8192)
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(1024)
                    var read: Int = bufferedInput.read(buffer)
                    while (read != -1) {
                        output.write(buffer, 0, read)
                        total += read
                        read = bufferedInput.read(buffer)
                    }
                    output.flush()
                }
            }
        } finally {
            connection.disconnect()
        }
        Timber.i( "bytes: $total")
        return total
    }

    private fun getUrlContent(urlString: String): ByteArray {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 700 // important for doogee
        connection.connect()

        try {
            connection.inputStream.use { input ->
                val bufferedInput = BufferedInputStream(input, 8192)
                ByteArrayOutputStream().use { output ->
                    val data = ByteArray(1024)
                    var count: Int
                    while (bufferedInput.read(data).also { count = it } != -1) {
                        output.write(data, 0, count)
                    }
                    output.flush()
                    return output.toByteArray()
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}