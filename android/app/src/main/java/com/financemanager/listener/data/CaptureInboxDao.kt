package com.financemanager.listener.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureInboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(observation: CaptureObservation): Long

    @Query("SELECT * FROM capture_inbox WHERE status = 'PENDING' ORDER BY id LIMIT 50")
    fun pending(): List<CaptureObservation>

    @Query("SELECT * FROM capture_inbox WHERE id = :id")
    fun get(id: Long): CaptureObservation?

    @Query("UPDATE capture_inbox SET status = :status, reason = :reason, transactionHash = :hash WHERE id = :id")
    fun finish(id: Long, status: String, reason: String?, hash: String?)

    @Query("UPDATE capture_inbox SET title = NULL, text = NULL, bigText = NULL, lines = '' WHERE id = :id")
    fun clearContent(id: Long)

    @Query("SELECT COUNT(CASE WHEN status = 'PENDING' THEN 1 END) AS pending, COUNT(CASE WHEN status = 'REVIEW' THEN 1 END) AS review FROM capture_inbox WHERE ownerKey = :owner OR ownerKey IS NULL")
    fun counts(owner: String?): Flow<InboxCounts>

    @Query("SELECT * FROM capture_inbox WHERE status = 'REVIEW' AND (ownerKey = :owner OR ownerKey IS NULL) ORDER BY receivedAt DESC LIMIT 100")
    fun review(owner: String?): Flow<List<CaptureObservation>>

    @Query("UPDATE capture_inbox SET ownerKey = :verifiedOwner WHERE ownerKey = :credentialOwner")
    fun upgradeVerifiedOwner(credentialOwner: String, verifiedOwner: String)

    // Keep unresolved evidence for seven days; retain its status/identity after redaction.
    @Query("UPDATE capture_inbox SET title = NULL, text = NULL, bigText = NULL, lines = '' WHERE status != 'PENDING' AND receivedAt < :before")
    fun redactExpired(before: Long)

    @Query("DELETE FROM capture_inbox WHERE status IN ('DUPLICATE', 'IGNORED', 'CONTROL') AND receivedAt < :before")
    fun pruneReceipts(before: Long)
}
