package com.financemanager.listener

import android.app.Application

class FinTrackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        com.financemanager.listener.service.CaptureStatus.initialize(this)
        com.financemanager.listener.data.PairingPreferences.session(this)
        com.financemanager.listener.capture.CaptureRuntime.get(this).resume()
    }

    companion object {
        lateinit var instance: FinTrackApplication
            private set
    }
}
