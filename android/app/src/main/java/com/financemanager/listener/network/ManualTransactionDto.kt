package com.financemanager.listener.network

import java.math.BigDecimal

data class ManualTransactionDto(
    val requestId: String,
    val amount: BigDecimal,
    val flowType: String,
    val contactName: String,
    val transactionDate: String,
    val paymentMethod: String
)
