package com.financemanager.listener.worker

import com.financemanager.listener.data.LocalTransactionEntity
import kotlinx.coroutines.CancellationException
import java.io.IOException

data class SyncReply(val code: Int, val hashes: List<String>)
enum class SyncOutcome { COMPLETE, RETRY, REJECTED, AUTH_REQUIRED, SESSION_CHANGED }

object SyncEngine {
    suspend fun run(
        entries: List<LocalTransactionEntity>, owner: String,
        isCurrentSession: () -> Boolean,
        send: suspend (LocalTransactionEntity) -> SyncReply,
        acknowledge: (LocalTransactionEntity) -> Unit,
        onProblem: (SyncProblem) -> Unit = {}
    ): SyncOutcome {
        var rejected = false
        for (entry in entries) {
            if (!isCurrentSession()) return SyncOutcome.SESSION_CHANGED
            if (entry.ownerKey != owner) continue
            try {
                val response = send(entry)
                when {
                    response.code in 200..299 -> {
                        if (response.hashes != listOf(entry.transactionHash)) {
                            onProblem(SyncProblem(SyncFailure.MISSING_RECEIPT))
                            return SyncOutcome.RETRY
                        }
                        acknowledge(entry)
                    }
                    response.code == 401 || response.code == 403 -> {
                        onProblem(SyncProblem(SyncFailure.AUTHORIZATION, response.code))
                        return SyncOutcome.AUTH_REQUIRED
                    }
                    response.code == 408 || response.code == 429 || response.code >= 500 -> {
                        onProblem(SyncProblem(SyncFailure.HTTP, response.code))
                        return SyncOutcome.RETRY
                    }
                    else -> {
                        onProblem(SyncProblem(SyncFailure.HTTP, response.code))
                        rejected = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (error: IOException) {
                onProblem(syncProblem(error))
                return SyncOutcome.RETRY
            } catch (_: IllegalArgumentException) {
                onProblem(SyncProblem(SyncFailure.INVALID_RECORD))
                // A malformed local record must not prevent subsequent records from syncing.
                rejected = true
            }
        }
        return if (rejected) SyncOutcome.REJECTED else SyncOutcome.COMPLETE
    }
}
