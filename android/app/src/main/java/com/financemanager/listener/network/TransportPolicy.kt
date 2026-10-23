package com.financemanager.listener.network

import com.financemanager.listener.data.PairingScope
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

class InsecureTransportException : IOException("HTTPS is required for this build")

object TransportPolicy {
    fun validate(server: String, allowCleartext: Boolean): String {
        val normalized = PairingScope.normalizeServer(server)
        if (!allowCleartext && URI(normalized).scheme != "https") throw InsecureTransportException()
        return normalized
    }

    fun client(allowCleartext: Boolean): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        // Device tokens use a custom header; never forward them through redirects.
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain ->
            if (!allowCleartext && !chain.request().url().isHttps()) throw InsecureTransportException()
            chain.proceed(chain.request())
        }.build()
}
