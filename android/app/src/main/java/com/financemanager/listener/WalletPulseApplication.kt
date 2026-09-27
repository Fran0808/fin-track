package com.financemanager.listener

import android.app.Application

class WalletPulseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: WalletPulseApplication
            private set
    }
}
