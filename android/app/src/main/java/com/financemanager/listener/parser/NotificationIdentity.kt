package com.financemanager.listener.parser

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

data class NotificationMetadata(
    val key: String,
    val postTime: Long,
    val eventTime: Long = postTime
)

object NotificationIdentity {
    fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun fingerprint(parsed: ParsedTransaction): String {
        // Security codes distinguish otherwise identical receipts; they are never logged.
        val code = Regex("""c[oó]d(?:igo|\.)?\s+de\s+seguridad\s*(?:es)?\s*:\s*(\d+)""",
            RegexOption.IGNORE_CASE).find(parsed.rawText)?.groupValues?.get(1).orEmpty()
        return digest(listOf(parsed.flowType.name, parsed.amount.stripTrailingZeros().toPlainString(),
            parsed.contactName.lowercase(Locale.ROOT).trim(), code).joinToString("|"))
    }

    fun apply(parsed: ParsedTransaction, metadata: NotificationMetadata): ParsedTransaction = parsed.copy(
        transactionDate = Instant.ofEpochMilli(metadata.postTime).atZone(ZoneId.systemDefault()).toLocalDateTime(),
        transactionHash = digest("notification-v2|${metadata.key}|${metadata.eventTime}|${fingerprint(parsed)}")
    )
}
