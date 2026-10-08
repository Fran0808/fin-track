package com.store.api.service.transaction;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.transaction.TransactionSyncRequest;
import com.store.api.model.entity.*;
import com.store.api.model.enums.*;
import com.store.api.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InstrumentAssignmentServiceTest {
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final FinancialInstrumentRepository instruments = mock(FinancialInstrumentRepository.class);
    private final InstrumentAssignmentService service = new InstrumentAssignmentService(transactions, instruments);
    private final User user = User.builder().id(7L).email("owner@example.test").build();
    private Transaction transaction;
    private FinancialInstrument card;

    @BeforeEach void setup() {
        UserContext.setCurrentUser(user);
        transaction = Transaction.builder().id(1L).user(user).amount(new BigDecimal("25.50")).flowType(FlowType.EXPENSE)
                .contactName("Store").transactionHash("original").channel("TARJETA_DEBITO_BCP").cardLast4("1234")
                .transactionDate(LocalDateTime.of(2026, 10, 1, 12, 0)).build();
        card = FinancialInstrument.builder().id(2L).user(user).type(InstrumentType.DEBIT_CARD).bank(Bank.BCP).alias("Debit").lastFour("1234").build();
        when(transactions.findByIdAndUser(1L, user)).thenReturn(Optional.of(transaction));
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(instruments.findByIdAndUser(2L, user)).thenReturn(Optional.of(card));
        when(instruments.findByUserOrderByAliasAscIdAsc(user)).thenReturn(List.of(card));
    }
    @AfterEach void clear() { UserContext.clear(); }

    @Test void assignsChangesAndUnlinksWithoutChangingFinancialEvidence() {
        assertEquals(2L, service.assign(1L, 2L).getFinancialInstrument().getId());
        FinancialInstrument second = FinancialInstrument.builder().id(3L).bank(Bank.BBVA).alias("Credit").type(InstrumentType.CREDIT_CARD).build();
        when(instruments.findByIdAndUser(3L, user)).thenReturn(Optional.of(second));
        assertEquals(3L, service.assign(1L, 3L).getFinancialInstrument().getId());
        assertNull(service.assign(1L, null).getFinancialInstrument());
        assertEquals(new BigDecimal("25.50"), transaction.getAmount());
        assertEquals(FlowType.EXPENSE, transaction.getFlowType());
        assertEquals("original", transaction.getTransactionHash());
        assertEquals("TARJETA_DEBITO_BCP", transaction.getChannel());
        assertEquals("1234", transaction.getCardLast4());
        assertEquals(LocalDateTime.of(2026, 10, 1, 12, 0), transaction.getTransactionDate());
    }

    @Test void rejectsForeignProductsAndTransactionsAndArchivedAssignments() {
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.assign(99L, 2L)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.suggestions(99L)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.assign(1L, 99L)).getStatusCode().value());
        card.setActive(false);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.assign(1L, 2L)).getStatusCode().value());
        transaction.setFinancialInstrument(card);
        assertNull(service.assign(1L, null).getFinancialInstrument());
    }

    @Test void anonymousCannotAssignOrGetSuggestions() {
        UserContext.clear();
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.assign(1L, 2L)).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.suggestions(1L)).getStatusCode().value());
        verify(transactions, never()).save(any());
    }

    @Test void presentsAllMatchingCandidatesWithoutAssigning() {
        FinancialInstrument second = FinancialInstrument.builder().id(3L).type(InstrumentType.DEBIT_CARD).bank(Bank.BCP).alias("Second").lastFour("1234").build();
        when(instruments.findByUserOrderByAliasAscIdAsc(user)).thenReturn(List.of(card, second));
        assertEquals(2, service.suggestions(1L).size());
        assertNull(transaction.getFinancialInstrument());
        verify(transactions, never()).save(any());
        card.setActive(false);
        assertEquals(1, service.suggestions(1L).size());
        second.setBank(Bank.BBVA);
        assertTrue(service.suggestions(1L).isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"YAPE", "PLIN", "BCP", "UNKNOWN", "TARJETA_DEBITO_BCP_EXTRA", "BCP_TRANSFERENCIA"})
    void insufficientOrWrongProductEvidenceProducesNoCardSuggestion(String channel) {
        transaction.setChannel(channel);
        assertTrue(service.suggestions(1L).isEmpty());
    }

    @Test void suggestionsRequireExactSuffixAndProductType() {
        transaction.setCardLast4(null); assertTrue(service.suggestions(1L).isEmpty());
        transaction.setCardLast4("123"); assertTrue(service.suggestions(1L).isEmpty());
        transaction.setCardLast4("1234"); card.setType(InstrumentType.CREDIT_CARD);
        assertTrue(service.suggestions(1L).isEmpty());
        transaction.setChannel("TARJETA_CREDITO_BCP"); assertEquals(1, service.suggestions(1L).size());
        transaction.setChannel("BCP_TRANSFERENCIA"); card.setType(InstrumentType.BANK_ACCOUNT);
        assertEquals(1, service.suggestions(1L).size());
    }

    @Test void duplicateIngestionPreservesConfirmedAssignmentIncludingArchivedProduct() {
        transaction.setFinancialInstrument(card); card.setActive(false);
        when(transactions.existsByTransactionHashAndUser("original", user)).thenReturn(true);
        when(transactions.findByTransactionHashAndUser("original", user)).thenReturn(Optional.of(transaction));
        TransactionSyncRequest request = new TransactionSyncRequest();
        request.setTransactionHash("original"); request.setChannel("YAPE");
        var result = new TransactionService(transactions, mock(RawNotificationRepository.class), mock(CategoryRepository.class)).processAndSave(request);
        assertEquals(2L, result.getFinancialInstrument().getId());
        assertFalse(result.getFinancialInstrument().isActive());
        assertEquals(new BigDecimal("25.50"), result.getAmount());
        verify(transactions, never()).save(any());
    }
}
