package com.financemanager.listener.parser

import java.math.BigDecimal
import java.time.LocalDateTime

enum class FlowType {
    INCOME,
    EXPENSE,
    INTERNAL_TRANSFER
}

data class ParsedTransaction(
    val amount: BigDecimal,
    val flowType: FlowType,
    val contactName: String,
    val channel: String,
    val transactionDate: LocalDateTime = LocalDateTime.now(),
    val transactionHash: String,
    val rawText: String
)
