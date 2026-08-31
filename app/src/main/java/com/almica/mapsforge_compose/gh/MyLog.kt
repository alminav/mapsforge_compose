package com.almica.mapsforge_compose.gh

import android.util.Log

class MyLog(private val enabled: Boolean, private val tag: String) {
    fun i(msg: String) {
        if (enabled) Log.i(tag, msg)
    }
    fun e(msg: String) {
        Log.e(tag, msg)
    }
    fun d(msg: String) {
        if (enabled) Log.d(tag, msg)
    }
}
