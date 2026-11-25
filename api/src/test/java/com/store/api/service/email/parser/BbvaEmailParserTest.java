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

class BbvaEmailParserTest {

    private BbvaEmailParser parser;

    @BeforeEach
    void setUp() {
        parser = new BbvaEmailParser();
    }

    @Test
    void shouldRestrictGmailSearchTermsToOfficialSenders() {
        List<String> terms = parser.gmailSearchTerms();
        assertFalse(terms.isEmpty());
        assertTrue(terms.stream().allMatch(term -> term.startsWith("from:")));
        assertTrue(terms.contains("from:bbva.com"));
    }

    @Test
    void shouldAcceptOfficialDomainsAndRejectSpoofedSenders() {
        assertTrue(parser.supports("notificaciones@bbva.com", "Constancia de operación"));
        assertTrue(parser.supports("BBVA Perú <avisos@avisos.bbva.com>", "Consumo con tarjeta"));
        assertTrue(parser.supports("alertas@bbva.pe", "Constancia de transferencia"));

        assertFalse(parser.supports("BBVA Perú <fake@bbva-peru-seguridad.example>", "Constancia de pago"));
        assertFalse(parser.supports("soporte@gmail.com", "Constancia de pago BBVA"));
    }

    @Test
    void shouldParseCreditCardExpenseWithLast4AndOperationNumber() {
        LocalDateTime date = LocalDateTime.of(2026, 11, 24, 16, 15);
        String html = """
                <html><body>
                  <p>Hola Cliente, realizaste un consumo con tu tarjeta de crédito terminada en 7890.</p>
                  <p>Comercio: FARMACIA EJEMPLO Monto: S/ 64.90 Nro. de operación: BBVA-123456</p>
                </body></html>
                """;

        ParsedEmailTransaction tx = parser.parse("Constancia de consumo con tu tarjeta de crédito", html, date);
        assertNotNull(tx);
        assertEquals(FlowType.EXPENSE, tx.getFlowType());
        assertEquals(OperationType.UNKNOWN, tx.getOperationType());
        assertEquals("TARJETA_CREDITO_BBVA", tx.getChannel());
        assertEquals("7890", tx.getCardLast4());
        assertEquals(new BigDecimal("64.90"), tx.getAmount());
        assertEquals("PEN", tx.getCurrency());
        assertEquals("FARMACIA EJEMPLO", tx.getMerchantName());
        assertEquals("BBVA-123456", tx.getOperationNumber());
    }

    @Test
    void shouldParseOutgoingAndIncomingPlinOperations() {
        LocalDateTime date = LocalDateTime.of(2026, 11, 24, 19, 0);

        ParsedEmailTransaction outgoing = parser.parse(
                "Constancia de envío Plin",
                "Realizaste un envío por Plin. Destinatario: Luis Paredes Monto enviado: S/ 40.00 Código de operación: 887766",
                date);
        assertNotNull(outgoing);
        assertEquals(FlowType.EXPENSE, outgoing.getFlowType());
        assertEquals("PLIN", outgoing.getChannel());
        assertEquals("Luis Paredes", outgoing.getMerchantName());
        assertEquals(new BigDecimal("40.00"), outgoing.getAmount());

        ParsedEmailTransaction incoming = parser.parse(
                "Constancia de recepción Plin",
                "Recibiste un Plin de María Rojas por S/ 75.50. Código de operación: 998877",
                date);
        assertNotNull(incoming);
        assertEquals(FlowType.INCOME, incoming.getFlowType());
        assertEquals("PLIN", incoming.getChannel());
        assertEquals("María Rojas", incoming.getMerchantName());
        assertEquals(new BigDecimal("75.50"), incoming.getAmount());
    }

    @Test
    void shouldClassifyInternalTransfersAndOwnCardPaymentsAsInternalTransfer() {
        LocalDateTime date = LocalDateTime.of(2026, 11, 24, 11, 0);

        ParsedEmailTransaction ownTransfer = parser.parse(
                "Constancia de transferencia entre mis cuentas",
                "Realizaste una transferencia entre tus cuentas por S/ 300.00 Nro. operación: 445566",
                date);
        assertNotNull(ownTransfer);
        assertEquals(FlowType.INTERNAL_TRANSFER, ownTransfer.getFlowType());
        assertEquals("BBVA_TRANSFERENCIA", ownTransfer.getChannel());

        ParsedEmailTransaction cardRepayment = parser.parse(
                "Constancia de pago de tarjeta de crédito propia",
                "Realizaste un pago de tu tarjeta de crédito propia terminada en 7890. Monto pagado: S/ 620.00 Nro. operación: 556677",
                date);
        assertNotNull(cardRepayment);
        assertEquals(FlowType.INTERNAL_TRANSFER, cardRepayment.getFlowType());
        assertEquals(OperationType.OWN_CARD_PAYMENT, cardRepayment.getOperationType());
        assertEquals("TARJETA_CREDITO_BBVA", cardRepayment.getChannel());
        assertEquals("7890", cardRepayment.getCardLast4());
    }

    @Test
    void shouldIgnoreNonFinancialAndAmbiguousEmails() {
        LocalDateTime date = LocalDateTime.of(2026, 11, 24, 12, 0);

        assertNull(parser.parse("Tu Token Digital BBVA",
                "Tu código de verificación es 654321. Monto: S/ 100.00", date));
        assertNull(parser.parse("Estado de cuenta BBVA",
                "Tu estado de cuenta del mes. Monto total: S/ 1,250.00", date));
        assertNull(parser.parse("Constancia de pago",
                "Monto: S/ 25.00 Importe: S/ 50.00", date));
        assertNull(parser.parse("Constancia de operación",
                "Recibiste una transferencia de S/ 40.00 y realizaste un pago de S/ 40.00", date));
    }

    @Test
    void shouldProduceDistinctHashFromSameOperationInInterbank() {
        LocalDateTime date = LocalDateTime.of(2026, 11, 24, 12, 0);
        InterbankEmailParser ibk = new InterbankEmailParser();

        ParsedEmailTransaction bbvaTx = parser.parse("Constancia de transferencia",
                "Realizaste una transferencia. Destino: Comercio Demo Monto: S/ 50.00 Nro. de operación: 100200", date);
        ParsedEmailTransaction ibkTx = ibk.parse("Constancia de transferencia",
                "Realizaste una transferencia. Destino: Comercio Demo Monto: S/ 50.00 Nro. de operación: 100200", date);

        assertNotNull(bbvaTx);
        assertNotNull(ibkTx);
        assertNotEquals(ibkTx.getTransactionHash(), bbvaTx.getTransactionHash());
    }
}
