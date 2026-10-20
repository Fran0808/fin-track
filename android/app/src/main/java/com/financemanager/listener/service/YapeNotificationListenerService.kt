package com.financemanager.listener.service

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.financemanager.listener.capture.*
import com.financemanager.listener.data.CaptureObservation
import com.financemanager.listener.data.PairingPreferences
import com.financemanager.listener.parser.NotificationParserDispatcher
import java.util.UUID

/** Preserve the registered component identity and existing notification-access grants. */
class YapeNotificationListenerService : NotificationListenerService() {
    private val runtime get() = CaptureRuntime.get(applicationContext)
    override fun onListenerConnected() {
        super.onListenerConnected()
        current = this
        CaptureStatus.connected(true)
        CaptureExecutionProbe.listenerConnected(this)
        ListenerHealthCoordinator.connected()
        reviewActiveNotifications()
        runtime.resume()
    }
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (current === this) current = null
        CaptureStatus.connected(false)
        CaptureExecutionProbe.lifecycle("DISCONNECTED")
        ListenerHealthCoordinator.reconnect(applicationContext)
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { if (sbn != null) accept(sbn, false) }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null || !NotificationParserDispatcher.defaultInstance.isSupportedPackage(sbn.packageName)) return
        val key = sbn.key
        runtime.submit { CaptureObservation(observationKey = UUID.randomUUID().toString(), kind = "REMOVED", sourceKey = key) }
    }
    private fun reviewActiveNotifications() {
        if (current !== this || !CaptureStatus.state.value.connected || !hasPermission(this)) return
        try {
            CaptureExecutionProbe.lifecycle("ACTIVE_QUERY")
            val supported = activeNotifications?.filter { NotificationParserDispatcher.defaultInstance.isSupportedPackage(it.packageName) } ?: return
            val keys = supported.map { it.key }
            runtime.submit { CaptureObservation(observationKey = UUID.randomUUID().toString(), kind = "RECONCILE", lines = CaptureEncoding.encode(keys)) }
            supported.forEach { accept(it, true) }
        } catch (_: Exception) { CaptureStatus.error("No se pudieron consultar las notificaciones activas.") }
    }
    private fun accept(sbn: StatusBarNotification, recovering: Boolean) {
        // Authorize before accessing extras. New emitters must be registered explicitly.
        if (!NotificationParserDispatcher.defaultInstance.isSupportedPackage(sbn.packageName)) return
        CaptureStatus.received()
        try {
            val notification = sbn.notification
            val extras = notification.extras
            var truncated = false
            var remaining = 32768
            fun bounded(value: CharSequence?): String? {
                if (value == null) return null
                val size = minOf(value.length, 8192, remaining)
                if (value.length > size) truncated = true
                remaining -= size
                return value.subSequence(0, size).toString()
            }
            val allLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if ((allLines?.size ?: 0) > 20) truncated = true
            val content = NotificationContent(bounded(extras.getCharSequence(Notification.EXTRA_TITLE)),
                bounded(extras.getCharSequence(Notification.EXTRA_TEXT)), bounded(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)),
                allLines?.take(20)?.mapNotNull { bounded(it) } ?: emptyList())
            val key = sbn.key
            val packageName = sbn.packageName
            val postTime = sbn.postTime
            val eventTime = notification.`when`.takeIf { it > 0 && it <= postTime } ?: postTime
            val receivedAt = System.currentTimeMillis()
            val zoneId = java.time.ZoneId.systemDefault().id
            val owner = PairingPreferences.session(applicationContext)?.takeIf { eventTime >= it.startedAt && postTime >= it.startedAt }?.ownerKey
            val summary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
            val clipped = truncated
            runtime.submit { CaptureObservation.posted(packageName, key, postTime, eventTime, receivedAt,
                owner, recovering, summary, content, clipped, zoneId) }
        } catch (_: Exception) { CaptureStatus.error("No se pudo leer una notificación compatible.") }
    }
    override fun onDestroy() {
        CaptureExecutionProbe.lifecycle("DESTROYED")
        if (current === this) {
            current = null
            CaptureStatus.connected(false)
            ListenerHealthCoordinator.reconnect(applicationContext)
        }
        // Application-owned ingress and committed processing outlive this service instance.
        super.onDestroy()
    }
    companion object {
        private var current: YapeNotificationListenerService? = null
        fun reviewActive() { current?.reviewActiveNotifications() }
        fun reconnect(context: Context) = ListenerHealthCoordinator.reconnect(context)
        fun hasPermission(context: Context): Boolean {
            val component = ComponentName(context, YapeNotificationListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                return context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
            }
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
        }
    }
}
