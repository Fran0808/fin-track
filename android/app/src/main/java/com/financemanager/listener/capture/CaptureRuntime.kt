package com.financemanager.listener.capture

import android.content.Context
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteTableLockedException
import android.database.sqlite.SQLiteDiskIOException
import com.financemanager.listener.data.*
import com.financemanager.listener.service.CaptureEvent
import com.financemanager.listener.service.CaptureStatus
import com.financemanager.listener.worker.TransactionSyncWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Application lifetime, independent of the listener's onDestroy. */
class CaptureRuntime private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val db by lazy { AppDatabase.getDatabase(context) }
    private val ingress = DurableCaptureQueue<() -> CaptureObservation>(scope,
        persist = { snapshot ->
            val observation = snapshot()
            val inserted = StorageRetry.run(isTransient = ::isTransientStorageError) { db.captureInboxDao().insert(observation) }
            if (inserted > 0 && observation.kind == "POSTED") CaptureStatus.event(CaptureEvent.CAPTURED)
        },
        onCommitted = {
            wakeups.trySend(Unit)
            runCatching { CaptureProcessingWorker.enqueue(context) }
                .onFailure { CaptureStatus.error("La captura está guardada; no se pudo programar su procesamiento en segundo plano.") }
        },
        onFailure = { CaptureStatus.error("No se pudo guardar una captura. Revisa las notificaciones aún visibles.") }
    )
    init {
        scope.launch {
            for (ignored in wakeups) {
                try { process(context) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { CaptureStatus.error("Hay capturas guardadas pendientes de procesar. Se volverá a intentar.") }
            }
        }
    }
    fun submit(snapshot: () -> CaptureObservation) {
        if (!ingress.offer(snapshot)) CaptureStatus.error("La captura está saturada. Revisa las notificaciones aún visibles.")
    }
    fun resume() {
        wakeups.trySend(Unit)
        CaptureProcessingWorker.enqueue(context)
        TransactionSyncWorker.enqueue(context)
    }
    companion object {
        @Volatile private var instance: CaptureRuntime? = null
        fun get(context: Context): CaptureRuntime = instance ?: synchronized(this) {
            instance ?: CaptureRuntime(context.applicationContext).also { instance = it }
        }
        suspend fun process(context: Context) {
            var saved = false
            try { NotificationInboxProcessor(AppDatabase.getDatabase(context)).drain { result ->
                when (result.status) {
                    "SAVED" -> { saved = true; CaptureStatus.saved() }
                    "DUPLICATE" -> CaptureStatus.event(CaptureEvent.DUPLICATE)
                    "REVIEW" -> CaptureStatus.event(CaptureEvent.REVIEW)
                }
            } } finally { if (saved) TransactionSyncWorker.enqueue(context) }
        }
        fun isTransientStorageError(error: Exception): Boolean = error is SQLiteDatabaseLockedException ||
            error is SQLiteTableLockedException || error is SQLiteDiskIOException
    }
}
