package com.financemanager.listener.capture

import com.financemanager.listener.parser.ParsedTransaction

data class NotificationContent(
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val lines: List<String> = emptyList()
)

sealed interface ParseResult {
    data class Recognized(val transaction: ParsedTransaction) : ParseResult
    data object NonFinancial : ParseResult
    data class Ambiguous(val reason: String) : ParseResult
    data class UnsupportedFormat(val reason: String) : ParseResult
}

/** Length prefixes avoid delimiter collisions without interpreting notification contents. */
object CaptureEncoding {
    fun encode(values: List<String>): String = values.joinToString("") { "${it.length}:$it" }
    fun decode(value: String): List<String> = buildList {
        var offset = 0
        while (offset < value.length) {
            val colon = value.indexOf(':', offset)
            require(colon >= offset)
            val size = value.substring(offset, colon).toInt()
            require(size >= 0 && size <= value.length - colon - 1)
            offset = colon + 1
            add(value.substring(offset, offset + size))
            offset += size
        }
    }
}
