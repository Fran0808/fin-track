package com.financemanager.listener

import com.financemanager.listener.capture.NotificationContent
import com.financemanager.listener.capture.ParseResult
import com.financemanager.listener.parser.FlowType
import com.financemanager.listener.parser.InterbankNotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class InterbankNotificationParserTest {

    private val parser = InterbankNotificationParser()

    @Test
    fun parsesIncomingPlinWithSenderAsPlinIncome() {
        val result = parser.parse(
            title = "¡Te plinearon!",
            text = "Ana Torres te plineó S/ 45.50"
        )
        assertNotNull(result)
        assertEquals(FlowType.INCOME, result?.flowType)
        assertEquals("PLIN", result?.channel)
        assertEquals("Ana Torres", result?.contactName)
        assertEquals(BigDecimal("45.50"), result?.amount)
    }

    @Test
    fun parsesGeneralIncomingPlinWithoutSender() {
        val result = parser.parse(
            title = "Recibiste un Plin",
            text = "Recibiste un Plin por S/ 120.00 en tu cuenta"
        )
        assertNotNull(result)
        assertEquals(FlowType.INCOME, result?.flowType)
        assertEquals("PLIN", result?.channel)
        assertEquals("Plin Interbank", result?.contactName)
        assertEquals(BigDecimal("120.00"), result?.amount)
    }

    @Test
    fun ignoresCardConsumptionsAndOutgoingPaymentsToAvoidEmailDuplicates() {
        assertNull(parser.parse("Consumo con tu tarjeta", "Realizaste un consumo de S/ 89.00 en TIENDA"))
        assertNull(parser.parse("Plin enviado", "Plineaste S/ 25.00 a Carlos Vega"))
        val cardResult = parser.parseResult(
            NotificationContent("Consumo con tu tarjeta", "Consumo S/ 89.00 en TIENDA", null, emptyList())
        )
        assertTrue(cardResult is ParseResult.NonFinancial)
    }

    @Test
    fun rejectsNegatedAndMultipleAmountPlinNotifications() {
        val negated = parser.parseResult(
            NotificationContent("Aviso Plin", "No te plineó S/ 30.00", null, emptyList())
        )
        assertTrue(negated is ParseResult.NonFinancial)

        val multiple = parser.parseResult(
            NotificationContent("Plin", "Recibiste un Plin de S/ 30.00 y otro de S/ 40.00", null, emptyList())
        )
        assertTrue(multiple is ParseResult.Ambiguous)
    }
}
