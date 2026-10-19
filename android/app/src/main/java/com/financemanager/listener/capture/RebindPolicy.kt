package com.financemanager.listener.capture

class RebindPolicy(private val cooldownMillis: Long = 60_000) {
    private var lastAttempt: Long? = null
    fun delayMillis(now: Long): Long = lastAttempt?.let { (cooldownMillis - (now - it)).coerceAtLeast(0) } ?: 0
    fun attempted(now: Long) { lastAttempt = now }
}
