package com.financemanager.listener.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_transactions",
    indices = [Index(value = ["transactionHash"], unique = true), Index(value = ["sourceEventId"], unique = true)]
)
data class LocalTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: String,
    val flowType: String, // INCOME or EXPENSE
    val contactName: String,
    val channel: String = "YAPE",
    val transactionDate: String,
    val transactionHash: String,
    val rawText: String,
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val ownerKey: String? = null,
    val sourceEventId: String? = null,
    val sourceKey: String? = null,
    val sourceFingerprint: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val sourceActive: Boolean = false
)
