package com.financemanager.listener.parser

/** Production source policy. Test-only sources are not part of these builds. */
internal object CaptureSourceRegistry {
    fun parsers(): List<NotificationParser> = listOf(YapeNotificationParser())
}
