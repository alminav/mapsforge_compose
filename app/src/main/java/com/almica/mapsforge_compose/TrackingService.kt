package com.almica.mapsforge_compose

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mapsforge.core.model.LatLong
import timber.log.Timber

class TrackingService : Service(), SensorEventListener {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var locationClient: LocationClient
    private var gpsJob: Job? = null
    
    private var sensorManager: SensorManager? = null
    private var pressureSensor: Sensor? = null
    private var lastAltitude: Double? = null

    companion object {
        const val CHANNEL_ID = "tracking_channel"
        const val NOTIFICATION_ID = 1
        val locationFlow = MutableSharedFlow<RoutePoint>(replay = 1)
        val currentTrackPoints = mutableListOf<RoutePoint>()
        
        private val _isEcoMode = MutableStateFlow(false)
        val isEcoMode = _isEcoMode.asStateFlow()
        
        private val _statsFlow = MutableStateFlow(TourStatistics())
        val statsFlow = _statsFlow.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationClient(this)
        setupBarometer()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    private fun setupBarometer() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val pressure = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (pressure != null) {
            pressureSensor = pressure
            sensorManager?.registerListener(this, pressure, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            pressureSensor = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP_TRACKING") {
            stopAndSaveTourToDatabase()
            stopSelf()
            return START_NOT_STICKY
        }

        currentTrackPoints.clear()
        _statsFlow.value = TourStatistics()
        lastAltitude = null
        startGpsTracking(intervalMs = 3000L)
        
        return START_STICKY
    }

    private fun startGpsTracking(intervalMs: Long) {
        val settings = SettingsRepository(this)
        gpsJob?.cancel()
        gpsJob = serviceScope.launch {
            locationClient.getAdaptiveLocationUpdates(intervalMs).collect { locationData ->
                val currentSpeed = _statsFlow.value.currentSpeedKmh
                
                if (currentSpeed == 0.0 && !_isEcoMode.value) {
                    _isEcoMode.value = true
                    startGpsTracking(60000L) 
                    return@collect
                } else if (currentSpeed > 1.5 && _isEcoMode.value) {
                    _isEcoMode.value = false
                    startGpsTracking(3000L)
                    return@collect
                }

                val correction = settings.getAltitudeCorrection()
                val baseAltitude = locationData.mslAltitude ?: locationData.altitude
                processNewLocation(RoutePoint(locationData.latitude,
                    locationData.longitude, baseAltitude + correction, locationData.time), baseAltitude + correction)
            }
        }
    }

    private suspend fun processNewLocation(routePoint: RoutePoint, currentAltitudeMeters: Double) {
        val currentStats = _statsFlow.value
        var addedDistance = 0.0

        if (currentTrackPoints.isNotEmpty()) {
            addedDistance = TrackStatsCalculator.calculateDistanceKm(
                LatLong(currentTrackPoints.last().latitude, currentTrackPoints.last().longitude),
                LatLong(routePoint.latitude, routePoint.longitude))
        }

        currentTrackPoints.add(routePoint)
        locationFlow.emit(routePoint)

        val computedSpeed = (addedDistance / (3.0 / 3600.0))

        _statsFlow.value = currentStats.copy(
            totalDistanceKm = currentStats.totalDistanceKm + addedDistance,
            currentSpeedKmh = if (computedSpeed < 1.0) 0.0 else computedSpeed,
            //elevationDifferenceMeters = TourEntity.calculateElevationDifference(currentTrackPoints),
            currentAltitudeMeters = currentAltitudeMeters
        )
        Timber.i("currentAltitudeMeters: $currentAltitudeMeters")
    }

    private fun stopAndSaveTourToDatabase() {
        if (currentTrackPoints.isEmpty()) return
        val stats = _statsFlow.value
        val newTour = TourEntity(
            timestamp = System.currentTimeMillis(),
            totalDistanceKm = stats.totalDistanceKm,
            elevationGainMeters = stats.elevationGainMeters,
            routePoints = currentTrackPoints.toList()
        )
        serviceScope.launch {
            TourDatabase.getDatabase(applicationContext).tourDao().insertTour(newTour)
            currentTrackPoints.clear()
            _statsFlow.value = TourStatistics()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PRESSURE) {
            val pressure = event.values[0]
            val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure).toDouble()
            val currentStats = _statsFlow.value
            var currentGain = currentStats.elevationGainMeters

            if (lastAltitude != null) {
                val diff = altitude - lastAltitude!!
                if (diff > 0.4) {
                    currentGain += diff
                }
            }
            
            lastAltitude = altitude
            _statsFlow.value = currentStats.copy(
                //currentAltitudeMeters = altitude,
                elevationGainMeters = currentGain
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Tracking aktiv")
            .setContentText("Ihre Position wird im Hintergrund erfasst.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "GPS Tracking", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
        gpsJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
