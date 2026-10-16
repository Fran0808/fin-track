package com.financemanager.listener.data

import android.content.Context
import android.content.SharedPreferences
import com.financemanager.listener.BuildConfig

object PairingPreferences {
    private const val PREFS_NAME = "wallet_pulse_pairing_prefs"
    private const val KEY_PAIRING_TOKEN = "key_device_pairing_token"
    private const val KEY_SERVER_URL = "key_server_url"
    private const val KEY_USER_EMAIL = "key_user_email"
    private const val KEY_USER_ID = "key_user_id"
    private const val KEY_STARTED_AT = "key_started_at"
    private const val OWNER_ALIAS_PREFIX = "key_owner_alias_"

    @Synchronized
    fun session(context: Context): PairingSession? {
        val prefs = getPrefs(context)
        val token = prefs.getString(KEY_PAIRING_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val server = runCatching { PairingScope.normalizeServer(getServerUrl(context)) }.getOrNull() ?: return null
        val startedAt = if (prefs.contains(KEY_STARTED_AT)) prefs.getLong(KEY_STARTED_AT, 0) else {
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_STARTED_AT, now).commit()
            now
        }
        val userId = if (prefs.contains(KEY_USER_ID)) prefs.getLong(KEY_USER_ID, 0) else null
        return PairingSession(token, server, ownerKey(prefs, server, token, userId), startedAt)
    }

    private fun ownerKey(prefs: SharedPreferences, server: String, token: String, userId: Long?): String {
        val canonical = PairingScope.ownerKey(server, token, userId)
        return if (userId == null) canonical else prefs.getString(OWNER_ALIAS_PREFIX + canonical, null) ?: canonical
    }

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

    @Synchronized
    fun savePairing(context: Context, token: String, serverUrl: String?, email: String?, userId: Long) {
        val previous = session(context)
        val prefs = getPrefs(context)
        val normalizedServer = PairingScope.normalizeServer(serverUrl ?: getServerUrl(context))
        val owner = ownerKey(prefs, normalizedServer, token.trim(), userId)
        val editor = prefs.edit()
        editor.putString(KEY_PAIRING_TOKEN, token.trim())
        editor.putLong(KEY_USER_ID, userId)
        val sameAccount = previous != null && (previous.ownerKey == owner ||
            (previous.token == token.trim() && previous.serverUrl == normalizedServer))
        editor.putLong(KEY_STARTED_AT, if (sameAccount) requireNotNull(previous).startedAt else System.currentTimeMillis())
        if (!serverUrl.isNullOrBlank()) {
            val normalizedUrl = PairingScope.normalizeServer(serverUrl)
            editor.putString(KEY_SERVER_URL, normalizedUrl)
        }
        if (!email.isNullOrBlank()) {
            editor.putString(KEY_USER_EMAIL, email.trim())
        }
        check(editor.commit()) { "Unable to save pairing" }
    }

    @Synchronized
    fun updateVerifiedServer(context: Context, expected: PairingSession, serverUrl: String, verifiedUserId: Long) {
        val prefs = getPrefs(context)
        val updated = PairingAddressChange.verifiedSession(session(context), expected, serverUrl,
            if (prefs.contains(KEY_USER_ID)) prefs.getLong(KEY_USER_ID, 0) else null, verifiedUserId)
        val aliasKey = OWNER_ALIAS_PREFIX + PairingScope.ownerKey(updated.serverUrl, updated.token, verifiedUserId)
        require(prefs.getString(aliasKey, null).let { it == null || it == updated.ownerKey }) {
            "The address already belongs to another local ownership scope"
        }
        // Keep the existing local ownership scope; only the verified transport address changes.
        check(prefs.edit().putString(KEY_SERVER_URL, updated.serverUrl)
            .putString(aliasKey, updated.ownerKey).putLong(KEY_USER_ID, verifiedUserId).commit()) {
            "Unable to update paired server address"
        }
    }

    @Synchronized
    fun clearPairing(context: Context) {
        val prefs = getPrefs(context)
        val aliases = prefs.all.filterKeys { it.startsWith(OWNER_ALIAS_PREFIX) }
        val editor = prefs.edit().clear()
        // Retain address history without retaining a credential or an active pairing.
        aliases.forEach { (key, value) -> if (value is String) editor.putString(key, value) }
        check(editor.commit()) { "Unable to clear pairing" }
    }
}
