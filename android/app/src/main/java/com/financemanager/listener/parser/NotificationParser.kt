package com.financemanager.listener.parser

interface NotificationParser {
    val supportedPackages: Set<String>

    fun canHandle(packageName: String): Boolean {
        return supportedPackages.contains(packageName)
    }

    fun parseResult(content: com.financemanager.listener.capture.NotificationContent): com.financemanager.listener.capture.ParseResult {
        val parsed = parse(content.title, content.text, expandedNotificationText(content.bigText, content.lines))
        return parsed?.let { com.financemanager.listener.capture.ParseResult.Recognized(it) }
            ?: com.financemanager.listener.capture.ParseResult.UnsupportedFormat("UNSUPPORTED_FORMAT")
    }

    fun parse(title: String?, text: String?, bigText: String? = null): ParsedTransaction?
}
