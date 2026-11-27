package com.financemanager.listener

import com.financemanager.listener.data.LocalTransactionEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class MobilePaymentMethod(val code: String, val label: String) {
    CASH("CASH", "Efectivo"),
    CARD("CARD", "Tarjeta"),
    WALLET("WALLET", "Billetera"),
    BANK_TRANSFER("BANK_TRANSFER", "Transferencia"),
    OTHER("OTHER", "Otro")
}

private val MAX_MANUAL_AMOUNT = BigDecimal("99999999.99")
private val MIN_MANUAL_AMOUNT = BigDecimal("0.01")

fun parseManualAmount(raw: String): BigDecimal? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val normalized = if (trimmed.count { it == ',' } == 1 && !trimmed.contains('.')) {
        trimmed.replace(',', '.')
    } else {
        trimmed
    }
    if (!normalized.matches(Regex("""^\d+(\.\d{1,2})?$"""))) return null
    val parsed = normalized.toBigDecimalOrNull() ?: return null
    if (parsed < MIN_MANUAL_AMOUNT || parsed > MAX_MANUAL_AMOUNT) return null
    return parsed.setScale(2, RoundingMode.UNNECESSARY)
}

fun buildManualTransactionEntity(
    amount: BigDecimal,
    flowType: String,
    contactName: String,
    paymentMethod: MobilePaymentMethod,
    ownerKey: String?,
    dateTime: LocalDateTime = LocalDateTime.now(),
    requestId: String = UUID.randomUUID().toString()
): LocalTransactionEntity {
    require(flowType == "EXPENSE" || flowType == "INCOME") { "Unsupported flow type" }
    val trimmedContact = contactName.trim()
    require(trimmedContact.isNotEmpty() && trimmedContact.length <= 150) { "Invalid contact name" }
    return LocalTransactionEntity(
        amount = amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
        flowType = flowType,
        contactName = trimmedContact,
        channel = paymentMethod.code,
        transactionDate = dateTime.truncatedTo(ChronoUnit.SECONDS).toString(),
        transactionHash = "manual:$requestId",
        rawText = "Manual",
        isSynced = false,
        ownerKey = ownerKey
    )
}
