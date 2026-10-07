package com.financemanager.listener.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteTableLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.financemanager.listener.data.AppDatabase
import com.financemanager.listener.data.LocalTransactionEntity
import com.financemanager.listener.data.PairingPreferences
import com.financemanager.listener.parser.NotificationIdentity
import com.financemanager.listener.parser.NotificationMetadata
import com.financemanager.listener.parser.NotificationParserDispatcher
import com.financemanager.listener.worker.TransactionSyncWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class YapeNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            for (event in events) {
                try { event() }
                catch (e: CancellationException) { throw e }
                catch (_: com.financemanager.listener.data.LegacyReviewRequired) {
                    CaptureStatus.error("Una notificación coincide con registros antiguos ambiguos. Revisa los registros locales antes de recuperarla.")
                }
                catch (_: Exception) { CaptureStatus.error("No se pudo procesar una notificación; revisa las notificaciones activas.") }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        current = this
        CaptureStatus.connected(true)
        reviewActiveNotifications()
        TransactionSyncWorker.enqueue(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        current = null
        CaptureStatus.connected(false)
        val now = SystemClock.elapsedRealtime()
        if (hasPermission(this) && (lastRebind == 0L || now - lastRebind >= 60_000)) {
            lastRebind = now
            runCatching { requestRebind(ComponentName(this, YapeNotificationListenerService::class.java)) }
                .onFailure { CaptureStatus.error("No se pudo solicitar la reconexión del servicio.") }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) accept(sbn, false)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null && NotificationParserDispatcher.defaultInstance.isSupportedPackage(sbn.packageName)) {
            events.trySend { AppDatabase.getDatabase(applicationContext).transactionDao().retireSource(sbn.key) }
        }
    }

    private fun reviewActiveNotifications() {
        if (!CaptureStatus.state.value.connected || !hasPermission(this)) return
        try {
            val active = activeNotifications ?: return
            val supported = active.filter { NotificationParserDispatcher.defaultInstance.isSupportedPackage(it.packageName) }
            events.trySend { AppDatabase.getDatabase(applicationContext).transactionDao().retireMissingSources(supported.map { it.key }) }
            supported.forEach { accept(it, true) }
        } catch (_: Exception) {
            CaptureStatus.error("No se pudieron consultar las notificaciones activas.")
        }
    }

    private fun accept(sbn: StatusBarNotification, recovering: Boolean) {
        val dispatcher = NotificationParserDispatcher.defaultInstance
        if (!dispatcher.isSupportedPackage(sbn.packageName)) return
        CaptureStatus.received()
        try {
            val notification = sbn.notification
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(" ")
            val parsed = dispatcher.parse(sbn.packageName, title, text, bigText) ?: return
            val metadata = NotificationMetadata(sbn.key, sbn.postTime,
                notification.`when`.takeIf { it > 0 && it <= sbn.postTime } ?: sbn.postTime)
            val identified = NotificationIdentity.apply(parsed, metadata)
            val session = PairingPreferences.session(applicationContext)
            val entity = LocalTransactionEntity(
                amount = identified.amount.toPlainString(), flowType = identified.flowType.name,
                contactName = identified.contactName, channel = identified.channel,
                transactionDate = identified.transactionDate.toString(), transactionHash = identified.transactionHash,
                rawText = identified.rawText,
                ownerKey = session?.takeIf { metadata.eventTime >= it.startedAt && sbn.postTime >= it.startedAt }?.ownerKey,
                sourceEventId = identified.transactionHash, sourceKey = sbn.key,
                sourceFingerprint = NotificationIdentity.fingerprint(parsed), sourceActive = true
            )
            events.trySend {
                val inserted = com.financemanager.listener.data.StorageRetry.run(
                    isTransient = { it is SQLiteDatabaseLockedException || it is SQLiteTableLockedException || it is SQLiteDiskIOException }
                ) {
                    AppDatabase.getDatabase(applicationContext).transactionDao().persistNotification(entity, recovering)
                }
                if (inserted != -1L) {
                    CaptureStatus.saved()
                    TransactionSyncWorker.enqueue(applicationContext)
                }
            }
        } catch (_: Exception) {
            CaptureStatus.error("No se pudo leer una notificación de Yape.")
        }
    }

    override fun onDestroy() {
        if (current === this) current = null
        CaptureStatus.connected(false)
        events.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private var current: YapeNotificationListenerService? = null
        private var lastRebind = 0L
        fun reviewActive() { current?.reviewActiveNotifications() }
        fun hasPermission(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val component = ComponentName(context, YapeNotificationListenerService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
        }
    }
}
