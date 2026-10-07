package com.financemanager.listener

import com.financemanager.listener.parser.FlowType
import com.financemanager.listener.parser.NotificationParserDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class NotificationParserDispatcherTest {

    private val dispatcher = NotificationParserDispatcher.defaultInstance

    @Test
    fun isSupportedPackage_withYapePackage_returnsTrue() {
        assertTrue(dispatcher.isSupportedPackage("com.bcp.innovacxion.yapeapp"))
        assertFalse(dispatcher.isSupportedPackage("com.android.shell"))
    }

    @Test
    fun isSupportedPackage_withUnsupportedPackage_returnsFalse() {
        assertFalse(dispatcher.isSupportedPackage("com.whatsapp"))
        assertFalse(dispatcher.isSupportedPackage("com.facebook.katana"))
        assertFalse(dispatcher.isSupportedPackage(null))
    }

    @Test
    fun parse_withYapePackage_routesToYapeParserCorrectly() {
        val packageName = "com.bcp.innovacxion.yapeapp"
        val title = "¡Te yapearon!"
        val text = "Mario Bros te envió S/ 99.90 a tu Yape"

        val result = dispatcher.parse(packageName, title, text)

        assertNotNull(result)
        assertEquals(BigDecimal("99.90"), result?.amount)
        assertEquals("Mario Bros", result?.contactName)
        assertEquals(FlowType.INCOME, result?.flowType)
        assertEquals("YAPE", result?.channel)
    }

    @Test
    fun parse_withUnsupportedPackage_returnsNull() {
        val packageName = "com.spotify.music"
        val title = "Now playing"
        val text = "Song name"

        val result = dispatcher.parse(packageName, title, text)

        assertNull(result)
    }
}
