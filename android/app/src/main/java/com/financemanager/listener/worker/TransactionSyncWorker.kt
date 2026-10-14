package com.financemanager.listener.worker

import android.content.Context
import androidx.work.*
import com.financemanager.listener.data.AppDatabase
import com.financemanager.listener.data.PairingPreferences
import com.financemanager.listener.data.PairingSession
import com.financemanager.listener.network.ApiClient
import com.financemanager.listener.network.TransactionSyncDto
import com.financemanager.listener.service.CaptureStatus
import com.financemanager.listener.service.CaptureEvent
import com.financemanager.listener.service.SyncPhase
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class TransactionSyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = SyncCoordinator.serialized { syncPending() }

    private suspend fun syncPending(): Result {
        var activeSession: PairingSession? = null
        var problem: SyncProblem? = null
        return try {
            val session = PairingPreferences.session(applicationContext) ?: return Result.success()
            activeSession = session
            val dao = AppDatabase.getDatabase(applicationContext).transactionDao()
            val entries = dao.getUnsyncedTransactions(session.ownerKey)
            CaptureStatus.sync("Enviando ${entries.size} movimientos a la web…", SyncPhase.SENDING)
            val service = ApiClient.getService(session.serverUrl)
            val outcome = SyncEngine.run(entries, session.ownerKey,
                isCurrentSession = { PairingPreferences.session(applicationContext) == session },
                send = { entity ->
                    val dto = TransactionSyncDto(
                        amount = BigDecimal(entity.amount), flowType = entity.flowType,
                        contactName = entity.contactName, channel = entity.channel,
                        transactionDate = entity.transactionDate, transactionHash = entity.transactionHash,
                        rawNotificationText = entity.rawText
                    )
                    val response = service.syncBatch(session.token, listOf(dto))
                    SyncReply(response.code(), response.body()?.map { it.transactionHash }.orEmpty())
                },
                acknowledge = {
                    dao.markAsSynced(it.transactionHash, session.ownerKey)
                    CaptureStatus.event(CaptureEvent.SENT)
                },
                onProblem = { problem = it }
            )
            if (PairingPreferences.session(applicationContext) != session) return Result.success()
            when (outcome) {
                SyncOutcome.COMPLETE -> {
                    CaptureStatus.sync(if (entries.isEmpty()) "No hay movimientos por enviar a este destino." else "${entries.size} movimientos confirmados por la web.", SyncPhase.COMPLETE)
                    Result.success()
                }
                SyncOutcome.SESSION_CHANGED -> Result.success()
                SyncOutcome.AUTH_REQUIRED -> {
                    CaptureStatus.event(CaptureEvent.SEND_FAILED)
                    CaptureStatus.sync("Conecta de nuevo con la web: el servidor rechazó la autorización.", SyncPhase.AUTH_REQUIRED)
                    Result.failure()
                }
                SyncOutcome.REJECTED -> {
                    CaptureStatus.event(CaptureEvent.SEND_FAILED)
                    CaptureStatus.sync(problem?.message ?: "Algunos movimientos fueron rechazados; se conservaron para revisión.", SyncPhase.REJECTED)
                    Result.failure()
                }
                SyncOutcome.RETRY -> retryOrStop(problem)
            }
        } catch (e: CancellationException) {
            if (activeSession != null && PairingPreferences.session(applicationContext) == activeSession) {
                CaptureStatus.sync("El envío se pausó. Los movimientos siguen guardados y puedes volver a intentarlo.", SyncPhase.RETRY)
            }
            throw e
        } catch (error: Exception) {
            if (activeSession != null && PairingPreferences.session(applicationContext) != activeSession) return Result.success()
            retryOrStop(syncProblem(error))
        }
    }

    private fun retryOrStop(problem: SyncProblem?): Result {
        CaptureStatus.event(CaptureEvent.SEND_FAILED)
        val detail = problem?.message ?: "No se pudo completar el envío."
        return if (inputData.getBoolean(MANUAL, false)) {
            CaptureStatus.sync("$detail Puedes pulsar ‘Enviar ahora’ para volver a intentarlo.", SyncPhase.RETRY)
            Result.failure()
        } else if (runAttemptCount < 4) {
            CaptureStatus.sync("$detail Se reintentará automáticamente.", SyncPhase.RETRY)
            Result.retry()
        } else {
            CaptureStatus.sync("$detail No se pudo enviar tras 5 intentos. Puedes reintentar manualmente.", SyncPhase.RETRY)
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "TransactionSyncWork"
        private const val MANUAL_WORK_NAME = "TransactionManualSyncWork"
        private const val MANUAL = "manual_send"
        fun enqueue(context: Context, manual: Boolean = false) {
            if (PairingPreferences.session(context) == null) {
                CaptureStatus.sync("Conecta el teléfono con la web antes de enviar movimientos.", SyncPhase.AUTH_REQUIRED)
                return
            }
            CaptureStatus.sync(if (manual) "Envío manual solicitado. Android lo iniciará cuando haya conexión; si ya hay un envío en curso, esperará a que termine."
                else "Envío solicitado. Se realizará cuando haya conexión.", SyncPhase.QUEUED)
            val request = OneTimeWorkRequestBuilder<TransactionSyncWorker>()
                .setInputData(workDataOf(MANUAL to manual))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                if (manual) MANUAL_WORK_NAME else WORK_NAME,
                if (manual) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(MANUAL_WORK_NAME)
        }
    }
}
