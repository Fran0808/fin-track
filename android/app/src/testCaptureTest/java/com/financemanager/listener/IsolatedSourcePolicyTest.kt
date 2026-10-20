package com.financemanager.listener

import com.financemanager.listener.parser.NotificationParserDispatcher
import org.junit.Assert.*
import org.junit.Test

class IsolatedSourcePolicyTest {
    @Test fun isolatedRegistryAuthorizesOnlyTheSyntheticEmitter() {
        val dispatcher = NotificationParserDispatcher.defaultInstance
        assertTrue(dispatcher.isSupportedPackage("com.fintrack.capture.emitter"))
        assertFalse(dispatcher.isSupportedPackage("com.bcp.innovacxion.yapeapp"))
        assertFalse(dispatcher.isSupportedPackage("com.android.shell"))
        assertEquals("com.financemanager.listener.capturetest", BuildConfig.APPLICATION_ID)
    }
}
