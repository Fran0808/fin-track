package com.financemanager.listener

import com.financemanager.listener.parser.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class NotificationTextTest {
    @Test fun blankExpandedTextDoesNotHidePaymentInTextLines() {
        val expanded = expandedNotificationText(" ", listOf("Persona Pr* te envió un pago por S/ 2.", "El cód. de seguridad es: 123"))
        val result = requireNotNull(NotificationParserDispatcher().parse("com.bcp.innovacxion.yapeapp", "Confirmación de Pago", null, expanded))
        assertEquals(BigDecimal("2"), result.amount)
        assertEquals("Persona Pr*", result.contactName)
        assertEquals(FlowType.INCOME, result.flowType)
    }

    @Test fun invalidExpandedAmountCannotBeReplacedWithDifferentLineAmount() {
        val expanded = expandedNotificationText("Persona Pr* te envió S/ 25,50", listOf("Persona Pr* te envió S/ 25"))
        assertNull(NotificationParserDispatcher().parse("com.bcp.innovacxion.yapeapp", "Confirmación de Pago", null, expanded))
    }
}
