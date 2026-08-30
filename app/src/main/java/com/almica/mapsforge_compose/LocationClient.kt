package com.almica.mapsforge_compose

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val mslAltitude: Double? = null
)

class LocationClient(private val context: Context) {
    private val client: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun getAdaptiveLocationUpdates(intervalMs: Long): Flow<LocationData> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMinUpdateDistanceMeters(2.0f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val mslAlt = if (android.os.Build.VERSION.SDK_INT >= 34 && loc.hasMslAltitude()) {
                        Timber.i("mslAltitudeMeters: ${loc.mslAltitudeMeters}")
                        loc.mslAltitudeMeters
                    } else {
                        Timber.i("hasMslAltitude: false")
                        null
                    }
                    
                    trySend(
                        LocationData(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            altitude = loc.altitude,
                            mslAltitude = mslAlt
                        )
                    )
                }
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
