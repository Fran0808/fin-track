package com.financemanager.listener.parser

class NotificationParserDispatcher(
    private val parsers: List<NotificationParser> = listOf(YapeNotificationParser())
) {

    fun isSupportedPackage(packageName: String?): Boolean {
        if (packageName == null) return false
        return parsers.any { it.canHandle(packageName) }
    }

    fun parse(packageName: String?, title: String?, text: String?, bigText: String? = null): ParsedTransaction? {
        if (packageName == null) return null
        val matchedParser = parsers.firstOrNull { it.canHandle(packageName) } ?: return null
        return matchedParser.parse(title, text, bigText)
    }

    fun parseResult(packageName: String?, content: com.financemanager.listener.capture.NotificationContent): com.financemanager.listener.capture.ParseResult {
        val parser = parsers.firstOrNull { packageName != null && it.canHandle(packageName) }
            ?: return com.financemanager.listener.capture.ParseResult.UnsupportedFormat("UNSUPPORTED_PACKAGE")
        return parser.parseResult(content)
    }

    companion object {
        val defaultInstance = NotificationParserDispatcher(CaptureSourceRegistry.parsers())
    }
}
