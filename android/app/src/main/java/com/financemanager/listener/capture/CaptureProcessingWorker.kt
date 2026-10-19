package com.financemanager.listener.capture

import android.content.Context
import androidx.work.*
import com.financemanager.listener.service.CaptureStatus
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** No network constraint: offline capture processing must continue. */
class CaptureProcessingWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        CaptureRuntime.process(applicationContext)
        Result.success()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) {
        CaptureStatus.error("Las capturas siguen guardadas; el procesamiento requiere atención.")
        Result.retry()
    }
    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<CaptureProcessingWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
            // Appending prevents losing a wakeup as another worker finishes its empty read.
            WorkManager.getInstance(context).enqueueUniqueWork("CaptureInboxProcessing", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
