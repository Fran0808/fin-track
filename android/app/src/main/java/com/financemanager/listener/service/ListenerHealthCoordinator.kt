package com.financemanager.listener.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import com.financemanager.listener.capture.CaptureRuntime
import com.financemanager.listener.capture.RebindPolicy
import com.financemanager.listener.capture.CaptureExecutionProbe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

object ListenerHealthCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val policy = RebindPolicy()
    private var reconnectJob: Job? = null
    fun foreground(context: Context) {
        CaptureRuntime.get(context).resume()
        if (CaptureStatus.state.value.connected) YapeNotificationListenerService.reviewActive() else reconnect(context)
    }
    fun connected() { reconnectJob?.cancel(); reconnectJob = null }
    fun reconnect(context: Context) {
        if (reconnectJob?.isActive == true) return
        val app = context.applicationContext
        reconnectJob = scope.launch {
            repeat(3) {
                if (CaptureStatus.state.value.connected || !YapeNotificationListenerService.hasPermission(app)) return@launch
                if (!componentEnabled(app)) {
                    CaptureStatus.error("El servicio de captura está desactivado. Revisa la configuración de Android.")
                    return@launch
                }
                delay(policy.delayMillis(SystemClock.elapsedRealtime()))
                if (CaptureStatus.state.value.connected || !YapeNotificationListenerService.hasPermission(app)) return@launch
                if (!componentEnabled(app)) return@launch
                policy.attempted(SystemClock.elapsedRealtime())
                CaptureExecutionProbe.lifecycle("REBIND")
                runCatching { NotificationListenerService.requestRebind(ComponentName(app, YapeNotificationListenerService::class.java)) }
                    .onFailure { CaptureStatus.error("No se pudo solicitar la reconexión. Revisa el acceso a notificaciones.") }
                withTimeoutOrNull(60_000) { CaptureStatus.state.first { it.connected } }
                if (CaptureStatus.state.value.connected) return@launch
            }
            CaptureStatus.error("Android no ha conectado la captura. Revisa el acceso a notificaciones e intenta reconectar.")
        }
    }
    private fun componentEnabled(context: Context): Boolean {
        val state = context.packageManager.getComponentEnabledSetting(ComponentName(context, YapeNotificationListenerService::class.java))
        return context.applicationInfo.enabled && state in listOf(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }
}
