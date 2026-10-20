package com.financemanager.listener

import com.financemanager.listener.parser.NotificationParserDispatcher
import org.junit.Assert.*
import org.junit.Test

class ProductionSourcePolicyTest {
    @Test fun defaultRegistryOnlyAuthorizesTheProductionProvider() {
        val dispatcher = NotificationParserDispatcher.defaultInstance
        assertTrue(dispatcher.isSupportedPackage("com.bcp.innovacxion.yapeapp"))
        assertFalse(dispatcher.isSupportedPackage("com.fintrack.capture.emitter"))
        assertFalse(dispatcher.isSupportedPackage("com.android.shell"))
    }
}
