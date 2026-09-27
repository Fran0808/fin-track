package com.financemanager.listener

import android.app.Application

class FinTrackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: FinTrackApplication
            private set
    }
}
