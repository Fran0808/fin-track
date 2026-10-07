package com.financemanager.listener.network

import com.financemanager.listener.BuildConfig
import com.financemanager.listener.FinTrackApplication
import com.financemanager.listener.data.PairingPreferences
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface ApiService {
    @POST("api/v1/transactions/sync/batch")
    suspend fun syncBatch(@Header("X-Device-Token") token: String,
                         @Body requests: List<TransactionSyncDto>): Response<List<SyncReceipt>>

    @POST("api/v1/user/pairing-info/verify")
    suspend fun verifyPairing(
        @Header("X-Device-Token") token: String
    ): Response<PairingVerifyDto>
}

data class SyncReceipt(val transactionHash: String)

object ApiClient {
    private var customBaseUrl: String? = null
    private var cachedService: ApiService? = null

    private fun getOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun resolveBaseUrl(): String {
        return try {
            val app = FinTrackApplication.instance
            PairingPreferences.getServerUrl(app)
        } catch (e: Exception) {
            val defaultUrl = BuildConfig.BASE_URL
            if (defaultUrl.endsWith("/")) defaultUrl else "$defaultUrl/"
        }
    }

    @Synchronized
    fun getService(overrideBaseUrl: String? = null): ApiService {
        val targetUrl = com.financemanager.listener.data.PairingScope.normalizeServer(overrideBaseUrl ?: resolveBaseUrl())
        if (cachedService != null && customBaseUrl == targetUrl) {
            return cachedService!!
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(if (targetUrl.endsWith("/")) targetUrl else "$targetUrl/")
            .client(getOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        customBaseUrl = targetUrl
        cachedService = retrofit.create(ApiService::class.java)
        return cachedService!!
    }

    val service: ApiService
        get() = getService()

    fun resetService() {
        cachedService = null
        customBaseUrl = null
    }
}
