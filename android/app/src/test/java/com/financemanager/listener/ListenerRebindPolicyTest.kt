package com.financemanager.listener

import org.junit.Assert.*
import org.junit.Test
import com.financemanager.listener.capture.RebindPolicy

class ListenerRebindPolicyTest {
    @Test fun reconnectCooldownIsSharedAcrossTriggers() {
        val policy = RebindPolicy()
        assertEquals(0L, policy.delayMillis(100))
        policy.attempted(100)
        assertEquals(59_000L, policy.delayMillis(1100))
        assertEquals(0L, policy.delayMillis(60_100))
    }
}
