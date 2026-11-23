package com.financemanager.listener.parser

import com.financemanager.listener.capture.NotificationContent
import com.financemanager.listener.capture.ParseResult

class NotificationParserDispatcher(
    parsers: List<NotificationParser>
) {
    private val parsersByPackage = buildMap {
        for (parser in parsers) {
            for (packageName in parser.supportedPackages) {
                require(put(packageName, parser) == null) { "Duplicate notification parser package: $packageName" }
            }
        }
    }

    fun isSupportedPackage(packageName: String?): Boolean =
        packageName != null && parsersByPackage.containsKey(packageName)

    fun parse(packageName: String?, title: String?, text: String?, bigText: String? = null): ParsedTransaction? {
        if (packageName == null) return null
        return parsersByPackage[packageName]?.parse(title, text, bigText)
    }

    fun parseResult(packageName: String?, content: NotificationContent): ParseResult =
        packageName?.let { parsersByPackage[it] }?.parseResult(content)
            ?: ParseResult.UnsupportedFormat("UNSUPPORTED_PACKAGE")

    companion object {
        val defaultInstance = NotificationParserDispatcher(CaptureSourceRegistry.parsers())
    }
}
