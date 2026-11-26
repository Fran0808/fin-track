package com.financemanager.listener

import java.util.Locale

fun formatChannelLabel(channel: String): String {
    val normalized = channel.trim().uppercase(Locale.ROOT)
    return when (normalized) {
        "YAPE" -> "Yape"
        "PLIN" -> "Plin"
        "TARJETA_CREDITO_BCP",
        "TARJETA_CREDITO_INTERBANK",
        "TARJETA_CREDITO_BBVA",
        "TARJETA_CREDITO_SCOTIABANK" -> "Tarjeta de crédito"
        "TARJETA_DEBITO_BCP",
        "TARJETA_DEBITO_INTERBANK",
        "TARJETA_DEBITO_BBVA",
        "TARJETA_DEBITO_SCOTIABANK" -> "Tarjeta de débito"
        "BCP_TRANSFERENCIA",
        "INTERBANK_TRANSFERENCIA",
        "BBVA_TRANSFERENCIA",
        "SCOTIABANK_TRANSFERENCIA",
        "TRANSFERENCIA" -> "Transferencia"
        else -> normalized
            .split('_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-PE")) }
            }
            .ifBlank { "Movimiento" }
    }
}
