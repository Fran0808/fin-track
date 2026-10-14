package com.financemanager.listener.parser

fun expandedNotificationText(bigText: String?, textLines: List<CharSequence>?): String? =
    bigText?.takeIf { it.isNotBlank() } ?: textLines?.joinToString(" ")?.takeIf { it.isNotBlank() }
