package com.financemanager.listener

import com.financemanager.listener.parser.BbvaNotificationParser
import com.financemanager.listener.parser.FlowType
import com.financemanager.listener.parser.InterbankNotificationParser
import com.financemanager.listener.parser.NotificationParserDispatcher
import com.financemanager.listener.parser.NotificationParser
import com.financemanager.listener.parser.ParsedTransaction
import com.financemanager.listener.parser.YapeNotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class NotificationParserDispatcherTest {

    private val dispatcher = NotificationParserDispatcher(listOf(YapeNotificationParser(), InterbankNotificationParser(), BbvaNotificationParser()))

    @Test
    fun parsesSupportedBbvaPlinPackageOnly() {
        assertTrue(dispatcher.isSupportedPackage("com.bbva.nxt_peru"))
        val parsed = dispatcher.parse("com.bbva.nxt_peru", "¡Te plinearon!", "María Rojas te plineó S/ 20.00")
        assertNotNull(parsed)
        assertEquals("PLIN", parsed?.channel)
        assertNull(dispatcher.parse("com.bbva.nxt_peru.fake", "¡Te plinearon!", "María Rojas te plineó S/ 20.00"))
    }

    @Test
    fun parsesSupportedInterbankPlinPackageOnly() {
        assertTrue(dispatcher.isSupportedPackage("pe.com.interbank.mobilebanking"))
        val parsed = dispatcher.parse("pe.com.interbank.mobilebanking", "¡Te plinearon!", "Ana Torres te plineó S/ 15.00")
        assertNotNull(parsed)
        assertEquals("PLIN", parsed?.channel)
        assertNull(dispatcher.parse("pe.com.interbank.mobilebanking.fake", "¡Te plinearon!", "Ana Torres te plineó S/ 15.00"))
    }

    @Test
    fun additionalParserRoutesOnlyItsRegisteredPackage() {
        val additional = object : NotificationParser {
            override val supportedPackages = setOf("test.example.wallet")
            override fun parse(title: String?, text: String?, bigText: String?) = ParsedTransaction(
                BigDecimal("12.34"), FlowType.INCOME, "Synthetic contact", "TEST_WALLET",
                transactionHash = "synthetic-receipt", rawText = text.orEmpty()
            )
        }
        val multiSource = NotificationParserDispatcher(listOf(YapeNotificationParser(), additional))
        assertEquals("TEST_WALLET", multiSource.parse("test.example.wallet", null, "receipt")?.channel)
        assertNull(multiSource.parse("test.example.wallet.clone", null, "receipt"))
        assertTrue(multiSource.isSupportedPackage("com.bcp.innovacxion.yapeapp"))
    }

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
