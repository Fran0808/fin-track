package com.financemanager.listener.worker

import android.content.Context
import androidx.work.*
import com.financemanager.listener.data.AppDatabase
import com.financemanager.listener.data.PairingPreferences
import com.financemanager.listener.network.ApiClient
import com.financemanager.listener.network.TransactionSyncDto
import com.financemanager.listener.service.CaptureStatus
import com.financemanager.listener.service.CaptureEvent
import com.financemanager.listener.service.SyncPhase
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class TransactionSyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            val session = PairingPreferences.session(applicationContext) ?: return Result.success()
            val dao = AppDatabase.getDatabase(applicationContext).transactionDao()
            val entries = dao.getUnsyncedTransactions(session.ownerKey)
            CaptureStatus.sync("Enviando ${entries.size} movimientos a tu cuenta…", SyncPhase.SENDING)
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
                }
            )
            if (PairingPreferences.session(applicationContext) != session) return Result.success()
            when (outcome) {
                SyncOutcome.COMPLETE -> {
                    CaptureStatus.sync(if (entries.isEmpty()) "Todos tus movimientos están enviados." else "${entries.size} movimientos confirmados por la web.", SyncPhase.COMPLETE)
                    Result.success()
                }
                SyncOutcome.SESSION_CHANGED -> Result.success()
                SyncOutcome.AUTH_REQUIRED -> {
                    CaptureStatus.sync("Vuelve a vincular la cuenta: el servidor rechazó la autorización.", SyncPhase.AUTH_REQUIRED)
                    Result.failure()
                }
                SyncOutcome.REJECTED -> {
                    CaptureStatus.sync("Algunos movimientos fueron rechazados; se conservaron para revisión.", SyncPhase.REJECTED)
                    Result.failure()
                }
                SyncOutcome.RETRY -> retryOrStop()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            retryOrStop()
        }
    }

    private fun retryOrStop(): Result {
        return if (runAttemptCount < 4) {
            CaptureStatus.sync("El envío se reintentará automáticamente.", SyncPhase.RETRY)
            Result.retry()
        } else {
            CaptureStatus.sync("No se pudo enviar tras 5 intentos. Puedes reintentar manualmente.", SyncPhase.RETRY)
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "TransactionSyncWork"
        fun enqueue(context: Context) {
            if (PairingPreferences.session(context) == null) return
            CaptureStatus.sync("Envío solicitado. Se realizará cuando haya conexión.", SyncPhase.QUEUED)
            val request = OneTimeWorkRequestBuilder<TransactionSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
        fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
    }
}
