package com.almica.mapsforge_compose

import android.app.Application
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import timber.log.Timber

class MapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidGraphicFactory.createInstance(this)
        
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
