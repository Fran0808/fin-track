package com.financemanager.listener.parser

import com.financemanager.listener.capture.NotificationContent
import com.financemanager.listener.capture.ParseResult

/** This source file is compiled exclusively into the isolated captureTest build. */
internal object CaptureSourceRegistry {
    fun parsers(): List<NotificationParser> = listOf(SyntheticReceiptParser())
}

private class SyntheticReceiptParser : NotificationParser {
    override val supportedPackages = setOf("com.fintrack.capture.emitter")
    private val delegate = YapeNotificationParser()
    override fun parse(title: String?, text: String?, bigText: String?) = delegate.parse(title, text, bigText)
    override fun parseResult(content: NotificationContent): ParseResult = delegate.parseResult(content)
}
