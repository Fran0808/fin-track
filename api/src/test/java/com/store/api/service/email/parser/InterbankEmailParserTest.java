package com.store.api.service.email.parser;

import com.store.api.model.dto.email.ParsedEmailTransaction;
import com.store.api.model.enums.FlowType;
import com.store.api.model.enums.OperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InterbankEmailParserTest {

    private InterbankEmailParser parser;

    @BeforeEach
    void setUp() {
        parser = new InterbankEmailParser();
    }

    @Test
    void shouldRestrictGmailSearchTermsToOfficialSenders() {
        List<String> terms = parser.gmailSearchTerms();
        assertFalse(terms.isEmpty());
        assertTrue(terms.stream().allMatch(term -> term.startsWith("from:")));
        assertTrue(terms.contains("from:interbank.pe"));
    }

    @Test
    void shouldAcceptOfficialTransactionalDomainsAndRejectPromotionalOrSpoofedSenders() {
        assertTrue(parser.supports("notificaciones@avisos.interbank.pe", "Constancia de pago"));
        assertTrue(parser.supports("constancias@tiendas.interbank.pe", "Constancia de transferencia"));
        assertTrue(parser.supports("Interbank <alertas@interbank.pe>", "Consumo con tarjeta"));

        assertFalse(parser.supports("novedades@novedades.interbank.pe", "Promociones del mes"));
        assertFalse(parser.supports("tarjetasdecredito@eecc.interbank.pe", "Estado de cuenta"));
        assertFalse(parser.supports("Interbank <fake@attacker.example>", "Constancia de pago"));
    }

    @Test
    void shouldParseCreditCardExpenseWithLast4AndOperationNumber() {
        LocalDateTime date = LocalDateTime.of(2026, 11, 24, 15, 30);
        String html = """
                <html><body>
                  <p>Hola Cliente, realizaste un consumo con tu tarjeta de crédito terminada en 4321.</p>
                  <p>Comercio: SUPERMERCADO EJEMPLO Monto: S/ 128.40 Código de operación: IBK-908172</p>
                </body></html>
                """;

        ParsedEmailTransaction tx = parser.parse("Constancia de consumo con tu tarjeta de crédito", html, date);
        assertNotNull(tx);
        assertEquals(FlowType.EXPENSE, tx.getFlowType());
        assertEquals(OperationType.UNKNOWN, tx.getOperationType());
        assertEquals("TARJETA_CREDITO_INTERBANK", tx.getChannel());
        assertEquals("4321", tx.getCardLast4());
        assertEquals(new BigDecimal("128.40"), tx.getAmount());
        assertEquals("PEN", tx.getCurrency());
        assertEquals("SUPERMERCADO EJEMPLO", tx.getMerchantName());
        assertEquals("IBK-908172", tx.getOperationNumber());
        assertEquals("IBK-908172", tx.getReceiptReference());
    }

    @Test
    void shouldParseOutgoingAndIncomingPlinOperations() {
        LocalDateTime date = LocalDateTime.of(2026, 11, 24, 18, 0);

        ParsedEmailTransaction outgoing = parser.parse(
                "Constancia de envío Plin",
                "Realizaste un envío por Plin. Destinatario: Carlos Vega Monto enviado: S/ 35.00 Nro. operación: 554433",
                date);
        assertNotNull(outgoing);
        assertEquals(FlowType.EXPENSE, outgoing.getFlowType());
        assertEquals("PLIN", outgoing.getChannel());
        assertEquals("Carlos Vega", outgoing.getMerchantName());
        assertEquals(new BigDecimal("35.00"), outgoing.getAmount());

        ParsedEmailTransaction incoming = parser.parse(
                "Constancia de recepción Plin",
                "Recibiste un Plin de Ana Torres por S/ 60.00. Código de operación: 778899",
                date);
        assertNotNull(incoming);
        assertEquals(FlowType.INCOME, incoming.getFlowType());
        assertEquals("PLIN", incoming.getChannel());
        assertEquals("Ana Torres", incoming.getMerchantName());
        assertEquals(new BigDecimal("60.00"), incoming.getAmount());
    }

    @Test
    void shouldClassifyInternalTransfersAndOwnCardPaymentsAsInternalTransfer() {
        LocalDateTime date = LocalDateTime.of(2026, 11, 24, 10, 0);

        ParsedEmailTransaction ownTransfer = parser.parse(
                "Constancia de transferencia entre mis cuentas",
                "Realizaste una transferencia entre tus cuentas por S/ 500.00 Nro. operación: 112233",
                date);
        assertNotNull(ownTransfer);
        assertEquals(FlowType.INTERNAL_TRANSFER, ownTransfer.getFlowType());
        assertEquals("INTERBANK_TRANSFERENCIA", ownTransfer.getChannel());

        ParsedEmailTransaction cardRepayment = parser.parse(
                "Constancia de pago de tarjeta de crédito propia",
                "Realizaste un pago de tu tarjeta de crédito propia terminada en 4321. Monto pagado: S/ 850.00 Nro. operación: 223344",
                date);
        assertNotNull(cardRepayment);
        assertEquals(FlowType.INTERNAL_TRANSFER, cardRepayment.getFlowType());
        assertEquals(OperationType.OWN_CARD_PAYMENT, cardRepayment.getOperationType());
        assertEquals("TARJETA_CREDITO_INTERBANK", cardRepayment.getChannel());
        assertEquals("4321", cardRepayment.getCardLast4());
    }

    @Test
    void shouldIgnoreNonFinancialAndAmbiguousEmails() {
        LocalDateTime date = LocalDateTime.of(2026, 11, 24, 12, 0);

        assertNull(parser.parse("Tu clave dinámica Interbank",
                "Tu código de verificación es 123456. Monto: S/ 100.00", date));
        assertNull(parser.parse("Estado de cuenta Interbank",
                "Tu estado de cuenta de noviembre. Monto total: S/ 950.00", date));
        assertNull(parser.parse("Constancia de pago",
                "Monto: S/ 25.00 Importe: S/ 50.00", date));
        assertNull(parser.parse("Constancia de operación",
                "Recibiste una transferencia de S/ 40.00 y realizaste un pago de S/ 40.00", date));
    }

    @Test
    void shouldProduceDistinctHashFromSameOperationInBcp() {
        LocalDateTime date = LocalDateTime.of(2026, 11, 24, 12, 0);
        BcpEmailParser bcp = new BcpEmailParser();

        ParsedEmailTransaction ibkTx = parser.parse("Constancia de transferencia",
                "Realizaste una transferencia. Destino: Comercio Demo Monto: S/ 50.00 Nro. de operación: 100200", date);
        ParsedEmailTransaction bcpTx = bcp.parse("Constancia de transferencia",
                "Realizaste una transferencia. Destino: Comercio Demo Monto: S/ 50.00 Nro. de operación: 100200", date);

        assertNotNull(ibkTx);
        assertNotNull(bcpTx);
        assertNotEquals(bcpTx.getTransactionHash(), ibkTx.getTransactionHash());
    }
}
