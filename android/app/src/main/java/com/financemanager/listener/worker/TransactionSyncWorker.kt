package com.financemanager.listener.worker

import android.content.Context
import androidx.work.*
import com.financemanager.listener.data.PairingPreferences
import com.financemanager.listener.service.CaptureStatus
import com.financemanager.listener.service.SyncPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

class TransactionSyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = SyncCoordinator.serialized {
        when (TransactionSynchronizer(applicationContext, inputData.getBoolean(MANUAL, false), runAttemptCount).run()) {
            SyncAttemptResult.SUCCESS -> Result.success()
            SyncAttemptResult.FAILURE -> Result.failure()
            SyncAttemptResult.RETRY -> Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "TransactionSyncWork"
        private const val MANUAL_WORK_NAME = "TransactionManualSyncWork"
        private const val MANUAL = "manual_send"
        private val manualDispatcher = ManualSyncDispatcher(CoroutineScope(SupervisorJob() + Dispatchers.IO))

        fun enqueue(context: Context, manual: Boolean = false) {
            if (PairingPreferences.session(context) == null) {
                CaptureStatus.sync("Conecta el teléfono con la web antes de enviar movimientos.", SyncPhase.AUTH_REQUIRED)
                return
            }
            if (manual) {
                val appContext = context.applicationContext
                manualDispatcher.start {
                    CaptureStatus.sync("Preparando envío manual. Si hay un envío en curso, esperará a que termine.", SyncPhase.QUEUED)
                    SyncCoordinator.serialized { TransactionSynchronizer(appContext, manual = true).run() }
                }
                return
            }
            CaptureStatus.sync("Envío solicitado. Se realizará cuando haya conexión.", SyncPhase.QUEUED)
            val request = OneTimeWorkRequestBuilder<TransactionSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun cancel(context: Context) {
            manualDispatcher.cancel()
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            // Cancel manual requests persisted by older app versions as well.
            WorkManager.getInstance(context).cancelUniqueWork(MANUAL_WORK_NAME)
        }
    }
}
