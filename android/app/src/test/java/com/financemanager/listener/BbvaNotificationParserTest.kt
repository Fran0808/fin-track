package com.financemanager.listener

import com.financemanager.listener.capture.NotificationContent
import com.financemanager.listener.capture.ParseResult
import com.financemanager.listener.parser.BbvaNotificationParser
import com.financemanager.listener.parser.FlowType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BbvaNotificationParserTest {

    private val parser = BbvaNotificationParser()

    @Test
    fun parsesIncomingPlinWithSenderAsPlinIncome() {
        val result = parser.parse(
            title = "¡Te plinearon!",
            text = "María Rojas te plineó S/ 75.50"
        )
        assertNotNull(result)
        assertEquals(FlowType.INCOME, result?.flowType)
        assertEquals("PLIN", result?.channel)
        assertEquals("María Rojas", result?.contactName)
        assertEquals(BigDecimal("75.50"), result?.amount)
    }

    @Test
    fun parsesGeneralIncomingPlinWithoutSender() {
        val result = parser.parse(
            title = "Recibiste un Plin",
            text = "Recibiste un Plin por S/ 90.00 en tu cuenta BBVA"
        )
        assertNotNull(result)
        assertEquals(FlowType.INCOME, result?.flowType)
        assertEquals("PLIN", result?.channel)
        assertEquals("Plin BBVA", result?.contactName)
        assertEquals(BigDecimal("90.00"), result?.amount)
    }

    @Test
    fun ignoresCardConsumptionsAndOutgoingPaymentsToAvoidEmailDuplicates() {
        assertNull(parser.parse("Compra con tarjeta", "Realizaste una compra de S/ 64.90 en FARMACIA"))
        assertNull(parser.parse("Plin enviado", "Plineaste S/ 40.00 a Luis Paredes"))
        val cardResult = parser.parseResult(
            NotificationContent("Compra con tarjeta", "Consumo S/ 64.90 en FARMACIA", null, emptyList())
        )
        assertTrue(cardResult is ParseResult.NonFinancial)
    }

    @Test
    fun rejectsNegatedAndMultipleAmountPlinNotifications() {
        val negated = parser.parseResult(
            NotificationContent("Aviso Plin", "Nunca te plineó S/ 30.00", null, emptyList())
        )
        assertTrue(negated is ParseResult.NonFinancial)

        val multiple = parser.parseResult(
            NotificationContent("Plin", "Recibiste un Plin de S/ 30.00 y otro de S/ 40.00", null, emptyList())
        )
        assertTrue(multiple is ParseResult.Ambiguous)
    }
}
