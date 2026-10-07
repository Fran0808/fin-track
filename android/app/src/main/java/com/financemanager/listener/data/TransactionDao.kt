package com.financemanager.listener.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insert(transaction: LocalTransactionEntity): Long

    @Query("SELECT * FROM local_transactions WHERE isSynced = 0 AND ownerKey = :owner ORDER BY createdAt ASC")
    abstract fun getUnsyncedTransactions(owner: String): List<LocalTransactionEntity>

    @Query("UPDATE local_transactions SET isSynced = 1 WHERE transactionHash = :hash AND ownerKey = :owner")
    abstract fun markAsSynced(hash: String, owner: String)

    @Query("SELECT * FROM local_transactions WHERE ownerKey = :owner ORDER BY createdAt DESC LIMIT 50")
    abstract fun getRecentTransactionsFlow(owner: String?): Flow<List<LocalTransactionEntity>>

    @Query("SELECT COUNT(*) FROM local_transactions WHERE isSynced = 0 AND ownerKey = :owner")
    abstract fun pendingCount(owner: String?): Flow<Int>

    @Query("SELECT COUNT(*) FROM local_transactions WHERE ownerKey IS NULL")
    abstract fun unassignedCount(): Flow<Int>

    @Query("SELECT * FROM local_transactions WHERE ownerKey IS NULL ORDER BY createdAt DESC LIMIT 50")
    abstract fun unassignedTransactions(): Flow<List<LocalTransactionEntity>>

    @Query("UPDATE local_transactions SET ownerKey = :owner WHERE id = :id AND ownerKey IS NULL AND isSynced = 0")
    abstract fun assignReviewedEntry(id: Long, owner: String): Int

    @Query("UPDATE local_transactions SET ownerKey = :verifiedOwner WHERE ownerKey = :credentialOwner")
    abstract fun upgradeVerifiedOwner(credentialOwner: String, verifiedOwner: String)

    @Query("SELECT * FROM local_transactions WHERE sourceEventId = :eventId LIMIT 1")
    abstract fun findEvent(eventId: String): LocalTransactionEntity?

    @Query("SELECT * FROM local_transactions WHERE sourceKey = :key AND sourceFingerprint = :fingerprint AND sourceActive = 1 ORDER BY id DESC LIMIT 1")
    abstract fun findActiveEvent(key: String, fingerprint: String): LocalTransactionEntity?

    @Query("SELECT * FROM local_transactions WHERE sourceEventId IS NULL AND flowType = :flow AND channel = :channel")
    abstract fun legacyCandidates(flow: String, channel: String): List<LocalTransactionEntity>

    @Query("UPDATE local_transactions SET sourceEventId = :eventId, sourceKey = :key, sourceFingerprint = :fingerprint, sourceActive = 1 WHERE id = :id")
    abstract fun attachSource(id: Long, eventId: String, key: String, fingerprint: String)

    @Query("UPDATE local_transactions SET sourceActive = 0 WHERE sourceKey = :key")
    abstract fun retireSource(key: String)

    @Query("UPDATE local_transactions SET sourceActive = 0 WHERE sourceKey NOT IN (:activeKeys)")
    abstract fun retireMissingSources(activeKeys: List<String>)

    @Query("UPDATE local_transactions SET sourceActive = 1 WHERE id = :id")
    abstract fun reactivateSource(id: Long)

    @Transaction
    open fun persistNotification(entity: LocalTransactionEntity, recovering: Boolean): Long {
        val eventId = requireNotNull(entity.sourceEventId)
        val key = requireNotNull(entity.sourceKey)
        val fingerprint = requireNotNull(entity.sourceFingerprint)
        val existing = findEvent(eventId)
        if (existing != null) {
            retireSource(key)
            reactivateSource(existing.id)
            return -1
        }
        if (findActiveEvent(key, fingerprint) != null) return -1
        if (recovering) {
            val candidates = legacyCandidates(entity.flowType, entity.channel).filter {
                it.rawText == entity.rawText || (it.amount.toBigDecimalOrNull()?.compareTo(entity.amount.toBigDecimal()) == 0 &&
                    it.contactName.equals(entity.contactName, ignoreCase = true) &&
                    LegacyReconciliation.matches(it.transactionDate, entity.transactionDate))
            }
            if (candidates.size > 1) throw LegacyReviewRequired()
            if (candidates.size == 1) {
                if (!LegacyReconciliation.matches(candidates.single().transactionDate, entity.transactionDate)) throw LegacyReviewRequired()
                attachSource(candidates.single().id, eventId, key, fingerprint)
                return -1
            }
        }
        retireSource(key)
        return insert(entity)
    }
}
