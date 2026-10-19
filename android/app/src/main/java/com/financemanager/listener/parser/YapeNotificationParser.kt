package com.financemanager.listener.parser

import com.financemanager.listener.capture.NotificationContent
import com.financemanager.listener.capture.ParseResult
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Locale

open class YapeNotificationParser : NotificationParser {

    override val supportedPackages: Set<String> = setOf(
        "com.bcp.innovacxion.yapeapp"
    )

    // 1. Incomes with explicit sender name
    private val incomeWithSenderRegex = Regex(
        """(?:confirmaci[oó]n de pago!?\s*)?(?:¡?te yape(?:ó|aron|aste)?!?)?\s*([A-Za-zÀ-ÿ0-9\s.*'-]+?)\s+te\s+(?:envi[oó]|yape[oó])(?:\s+un\s+pago)?(?:\s+(?:por|de))?\s+S/\.?\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // 2. Incomes without explicit sender
    private val incomeGeneralRegex = Regex(
        """(?:¡?te yape(?:ó|aron|aste)?!?\s*)?(?:te\s+(?:enviaron|yapearon)|recibiste\s+un\s+yape(?:\s+de)?)(?:\s+un\s+pago)?(?:\s+(?:por|de))?\s+S/\.?\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // 3. Fallback for minimal income notifications
    private val incomeFallbackRegex = Regex(
        """(?:¡?te yape(?:ó|aron)?!?)\s*(?:a tu yape)?(?:\s+(?:por|de))?\s*S/\.?\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // 4. Outgoing expenses
    private val expenseRegex = Regex(
        """(?:¡?yapeaste!?|enviaste|pagaste)\s+S/\.?\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\.[0-9]{1,2})?)\s+a\s+([A-Za-zÀ-ÿ0-9\s.*'-]+)""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(title: String?, text: String?, bigText: String?): ParsedTransaction? {
        // Do not reinterpret an invalid expanded amount using a truncated short body.
        val body = bigText?.takeIf { it.isNotBlank() } ?: text?.takeIf { it.isNotBlank() }
        val content = "${title.orEmpty()} ${body.orEmpty()}".trim()
        if (content.isBlank()) return null
        if (negatedOperation(content)) return null
        parseContent(content)?.let { return it }
        // Only collapse one title mention and one body mention of the same amount.
        // Multiple amounts within a body still require review; never rescue invalid expanded content.
        val titleTokens = monetaryTokens(title.orEmpty())
        val bodyTokens = monetaryTokens(body.orEmpty())
        if (titleTokens.size == 1 && bodyTokens.size == 1 &&
            titleTokens.single().compareTo(bodyTokens.single()) == 0 && !conflictingDirections(content)) {
            val parsedBody = body?.let { parseContent(it) } ?: return null
            val parsedTitle = title?.let { parseContent(it) }
            if (parsedTitle != null && (parsedTitle.flowType != parsedBody.flowType ||
                    (!parsedTitle.contactName.equals("Yape", true) && !parsedBody.contactName.equals("Yape", true) &&
                        !parsedTitle.contactName.equals(parsedBody.contactName, true)))) return null
            return parsedBody.copy(rawText = content)
        }
        return null
    }

    override fun parseResult(content: NotificationContent): ParseResult {
        val expanded = expandedNotificationText(content.bigText, content.lines)
        val body = expanded ?: content.text
        val joined = "${content.title.orEmpty()} ${body.orEmpty()}".trim()
        parse(content.title, content.text, expanded)?.let { return ParseResult.Recognized(it) }
        if (negatedOperation(joined)) return ParseResult.NonFinancial
        if (conflictingDirections(joined)) return ParseResult.Ambiguous("CONFLICTING_DIRECTION")
        val amounts = monetaryTokens(joined)
        if (amounts.size > 1) return ParseResult.Ambiguous("MULTIPLE_AMOUNTS")
        return if (!Regex("""S/|yape|envi[oó]|recibiste|pagaste""", RegexOption.IGNORE_CASE).containsMatchIn(joined))
            ParseResult.NonFinancial else ParseResult.UnsupportedFormat("UNSUPPORTED_FORMAT")
    }

    private fun monetaryTokens(content: String): List<BigDecimal> =
        Regex("""S/\.?\s*([0-9][0-9.,]*)""", RegexOption.IGNORE_CASE).findAll(content).mapNotNull {
            val token = it.groupValues[1].removeSuffix(".")
            if (Regex("""(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\.[0-9]{1,2})?""").matches(token)) sanitizeAmount(token) else null
        }.toList()

    private fun conflictingDirections(content: String): Boolean =
        Regex("""\b(?:yapeaste|enviaste|pagaste)\b""", RegexOption.IGNORE_CASE).containsMatchIn(content) &&
            Regex("""(?U)\b(?:te\s+(?:envi[oó]|enviaron|yape[oó]|yapearon)|recibiste)\b""", RegexOption.IGNORE_CASE).containsMatchIn(content)

    private fun negatedOperation(content: String): Boolean = Regex(
        """(?U)\b(?:no|nunca)\s+(?:te\s+)?(?:yapeaste|enviaste|pagaste|recibiste|envi[oó]|yape[oó])\b""",
        RegexOption.IGNORE_CASE).containsMatchIn(content)

    private fun parseContent(fullContent: String): ParsedTransaction? {
        // Validate complete monetary tokens before regex extraction can consume a prefix.
        val amounts = Regex("""S/\.?\s*([0-9][0-9.,]*)""", RegexOption.IGNORE_CASE).findAll(fullContent).toList()
        if (amounts.size != 1) return null
        if (Regex("""^\s+\d""").containsMatchIn(fullContent.substring(amounts.single().range.last + 1))) return null
        val token = amounts.single().groupValues[1].removeSuffix(".")
        if (!Regex("""(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\.[0-9]{1,2})?""").matches(token)) return null
        if (sanitizeAmount(token)?.let { it < BigDecimal("0.01") || it > BigDecimal("99999999.99") } != false) return null
        if (Regex("""(?U)\b(?:no|nunca)\s+(?:yapeaste|enviaste|pagaste)\b""", RegexOption.IGNORE_CASE).containsMatchIn(fullContent)) return null
        if (Regex("""\b(?:yapeaste|enviaste|pagaste)\b""", RegexOption.IGNORE_CASE).containsMatchIn(fullContent) &&
            Regex("""(?U)\b(?:te\s+(?:envi[oó]|enviaron|yape[oó]|yapearon)|recibiste)\b""", RegexOption.IGNORE_CASE).containsMatchIn(fullContent)) return null
        // 1. Try explicit sender income
        incomeWithSenderRegex.find(fullContent)?.let { match ->
            val rawContact = match.groupValues[1].trim()
            val cleanContact = rawContact.replace(Regex("""^¡?te yape(?:aron|aste|ó)?!?\s*""", RegexOption.IGNORE_CASE), "").trim()
            val contact = if (cleanContact.isBlank() || cleanContact.equals("te", ignoreCase = true)) "Yape" else cleanContact
            val amountStr = match.groupValues[2].trim()
            val amount = sanitizeAmount(amountStr) ?: return@let
            val now = LocalDateTime.now()
            val hash = generateHash(FlowType.INCOME, amount, contact, fullContent)

            return ParsedTransaction(
                amount = amount,
                flowType = FlowType.INCOME,
                contactName = contact,
                channel = "YAPE",
                transactionDate = now,
                transactionHash = hash,
                rawText = fullContent
            )
        }

        // 2. Try general income without sender
        incomeGeneralRegex.find(fullContent)?.let { match ->
            val amountStr = match.groupValues[1].trim()
            val amount = sanitizeAmount(amountStr) ?: return@let
            val contact = "Yape"
            val now = LocalDateTime.now()
            val hash = generateHash(FlowType.INCOME, amount, contact, fullContent)

            return ParsedTransaction(
                amount = amount,
                flowType = FlowType.INCOME,
                contactName = contact,
                channel = "YAPE",
                transactionDate = now,
                transactionHash = hash,
                rawText = fullContent
            )
        }

        // 3. Try fallback minimal income
        incomeFallbackRegex.find(fullContent)?.let { match ->
            val amountStr = match.groupValues[1].trim()
            val amount = sanitizeAmount(amountStr) ?: return@let
            val contact = "Yape"
            val now = LocalDateTime.now()
            val hash = generateHash(FlowType.INCOME, amount, contact, fullContent)

            return ParsedTransaction(
                amount = amount,
                flowType = FlowType.INCOME,
                contactName = contact,
                channel = "YAPE",
                transactionDate = now,
                transactionHash = hash,
                rawText = fullContent
            )
        }

        // 4. Try expense
        expenseRegex.find(fullContent)?.let { match ->
            val amountStr = match.groupValues[1].trim()
            val contact = match.groupValues[2].trim()
            val amount = sanitizeAmount(amountStr) ?: return@let
            val now = LocalDateTime.now()
            val hash = generateHash(FlowType.EXPENSE, amount, contact, fullContent)

            return ParsedTransaction(
                amount = amount,
                flowType = FlowType.EXPENSE,
                contactName = contact,
                channel = "YAPE",
                transactionDate = now,
                transactionHash = hash,
                rawText = fullContent
            )
        }

        return null
    }

    private fun sanitizeAmount(raw: String): BigDecimal? {
        return try {
            val clean = raw.replace(",", "").replace(" ", "")
            BigDecimal(clean)
        } catch (e: Exception) {
            null
        }
    }

    private fun generateHash(flowType: FlowType, amount: BigDecimal, contact: String, rawText: String): String {
        // Content identity only; the capture boundary adds Android event metadata before persistence.
        return NotificationIdentity.digest("${flowType.name}|${amount.stripTrailingZeros().toPlainString()}|${contact.lowercase(Locale.ROOT).trim()}|$rawText")
    }
}
