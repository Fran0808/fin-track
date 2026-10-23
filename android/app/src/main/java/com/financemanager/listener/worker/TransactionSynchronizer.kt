package com.financemanager.listener.worker

import android.content.Context
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

enum class SyncAttemptResult { SUCCESS, FAILURE, RETRY }

class TransactionSynchronizer(
    private val applicationContext: Context,
    private val manual: Boolean = false,
    private val runAttemptCount: Int = 0
) {
    suspend fun run(): SyncAttemptResult {
        var activeSession: PairingSession? = null
        var problem: SyncProblem? = null
        return try {
            val session = PairingPreferences.session(applicationContext) ?: return SyncAttemptResult.SUCCESS
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
            if (PairingPreferences.session(applicationContext) != session) return SyncAttemptResult.SUCCESS
            when (outcome) {
                SyncOutcome.COMPLETE -> {
                    CaptureStatus.sync(if (entries.isEmpty()) "No hay movimientos por enviar a este destino." else "${entries.size} movimientos confirmados por la web.", SyncPhase.COMPLETE)
                    SyncAttemptResult.SUCCESS
                }
                SyncOutcome.SESSION_CHANGED -> SyncAttemptResult.SUCCESS
                SyncOutcome.AUTH_REQUIRED -> {
                    CaptureStatus.event(CaptureEvent.SEND_FAILED)
                    CaptureStatus.sync("Conecta de nuevo con la web: el servidor rechazó la autorización.", SyncPhase.AUTH_REQUIRED)
                    SyncAttemptResult.FAILURE
                }
                SyncOutcome.REJECTED -> {
                    CaptureStatus.event(CaptureEvent.SEND_FAILED)
                    CaptureStatus.sync(problem?.message ?: "Algunos movimientos fueron rechazados; se conservaron para revisión.", SyncPhase.REJECTED)
                    SyncAttemptResult.FAILURE
                }
                SyncOutcome.RETRY -> retryOrStop(problem)
            }
        } catch (e: CancellationException) {
            if (activeSession != null && PairingPreferences.session(applicationContext) == activeSession) {
                CaptureStatus.sync("El envío se pausó. Los movimientos siguen guardados y puedes volver a intentarlo.", SyncPhase.RETRY)
            }
            throw e
        } catch (error: Exception) {
            if (activeSession != null && PairingPreferences.session(applicationContext) != activeSession) return SyncAttemptResult.SUCCESS
            retryOrStop(syncProblem(error))
        }
    }

    private fun retryOrStop(problem: SyncProblem?): SyncAttemptResult {
        CaptureStatus.event(CaptureEvent.SEND_FAILED)
        if (problem?.failure == SyncFailure.HTTPS_REQUIRED) {
            CaptureStatus.sync(problem.message, SyncPhase.REJECTED)
            return SyncAttemptResult.FAILURE
        }
        val detail = problem?.message ?: "No se pudo completar el envío."
        return if (manual) {
            CaptureStatus.sync("$detail Puedes pulsar ‘Enviar ahora’ para volver a intentarlo.", SyncPhase.RETRY)
            SyncAttemptResult.FAILURE
        } else if (runAttemptCount < 4) {
            CaptureStatus.sync("$detail Se reintentará automáticamente.", SyncPhase.RETRY)
            SyncAttemptResult.RETRY
        } else {
            CaptureStatus.sync("$detail No se pudo enviar tras 5 intentos. Puedes reintentar manualmente.", SyncPhase.RETRY)
            SyncAttemptResult.FAILURE
        }
    }
}
