package com.store.api.service.email;

import com.store.api.model.dto.email.ParsedEmailTransaction;
import com.store.api.model.enums.FlowType;
import com.store.api.service.email.parser.BcpEmailParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BcpEmailParserTest {

    private BcpEmailParser parser;

    @BeforeEach
    void setUp() {
        parser = new BcpEmailParser();
    }

    @Test
    void shouldParseCreditCardExpenseSuccessfully() {
        String subject = "Aviso de operación: Consumo con Tarjeta de Crédito";
        String htmlBody = """
                <html>
                <body>
                    <p>Estimado cliente,</p>
                    <p>Le informamos que se ha realizado un consumo con su tarjeta terminada en 9999.</p>
                    <table>
                        <tr><td>Importe:</td><td>S/ 25.50</td></tr>
                        <tr><td>Establecimiento:</td><td>TIENDA EJEMPLO</td></tr>
                        <tr><td>Nro. de operación:</td><td>98765432</td></tr>
                    </table>
                </body>
                </html>
                """;

        ParsedEmailTransaction tx = parser.parse(subject, htmlBody, LocalDateTime.now());

        assertNotNull(tx);
        assertEquals(new BigDecimal("25.50"), tx.getAmount());
        assertEquals("PEN", tx.getCurrency());
        assertEquals(FlowType.EXPENSE, tx.getFlowType());
        assertEquals("TIENDA EJEMPLO", tx.getMerchantName());
        assertEquals("TARJETA_CREDITO_BCP", tx.getChannel());
        assertEquals("9999", tx.getCardLast4());
        assertEquals("98765432", tx.getOperationNumber());
        assertNotNull(tx.getTransactionHash());
    }

    @Test
    void shouldParseDebitCardUsdExpenseSuccessfully() {
        String subject = "Aviso de operación: Consumo con Tarjeta de Débito";
        String htmlBody = """
                <div>
                    <h3>Detalle de operación</h3>
                    <p>Tarjeta débito **** 8888</p>
                    <p>Monto: US$ 14.99</p>
                    <p>Comercio: STREAMING SERVICE</p>
                    <p>Número de operación: 12345678</p>
                </div>
                """;

        ParsedEmailTransaction tx = parser.parse(subject, htmlBody, LocalDateTime.now());

        assertNotNull(tx);
        assertEquals(new BigDecimal("14.99"), tx.getAmount());
        assertEquals("USD", tx.getCurrency());
        assertEquals(FlowType.EXPENSE, tx.getFlowType());
        assertEquals("STREAMING SERVICE", tx.getMerchantName());
        assertEquals("TARJETA_DEBITO_BCP", tx.getChannel());
        assertEquals("8888", tx.getCardLast4());
        assertEquals("12345678", tx.getOperationNumber());
    }

    @Test
    void shouldParseSpecialCharacterMerchantPurchaseSuccessfully() {
        String subject = "Aviso de operación: Consumo con Tarjeta de Débito BCP";
        String body = """
                Estimado cliente, Realizaste un consumo de S/ 19.90 con tu Tarjeta de Débito BCP en ONLINE*MARKET.COM LTD.
                Por tu seguridad, te enviamos los datos de tu operación.
                Monto Total del consumo S/ 19.90
                Datos de la operación
                Operación realizada Consumo Tarjeta de Débito
                Fecha y hora 05 de setiembre de 2026 - 10:00 AM
                Número de Tarjeta de Débito ************1234
                Empresa ONLINE*MARKET.COM LTD
                Número de operación 123456
                """;

        ParsedEmailTransaction tx = parser.parse(subject, body, LocalDateTime.of(2026, 9, 5, 10, 0));

        assertNotNull(tx);
        assertEquals(new BigDecimal("19.90"), tx.getAmount());
        assertEquals("PEN", tx.getCurrency());
        assertEquals(FlowType.EXPENSE, tx.getFlowType());
        assertEquals("ONLINE*MARKET.COM LTD", tx.getMerchantName());
        assertEquals("TARJETA_DEBITO_BCP", tx.getChannel());
        assertEquals("1234", tx.getCardLast4());
        assertEquals("123456", tx.getOperationNumber());
    }

    @Test
    void shouldParseCreditCardOwnPaymentExpenseSuccessfully() {
        String subject = "Constancia de Pago de Tarjeta de Crédito Propia - Servicio de Notificaciones BCP";
        String body = """
                Hola Francisco Javier,
                Realizaste un pago a tu tarjeta de S/ 1068.87 desde tu Cuenta yape.
                A continuación, te enviamos los datos de tu operación.
                Montos
                Monto pagado S/ 1068.87
                Datos de la operación
                Operación realizada Pago de tarjeta propia BCP
                Fecha y hora 26 de Septiembre de 2026 - 08:19 AM
                Pagado a VISA Light **** 3127
                """;

        ParsedEmailTransaction tx = parser.parse(subject, body, LocalDateTime.of(2026, 9, 26, 8, 19));

        assertNotNull(tx);
        assertEquals(new BigDecimal("1068.87"), tx.getAmount());
        assertEquals("PEN", tx.getCurrency());
        assertEquals(FlowType.EXPENSE, tx.getFlowType());
        assertEquals("Pago Tarjeta Crédito BCP", tx.getMerchantName());
        assertEquals("3127", tx.getCardLast4());
    }
}
