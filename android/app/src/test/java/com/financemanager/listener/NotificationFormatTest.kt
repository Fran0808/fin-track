package com.financemanager.listener

import org.junit.Assert.*
import org.junit.Test
import com.financemanager.listener.capture.*
import com.financemanager.listener.parser.NotificationParserDispatcher
import com.financemanager.listener.parser.YapeNotificationParser

class NotificationFormatTest {
    private val parser = YapeNotificationParser()

    @Test fun repeatedTitleAmountDoesNotHideValidBody() {
        val result = parser.parseResult(NotificationContent("Confirmación de Pago S/ 12.30", "Persona Demo te envió S/ 12.30"))
        assertTrue(result is ParseResult.Recognized)
        assertEquals("12.30", (result as ParseResult.Recognized).transaction.amount.toPlainString())
        assertEquals("Persona Demo", result.transaction.contactName)
    }

    @Test fun differentAmountsAndDifferentSendersRequireReview() {
        assertTrue(parser.parseResult(NotificationContent("S/ 12.30", "Persona Demo te envió S/ 22.30")) is ParseResult.Ambiguous)
        assertTrue(parser.parseResult(NotificationContent("Persona Uno te envió S/ 12.30", "Persona Dos te envió S/ 12.30")) is ParseResult.Ambiguous)
    }

    @Test fun titleNegationOrOppositeDirectionCannotBeRescued() {
        assertNull(parser.parse("No yapeaste S/ 12.30", "Yapeaste S/ 12.30 a Persona Demo"))
        assertTrue(parser.parseResult(NotificationContent("Yapeaste S/ 12.30", "Persona Demo te envió S/ 12.30")) is ParseResult.Ambiguous)
    }

    @Test fun expandedAndLinesRemainConservative() {
        assertTrue(parser.parseResult(NotificationContent(text = "Persona Demo te envió S/ 25", bigText = "Persona Demo te envió S/ 25,50")) is ParseResult.UnsupportedFormat)
        assertTrue(parser.parseResult(NotificationContent(lines = listOf("Persona Demo te envió S/ 12.30"))) is ParseResult.Recognized)
        assertTrue(parser.parseResult(NotificationContent(lines = listOf("Persona Uno te envió S/ 12", "Persona Dos te envió S/ 14"))) is ParseResult.Ambiguous)
    }

    @Test fun unsupportedPackagesAndNonFinancialAreExplicit() {
        assertTrue(NotificationParserDispatcher().parseResult("com.android.shell", NotificationContent(text = "Persona te envió S/ 12")) is ParseResult.UnsupportedFormat)
        assertTrue(parser.parseResult(NotificationContent(text = "Descubre novedades y promociones")) is ParseResult.NonFinancial)
    }

    @Test fun encodingPreservesEmptySeparatorsAndUnicode() {
        val values = listOf("", "1:a\n2:b", "Persona Demo: áé", "")
        assertEquals(values, CaptureEncoding.decode(CaptureEncoding.encode(values)))
        assertThrows(IllegalArgumentException::class.java) { CaptureEncoding.decode("999:x") }
    }
}
