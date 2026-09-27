package com.financemanager.listener.data

import android.content.Context
import android.content.SharedPreferences
import com.financemanager.listener.BuildConfig

object PairingPreferences {
    private const val PREFS_NAME = "wallet_pulse_pairing_prefs"
    private const val KEY_PAIRING_TOKEN = "key_device_pairing_token"
    private const val KEY_SERVER_URL = "key_server_url"
    private const val KEY_USER_EMAIL = "key_user_email"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isPaired(context: Context): Boolean {
        val token = getPairingToken(context)
        return !token.isNullOrBlank()
    }

    fun getPairingToken(context: Context): String? {
        return getPrefs(context).getString(KEY_PAIRING_TOKEN, null)
    }

    fun getServerUrl(context: Context): String {
        val saved = getPrefs(context).getString(KEY_SERVER_URL, null)
        val base = if (!saved.isNullOrBlank()) saved else BuildConfig.BASE_URL
        return if (base.endsWith("/")) base else "$base/"
    }

    fun getPairedEmail(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_EMAIL, null)
    }

    fun savePairing(context: Context, token: String, serverUrl: String?, email: String?) {
        val editor = getPrefs(context).edit()
        editor.putString(KEY_PAIRING_TOKEN, token.trim())
        if (!serverUrl.isNullOrBlank()) {
            val normalizedUrl = if (serverUrl.trim().endsWith("/")) serverUrl.trim() else "${serverUrl.trim()}/"
            editor.putString(KEY_SERVER_URL, normalizedUrl)
        }
        if (!email.isNullOrBlank()) {
            editor.putString(KEY_USER_EMAIL, email.trim())
        }
        editor.apply()
    }

    fun clearPairing(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
