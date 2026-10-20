package com.financemanager.listener.capture

import androidx.room.withTransaction
import com.financemanager.listener.data.*
import com.financemanager.listener.parser.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ProcessedCapture(val status: String, val hash: String? = null)
class NotificationReviewRequired : RuntimeException()

/** Only committed observations reach this processor. Financial writes and completion are atomic. */
class NotificationInboxProcessor(
    private val db: AppDatabase,
    private val dispatcher: NotificationParserDispatcher = NotificationParserDispatcher.defaultInstance
) {
    suspend fun drain(onProcessed: (ProcessedCapture) -> Unit = {}): Boolean = lock.withLock {
        var inserted = false
        while (true) {
            val batch = db.captureInboxDao().pending()
            if (batch.isEmpty()) break
            for (observation in batch) {
                val result = db.withTransaction {
                    val current = db.captureInboxDao().get(observation.id)
                    if (current?.status != "PENDING") return@withTransaction ProcessedCapture("DUPLICATE")
                    process(current)
                }
                if (result.status == "SAVED") inserted = true
                onProcessed(result)
            }
        }
        db.captureInboxDao().redactExpired(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)
        db.captureInboxDao().pruneReceipts(System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L)
        inserted
    }

    private fun process(observation: CaptureObservation): ProcessedCapture {
        val inbox = db.captureInboxDao()
        val transactions = db.transactionDao()
        fun finish(status: String, reason: String? = null, hash: String? = null): ProcessedCapture {
            inbox.finish(observation.id, status, reason, hash)
            if (status != "REVIEW") inbox.clearContent(observation.id)
            return ProcessedCapture(status, hash)
        }
        when (observation.kind) {
            "REMOVED" -> { transactions.retireSource(observation.sourceKey); return finish("CONTROL") }
            "RECONCILE" -> { transactions.retireMissingSources(CaptureEncoding.decode(observation.lines)); return finish("CONTROL") }
            "POSTED" -> Unit
            else -> return finish("REVIEW", "UNKNOWN_KIND")
        }
        if (!dispatcher.isSupportedPackage(observation.packageName)) return finish("IGNORED", "UNSUPPORTED_PACKAGE")
        if (observation.groupSummary) return finish("IGNORED", "GROUP_SUMMARY")
        if (observation.truncated) return finish("REVIEW", "CONTENT_LIMIT")
        val content = try { observation.content() }
            catch (_: IllegalArgumentException) { return finish("REVIEW", "INVALID_CONTENT_ENCODING") }
        val result = dispatcher.parseResult(observation.packageName, content)
        val parsed = when (result) {
            is ParseResult.Recognized -> result.transaction
            is ParseResult.NonFinancial -> return finish("IGNORED", "NON_FINANCIAL")
            is ParseResult.Ambiguous -> return finish("REVIEW", result.reason)
            is ParseResult.UnsupportedFormat -> return finish("REVIEW", result.reason)
        }
        val identified = NotificationIdentity.apply(parsed,
            NotificationMetadata(observation.sourceKey, observation.postTime, observation.eventTime)).copy(
                transactionDate = java.time.Instant.ofEpochMilli(observation.postTime)
                    .atZone(java.time.ZoneId.of(observation.zoneId)).toLocalDateTime())
        val entity = LocalTransactionEntity(amount = identified.amount.toPlainString(), flowType = identified.flowType.name,
            contactName = identified.contactName, channel = identified.channel, transactionDate = identified.transactionDate.toString(),
            transactionHash = identified.transactionHash, rawText = identified.rawText, ownerKey = observation.ownerKey,
            sourceEventId = identified.transactionHash, sourceKey = observation.sourceKey,
            sourceFingerprint = NotificationIdentity.fingerprint(parsed), sourceActive = true)
        return try {
            val id = transactions.persistNotificationWithinTransaction(entity, observation.recovering)
            val storedHash = transactions.findEvent(identified.transactionHash)?.transactionHash ?: identified.transactionHash
            finish(if (id == -1L) "DUPLICATE" else "SAVED", hash = storedHash)
        } catch (_: NotificationReviewRequired) {
            finish("REVIEW", "REUSED_ACTIVE_SOURCE")
        } catch (_: LegacyReviewRequired) {
            finish("REVIEW", "LEGACY_AMBIGUITY")
        }
    }

    companion object { private val lock = Mutex() }
}
