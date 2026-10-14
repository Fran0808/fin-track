package com.financemanager.listener

import com.financemanager.listener.data.LocalTransactionEntity
import java.time.LocalDate
import java.time.LocalDateTime

enum class MovementStatus(val label: String) {
    ALL("Todos"), TO_SEND("Por enviar"), SENT("Enviados"), UNASSIGNED("Sin cuenta")
}

fun filterMovements(rows: List<LocalTransactionEntity>, query: String, status: MovementStatus,
    start: LocalDate? = null, end: LocalDate? = null): List<LocalTransactionEntity> = rows.filter { row ->
    val date = runCatching { LocalDateTime.parse(row.transactionDate).toLocalDate() }.getOrNull()
    val matchesDate = (start == null && end == null) ||
        (date != null && (start == null || !date.isBefore(start)) && (end == null || !date.isAfter(end)))
    val matchesStatus = when (status) {
        MovementStatus.ALL -> true
        MovementStatus.TO_SEND -> row.ownerKey != null && !row.isSynced
        MovementStatus.SENT -> row.isSynced
        MovementStatus.UNASSIGNED -> row.ownerKey == null
    }
    matchesDate && matchesStatus && (query.isBlank() ||
        row.contactName.contains(query.trim(), ignoreCase = true) || row.amount.contains(query.trim()))
}
