package com.financemanager.listener.parser

import com.financemanager.listener.capture.NotificationContent
import com.financemanager.listener.capture.ParseResult
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Locale

/**
 * Captures incoming Plin push notifications from Scotiabank Peru (`com.scotiabank.peru`).
 * Card consumptions, bank transfers, and token alerts are ignored (`NonFinancial`) so they do
 * not duplicate rich email receipts handled by `ScotiabankEmailParser`.
 */
class ScotiabankNotificationParser : NotificationParser {

    override val supportedPackages: Set<String> = setOf(
        "com.scotiabank.peru"
    )

    private val incomingPlinWithSenderRegex = Regex(
        """(?:¡?te\s+pline[oó]!?\s*|recibiste\s+un\s+plin\s+de\s+)?([A-Za-zÀ-ÿ0-9\s.*'-]+?)\s+te\s+(?:pline[oó]|envi[oó])(?:\s+un\s+plin)?(?:\s+(?:por|de))?\s+S/\.?\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val incomingPlinGeneralRegex = Regex(
        """(?:¡?te\s+plinearon!?|recibiste\s+un\s+plin(?:\s+de)?|te\s+enviaron\s+un\s+plin)(?:\s+([A-Za-zÀ-ÿ0-9\s.*'-]+?))?(?:\s+(?:por|de))?\s+S/\.?\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val plinIndicatorRegex = Regex(
        """(?<![\p{L}\p{N}_])(?:plin|pline[oó]|plinearon)(?![\p{L}\p{N}_])""",
        RegexOption.IGNORE_CASE
    )

    private val negatedRegex = Regex(
        """(?<![\p{L}\p{N}_])(?:no|nunca)\s+(?:te\s+)?(?:pline[oó]|plinearon|recibiste|envi[oó]|enviaron)(?![\p{L}\p{N}_])""",
        RegexOption.IGNORE_CASE
    )

    private val outgoingRegex = Regex(
        """(?<![\p{L}\p{N}_])(?:plineaste|enviaste|pagaste|realizaste\s+un\s+(?:consumo|compra))(?![\p{L}\p{N}_])""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(title: String?, text: String?, bigText: String?): ParsedTransaction? {
        val body = bigText?.takeIf { it.isNotBlank() } ?: text?.takeIf { it.isNotBlank() }
        val content = "${title.orEmpty()} ${body.orEmpty()}".trim()
        if (content.isBlank()) return null
        if (negatedRegex.containsMatchIn(content)) return null
        if (!plinIndicatorRegex.containsMatchIn(content)) return null
        if (outgoingRegex.containsMatchIn(content)) return null

        val amounts = monetaryTokens(content)
        if (amounts.size != 1) return null
        val amount = amounts.single()
        if (amount < BigDecimal("0.01") || amount > BigDecimal("99999999.99")) return null

        val contact = extractSenderName(content)
        val hash = NotificationIdentity.digest(
            "SCOTIABANK_PLIN|${FlowType.INCOME.name}|${amount.stripTrailingZeros().toPlainString()}|${contact.lowercase(Locale.ROOT)}|$content"
        )

        return ParsedTransaction(
            amount = amount,
            flowType = FlowType.INCOME,
            contactName = contact,
            channel = "PLIN",
            transactionDate = LocalDateTime.now(),
            transactionHash = hash,
            rawText = content
        )
    }

    override fun parseResult(content: NotificationContent): ParseResult {
        val expanded = expandedNotificationText(content.bigText, content.lines)
        val body = expanded ?: content.text
        val joined = "${content.title.orEmpty()} ${body.orEmpty()}".trim()
        parse(content.title, content.text, expanded)?.let { return ParseResult.Recognized(it) }
        if (negatedRegex.containsMatchIn(joined)) return ParseResult.NonFinancial
        if (!plinIndicatorRegex.containsMatchIn(joined)) return ParseResult.NonFinancial
        if (outgoingRegex.containsMatchIn(joined)) return ParseResult.NonFinancial
        val amounts = monetaryTokens(joined)
        if (amounts.size > 1) return ParseResult.Ambiguous("MULTIPLE_AMOUNTS")
        return ParseResult.UnsupportedFormat("UNSUPPORTED_FORMAT")
    }

    private fun extractSenderName(content: String): String {
        incomingPlinWithSenderRegex.find(content)?.let { match ->
            val cleaned = cleanContact(match.groupValues[1])
            if (cleaned != null) return cleaned
        }
        incomingPlinGeneralRegex.find(content)?.let { match ->
            val cleaned = cleanContact(match.groupValues[1])
            if (cleaned != null) return cleaned
        }
        return "Plin Scotiabank"
    }

    private fun cleanContact(raw: String): String? {
        val cleaned = raw
            .replace(Regex("""^¡?(?:te\s+pline(?:aron|[oó])!?|recibiste\s+un\s+plin(?:\s+de)?)\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[.,;:!]+$"""), "")
            .trim()
        if (cleaned.length < 2 || cleaned.equals("scotiabank", true) || cleaned.equals("plin", true) || cleaned.equals("te", true)) {
            return null
        }
        return cleaned
    }

    private fun monetaryTokens(content: String): List<BigDecimal> =
        Regex("""S/\.?\s*([0-9][0-9.,]*)""", RegexOption.IGNORE_CASE).findAll(content).mapNotNull {
            val token = it.groupValues[1].removeSuffix(".")
            if (Regex("""(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\.[0-9]{1,2})?""").matches(token)) {
                token.replace(",", "").toBigDecimalOrNull()
            } else null
        }.toList()
}
