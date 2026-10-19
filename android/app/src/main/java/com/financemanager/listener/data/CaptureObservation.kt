package com.financemanager.listener.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.financemanager.listener.capture.CaptureEncoding
import com.financemanager.listener.capture.NotificationContent
import java.util.UUID

@Entity(tableName = "capture_inbox", indices = [Index(value = ["observationKey"], unique = true), Index(value = ["status", "id"])])
data class CaptureObservation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val observationKey: String,
    val kind: String = "POSTED",
    val packageName: String = "",
    val sourceKey: String = "",
    val postTime: Long = 0,
    val eventTime: Long = 0,
    val receivedAt: Long = System.currentTimeMillis(),
    val ownerKey: String? = null,
    val recovering: Boolean = false,
    val groupSummary: Boolean = false,
    val truncated: Boolean = false,
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val lines: String = "",
    val status: String = "PENDING",
    val reason: String? = null,
    val transactionHash: String? = null,
    val zoneId: String = java.time.ZoneId.systemDefault().id
) {
    fun content() = NotificationContent(title, text, bigText, CaptureEncoding.decode(lines))

    companion object {
        // Room is private app storage. No tokens, server URLs or current-session lookups are persisted here.
        fun posted(packageName: String, key: String, postTime: Long, eventTime: Long,
                   receivedAt: Long, owner: String?, recovering: Boolean, summary: Boolean,
                   content: NotificationContent, truncated: Boolean = false,
                   zoneId: String = java.time.ZoneId.systemDefault().id): CaptureObservation {
            val lines = CaptureEncoding.encode(content.lines)
            // Record every delivery, including a replay after removal. Financial identity is resolved later.
            return CaptureObservation(observationKey = UUID.randomUUID().toString(), packageName = packageName,
                sourceKey = key, postTime = postTime, eventTime = eventTime, receivedAt = receivedAt, ownerKey = owner,
                recovering = recovering, groupSummary = summary, truncated = truncated, title = content.title,
                text = content.text, bigText = content.bigText, lines = lines, zoneId = zoneId)
        }
    }
}

data class InboxCounts(val pending: Int, val review: Int)
