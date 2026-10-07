package com.financemanager.listener.data

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

class LegacyReviewRequired : IllegalStateException("Legacy notification requires review")

object LegacyReconciliation {
    fun matches(capturedDate: String, postedDate: String): Boolean = runCatching {
        abs(Duration.between(LocalDateTime.parse(capturedDate), LocalDateTime.parse(postedDate)).seconds) <= 120
    }.getOrDefault(false)
}
