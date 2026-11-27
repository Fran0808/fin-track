package com.financemanager.listener.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.financemanager.listener.BuildConfig
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val sha256: String
)

private const val VERSION_MANIFEST_URL =
    "https://github.com/Fran0808/fin-track/releases/latest/download/version.json"
private const val TRUSTED_DOWNLOAD_PREFIX =
    "https://github.com/Fran0808/fin-track/releases/"
private val SEMVER_REGEX = Regex("""^\d+\.\d+\.\d+$""")
private val SHA256_REGEX = Regex("""^[a-fA-F0-9]{64}$""")

fun parseAppUpdateJson(raw: String, currentVersionCode: Int): AppUpdateInfo? {
    val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return null
    val versionName = runCatching { json.get("versionName")?.asString?.trim() }.getOrNull().orEmpty()
    val versionCode = runCatching { json.get("versionCode")?.asInt }.getOrNull() ?: -1
    val downloadUrl = runCatching { json.get("downloadUrl")?.asString?.trim() }.getOrNull().orEmpty()
    val sha256 = runCatching { json.get("sha256")?.asString?.trim()?.lowercase() }.getOrNull().orEmpty()

    if (versionCode <= currentVersionCode) return null
    if (!SEMVER_REGEX.matches(versionName)) return null
    if (!downloadUrl.startsWith(TRUSTED_DOWNLOAD_PREFIX)) return null
    if (!SHA256_REGEX.matches(sha256)) return null

    return AppUpdateInfo(
        versionName = versionName,
        versionCode = versionCode,
        downloadUrl = downloadUrl,
        sha256 = sha256
    )
}

fun verifyFileSha256(file: File, expectedSha256: String): Boolean {
    if (!file.isFile || !SHA256_REGEX.matches(expectedSha256)) return false
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }
    return actual.equals(expectedSha256, ignoreCase = true)
}

object AppUpdater {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun checkForUpdate(currentVersionCode: Int = BuildConfig.VERSION_CODE): AppUpdateInfo? {
        val request = Request.Builder()
            .url(VERSION_MANIFEST_URL)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code()}")
            }
            val body = response.body()?.string() ?: return null
            return parseAppUpdateJson(body, currentVersionCode)
        }
    }

    fun downloadAndVerifyApk(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (Float?) -> Unit
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Clean previous cached APKs to avoid wasting storage.
        dir.listFiles()?.forEach { existing ->
            if (existing.name != "fintrack-${info.versionCode}.apk") existing.delete()
        }
        val target = File(dir, "fintrack-${info.versionCode}.apk")
        if (target.isFile && verifyFileSha256(target, info.sha256)) {
            onProgress(1f)
            return target
        }

        val temp = File(dir, "fintrack-${info.versionCode}.apk.part")
        val request = Request.Builder().url(info.downloadUrl).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Error al descargar (HTTP ${response.code()})")
                if (!response.request().url().isHttps) throw IOException("La descarga requiere HTTPS")
                val body = response.body() ?: throw IOException("Respuesta vacía")
                val total = body.contentLength()
                var downloaded = 0L
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null)
                        }
                    }
                }
            }
            if (!verifyFileSha256(temp, info.sha256)) {
                temp.delete()
                throw IOException("El archivo descargado no coincide con la verificación de seguridad.")
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            onProgress(1f)
            return target
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }

    /**
     * Launches Android's package installer for [apkFile].
     * Returns true if the installer was launched, or false if the user was sent to system settings
     * to grant "Install unknown apps" permission first.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return false
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return true
    }
}
