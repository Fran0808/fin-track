package com.financemanager.listener.data

import com.financemanager.listener.parser.NotificationIdentity
import java.net.URI
import java.util.Locale

data class PairingSession(val token: String, val serverUrl: String, val ownerKey: String, val startedAt: Long) {
    override fun toString(): String = "PairingSession(redacted)"
}

object PairingScope {
    fun normalizeServer(url: String): String {
        val uri = URI(url.trim())
        require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank())
        require(uri.userInfo == null && uri.query == null && uri.fragment == null)
        val port = if ((uri.scheme == "https" && uri.port == 443) || (uri.scheme == "http" && uri.port == 80)) -1 else uri.port
        return URI(uri.scheme, null, uri.host.lowercase(Locale.ROOT), port, uri.path.orEmpty().trimEnd('/') + "/", null, null).toString()
    }

    fun ownerKey(server: String, token: String, userId: Long?): String = NotificationIdentity.digest(
        "${normalizeServer(server)}|${userId?.let { "user:$it" } ?: "credential:$token"}"
    )
}
