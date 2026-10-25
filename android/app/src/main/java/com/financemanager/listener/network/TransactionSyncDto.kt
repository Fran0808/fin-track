package com.financemanager.listener.network

import java.math.BigDecimal

data class TransactionSyncDto(
    val amount: BigDecimal,
    val flowType: String,
    val contactName: String,
    val channel: String = "YAPE",
    val transactionDate: String,
    val transactionHash: String,
    val rawNotificationText: String? = null,
    val operationType: String? = null
)
