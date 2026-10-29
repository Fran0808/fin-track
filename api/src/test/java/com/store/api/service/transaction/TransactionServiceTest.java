package com.store.api.service.transaction;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.transaction.TransactionSyncRequest;
import com.store.api.model.entity.Transaction;
import com.store.api.model.entity.User;
import com.store.api.model.enums.FlowType;
import com.store.api.repository.RawNotificationRepository;
import com.store.api.repository.TransactionRepository;
import com.store.api.repository.CategoryRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransactionServiceTest {
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final RawNotificationRepository rawNotifications = mock(RawNotificationRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final com.store.api.service.category.ClassificationRuleService rules = mock(com.store.api.service.category.ClassificationRuleService.class);
    private final TransactionService service = new TransactionService(transactions, rawNotifications, categories, rules, mock(com.store.api.repository.UserRepository.class));

    @AfterEach
    public void clearContext() {
        UserContext.clear();
    }

    @Test void recordsPhoneAndEmailAtTrustedEntryPointsEvenWithoutRawText() {
        UserContext.setCurrentUser(User.builder().id(1L).build());
        when(transactions.save(any())).thenAnswer(call->call.getArgument(0));
        var req = request(); req.setRawNotificationText(null);
        service.processAndSave(req);
        service.processEmailAndSave(req," OP-123 ");
        var saved = org.mockito.ArgumentCaptor.forClass(Transaction.class);
        verify(transactions,times(2)).save(saved.capture());
        assertEquals(com.store.api.model.enums.CaptureOrigin.PHONE,saved.getAllValues().get(0).getCaptureOrigin());
        assertNull(saved.getAllValues().get(0).getOperationReference());
        assertEquals(com.store.api.model.enums.CaptureOrigin.EMAIL,saved.getAllValues().get(1).getCaptureOrigin());
        assertEquals("OP-123",saved.getAllValues().get(1).getOperationReference());
        assertEquals(req.getTransactionHash(),saved.getAllValues().get(1).getTransactionHash());
        verifyNoInteractions(rawNotifications);
    }

    @Test void invalidOptionalReferencesDoNotPreventSavingRecognizedMoneyMovements() {
        UserContext.setCurrentUser(User.builder().id(1L).build());
        when(transactions.save(any())).thenAnswer(call->call.getArgument(0));
        for (String reference : List.of(" ","<script>","x".repeat(101),"security code: 123")) {
            service.processEmailAndSave(request(),reference);
        }
        var saved = org.mockito.ArgumentCaptor.forClass(Transaction.class);
        verify(transactions,times(4)).save(saved.capture());
        assertTrue(saved.getAllValues().stream().allMatch(t->t.getCaptureOrigin()==com.store.api.model.enums.CaptureOrigin.EMAIL
                && t.getOperationReference()==null));
    }

    @Test void aRetryNeverRewritesFirstCaptureEvidenceOrInventsEvidenceForLegacyRecords() {
        var owner = User.builder().id(1L).build(); UserContext.setCurrentUser(owner);
        var existing = Transaction.builder().id(7L).user(owner).captureOrigin(com.store.api.model.enums.CaptureOrigin.EMAIL)
                .operationReference("FIRST-123").build();
        when(transactions.existsByTransactionHashAndUser("shared-hash",owner)).thenReturn(true);
        when(transactions.findByTransactionHashAndUser("shared-hash",owner)).thenReturn(Optional.of(existing));
        service.processAndSave(request());service.processEmailAndSave(request(),"NEW-456");
        assertEquals(com.store.api.model.enums.CaptureOrigin.EMAIL,existing.getCaptureOrigin());
        assertEquals("FIRST-123",existing.getOperationReference());
        existing.setCaptureOrigin(com.store.api.model.enums.CaptureOrigin.UNKNOWN);existing.setOperationReference(null);
        service.processEmailAndSave(request(),"NEW-456");
        assertEquals(com.store.api.model.enums.CaptureOrigin.UNKNOWN,existing.getCaptureOrigin());assertNull(existing.getOperationReference());
        verify(transactions,never()).save(any());verifyNoInteractions(rawNotifications);
    }

    @Test
    void anonymousOperationsCannotReadOrWriteAnything() {
        UserContext.clear();
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.processAndSave(request())).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.processBatch(List.of())).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.markOwnCardPayment(1L)).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.getTransactions(null, null, null, null, PageRequest.of(0, 10)))
                .getStatusCode().value());
        verifyNoInteractions(transactions, rawNotifications);
    }

    @Test
    void cardPaymentConfirmationRequiresOwnershipAndRejectsIncomeIndependentlyOfProvider() {
        User user = User.builder().id(10L).build();
        UserContext.setCurrentUser(user);
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.markOwnCardPayment(99L)).getStatusCode().value());
        Transaction tx = Transaction.builder().contactName("Unspecified operation")
                .flowType(FlowType.INCOME).channel("EXAMPLE_WALLET").build();
        when(transactions.findLockedByIdAndUser(1L, user)).thenReturn(Optional.of(tx));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.markOwnCardPayment(1L)).getStatusCode().value());
        verify(transactions, never()).save(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = FlowType.class, names = {"EXPENSE", "INCOME"})
    void ingestionRejectsOwnCardPaymentsWithNonNeutralFlow(FlowType flow) {
        UserContext.setCurrentUser(User.builder().id(10L).build());
        var invalid = request();
        invalid.setFlowType(flow);
        invalid.setOperationType(com.store.api.model.enums.OperationType.OWN_CARD_PAYMENT);
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.processAndSave(invalid)).getStatusCode().value());
        verifyNoInteractions(transactions, rawNotifications, rules);
    }

    @Test
    void duplicateHashFromAnotherUserDoesNotReturnOrOverwriteTheirTransaction() {
        User first = User.builder().id(10L).email("first@example.test").build();
        User second = User.builder().id(20L).email("second@example.test").build();
        Transaction existing = Transaction.builder().id(100L).user(first).build();
        when(transactions.existsByTransactionHashAndUser("shared-hash", first)).thenReturn(true);
        when(transactions.findByTransactionHashAndUser("shared-hash", first)).thenReturn(Optional.of(existing));
        when(transactions.save(any())).thenAnswer(invocation -> {
            Transaction saved = invocation.getArgument(0);
            assertSame(second, saved.getUser());
            saved.setId(200L);
            return saved;
        });

        UserContext.setCurrentUser(first);
        assertEquals(100L, service.processAndSave(request()).getId());
        UserContext.setCurrentUser(second);
        assertEquals(200L, service.processAndSave(request()).getId());
        verify(transactions).existsByTransactionHashAndUser("shared-hash", second);
        verify(transactions, never()).findByTransactionHashAndUser("shared-hash", second);
    }

    @Test
    void rawEvidenceBelongsToTheAuthenticatedUserAndSavedMovement() {
        var captor = org.mockito.ArgumentCaptor.forClass(com.store.api.model.entity.RawNotificationLog.class);
        when(transactions.save(any())).thenAnswer(call -> {
            Transaction transaction = call.getArgument(0);
            transaction.setId(transaction.getUser().getId() * 10);
            return transaction;
        });
        for (long userId : List.of(10L, 20L)) {
            UserContext.setCurrentUser(User.builder().id(userId).build());
            service.processAndSave(request());
        }
        verify(rawNotifications, times(2)).save(captor.capture());
        for (var raw : captor.getAllValues()) {
            assertSame(raw.getUser(), raw.getTransaction().getUser());
            assertEquals(raw.getUser().getId() * 10, raw.getTransaction().getId());
            assertEquals("Test notification", raw.getRawText());
        }
        assertNotEquals(captor.getAllValues().get(0).getUser().getId(),
                captor.getAllValues().get(1).getUser().getId());
    }

    @Test
    void retryDoesNotAppendEvidenceAndFailedMovementDoesNotSaveEvidence() {
        User owner = User.builder().id(10L).build();
        UserContext.setCurrentUser(owner);
        when(transactions.existsByTransactionHashAndUser("shared-hash", owner)).thenReturn(true, false);
        when(transactions.findByTransactionHashAndUser("shared-hash", owner))
                .thenReturn(Optional.of(Transaction.builder().id(100L).user(owner).build()));
        service.processAndSave(request());
        when(transactions.save(any())).thenThrow(new IllegalStateException("Synthetic persistence failure"));
        assertThrows(IllegalStateException.class, () -> service.processAndSave(request()));
        verifyNoInteractions(rawNotifications);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listingAlwaysIncludesTheCurrentUserPredicate() {
        Root<Transaction> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path<Object> userPath = mock(Path.class);
        Predicate ownerPredicate = mock(Predicate.class);
        Path<Object> reconciliationPath = mock(Path.class);
        Predicate active = mock(Predicate.class);
        when(root.get("user")).thenReturn(userPath);
        when(root.get("reconciledToId")).thenReturn(reconciliationPath);
        when(builder.isNull(reconciliationPath)).thenReturn(active);
        when(builder.equal(eq(userPath), any(User.class))).thenReturn(ownerPredicate);
        when(transactions.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(invocation -> {
            Specification<Transaction> specification = invocation.getArgument(0);
            specification.toPredicate(root, query, builder);
            verify(builder).equal(userPath, UserContext.requireCurrentUser());
            return Page.empty();
        });

        for (long id : List.of(10L, 20L)) {
            UserContext.setCurrentUser(User.builder().id(id).build());
            service.getTransactions(null, null, null, null, PageRequest.of(0, 10));
        }
        verify(builder, times(2)).and(new Predicate[] {ownerPredicate, active});
    }

    @Test
    void updateClassificationUpdatesCategoryTagsAndNotesForCurrentUser() {
        User user = User.builder().id(5L).email("user@example.test").build();
        UserContext.setCurrentUser(user);

        Transaction existing = Transaction.builder()
                .id(50L)
                .user(user)
                .amount(new BigDecimal("45.00"))
                .flowType(FlowType.EXPENSE)
                .contactName("Pumacahua")
                .channel("YAPE")
                .transactionDate(LocalDateTime.of(2026, 9, 15, 14, 0))
                .transactionHash("hash-50")
                .build();

        when(transactions.findLockedByIdAndUser(50L, user)).thenReturn(Optional.of(existing));
        when(transactions.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.store.api.model.dto.transaction.TransactionClassificationRequest request = com.store.api.model.dto.transaction.TransactionClassificationRequest.builder()
                .category("Comida y bebidas")
                .build();

        when(categories.existsByUserAndActiveTrueAndNameIgnoreCase(user, "Comida y bebidas")).thenReturn(true);
        var response = service.updateClassification(50L, request);

        assertEquals("Comida y bebidas", response.getCategory());
        verify(transactions).save(existing);
    }

    @Test
    void updateClassificationRejectsUnknownOrUnauthorizedTransaction() {
        User user = User.builder().id(5L).email("user@example.test").build();
        UserContext.setCurrentUser(user);
        when(transactions.findLockedByIdAndUser(99L, user)).thenReturn(Optional.empty());

        com.store.api.model.dto.transaction.TransactionClassificationRequest request = new com.store.api.model.dto.transaction.TransactionClassificationRequest();
        request.setCategory("OTROS");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateClassification(99L, request));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void exportTransactionsCsvGeneratesValidBomAndHeaders() {
        User user = User.builder().id(5L).email("user@example.test").build();
        UserContext.setCurrentUser(user);

        Transaction t1 = Transaction.builder()
                .id(1L)
                .user(user)
                .amount(new BigDecimal("15.50"))
                .flowType(FlowType.EXPENSE)
                .contactName("Metro Express")
                .category("ALIMENTACION")
                .channel("TARJETA_DEBITO_BCP")
                .cardLast4("1234")
                .transactionDate(LocalDateTime.of(2026, 9, 20, 10, 30))
                .transactionHash("hash-export-1")
                .build();

        when(transactions.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(t1)));

        byte[] csvBytes = service.exportTransactionsCsv(null, null, null, null, null, null, null, null, null);
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("\uFEFF"), "CSV must start with UTF-8 BOM");
        assertTrue(csv.contains("ID,Fecha,Comercio o contacto,Importe,Tipo,Categoria,Medio,Tarjeta,Hash"));
        assertTrue(csv.contains("\"Metro Express\""));
        assertTrue(csv.contains("\"ALIMENTACION\""));
        assertEquals(9, csv.lines().findFirst().orElseThrow().split(",").length);
    }

    @Test
    void appliesRulesBeforeSavingAndDoesNotReapplyOnDuplicateSync() {
        User owner = User.builder().id(7L).build();
        UserContext.setCurrentUser(owner);
        doAnswer(call -> { ((Transaction) call.getArgument(0)).setCategory("Regla"); return null; })
                .when(rules).classifyNewTransaction(any());
        when(transactions.save(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals("Regla", service.processAndSave(request()).getCategory());
        verify(transactions).save(argThat(tx -> "Regla".equals(tx.getCategory())));
        when(transactions.existsByTransactionHashAndUser("shared-hash", owner)).thenReturn(true);
        when(transactions.findByTransactionHashAndUser("shared-hash", owner)).thenReturn(Optional.of(
                Transaction.builder().user(owner).category("Corregida manualmente").build()));
        assertEquals("Corregida manualmente", service.processAndSave(request()).getCategory());
        verify(rules, times(1)).classifyNewTransaction(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(com.store.api.model.enums.PendingReason.class)
    void pendingQueriesAlwaysRequireOwnerAndUseTheSelectedReason(com.store.api.model.enums.PendingReason reason) {
        User owner = User.builder().id(7L).build();
        UserContext.setCurrentUser(owner);
        Root<Transaction> root = mock(Root.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> userPath = mock(Path.class), flowPath = mock(Path.class), instrumentPath = mock(Path.class);
        Path<String> categoryPath = mock(Path.class);
        jakarta.persistence.criteria.Expression<String> normalized = mock(jakarta.persistence.criteria.Expression.class);
        Predicate ownerPredicate = mock(Predicate.class), nonTransfer = mock(Predicate.class), blank = mock(Predicate.class);
        Predicate category = mock(Predicate.class), instrument = mock(Predicate.class), either = mock(Predicate.class), scoped = mock(Predicate.class);
        when(root.get("user")).thenReturn(userPath);
        when(root.get("flowType")).thenReturn(flowPath);
        when(root.get("financialInstrument")).thenReturn(instrumentPath);
        when(root.<String>get("category")).thenReturn(categoryPath);
        when(cb.coalesce(categoryPath, "")).thenReturn(normalized);
        when(cb.trim(normalized)).thenReturn(normalized);
        when(cb.equal(normalized, "")).thenReturn(blank);
        when(cb.notEqual(flowPath, FlowType.INTERNAL_TRANSFER)).thenReturn(nonTransfer);
        when(cb.and(nonTransfer, blank)).thenReturn(category);
        when(cb.isNull(instrumentPath)).thenReturn(instrument);
        when(cb.or(category, instrument)).thenReturn(either);
        when(cb.equal(userPath, owner)).thenReturn(ownerPredicate);
        Predicate expected = switch (reason) { case CATEGORY -> category; case INSTRUMENT -> instrument; case ALL -> either; };
        when(cb.and(ownerPredicate, null, expected)).thenReturn(scoped);
        when(transactions.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(call -> {
            Specification<Transaction> specification = call.getArgument(0);
            assertSame(scoped, specification.toPredicate(root, mock(CriteriaQuery.class), cb));
            return Page.empty(PageRequest.of(0, 10));
        });
        service.getPendingTransactions(reason, PageRequest.of(0, 10));
        verify(cb).and(ownerPredicate, null, expected);
        Predicate base = mock(Predicate.class), combined = mock(Predicate.class);
        when(cb.and(any(Predicate[].class))).thenReturn(base);
        when(cb.and(ownerPredicate, null, expected)).thenReturn(scoped);
        when(cb.and(base, scoped)).thenReturn(combined);
        when(transactions.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(call -> {
            Specification<Transaction> specification = call.getArgument(0);
            assertSame(combined, specification.toPredicate(root, mock(CriteriaQuery.class), cb));
            return Page.empty(PageRequest.of(0, 10));
        });
        service.getTransactions(null, null, null, null, null, null, null, null, null, reason, PageRequest.of(0, 10));
        service.exportTransactionsCsv(null, null, null, null, null, null, null, null, null, reason);
        verify(cb, times(3)).and(ownerPredicate, null, expected);
        verify(cb, times(2)).and(base, scoped);
        UserContext.clear();
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.getPendingTransactions(reason, PageRequest.of(0, 10))).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> service.getTransactions(null, null, null, null, null,
                null, null, null, null, reason, PageRequest.of(0, 10)));
        assertThrows(ResponseStatusException.class, () -> service.exportTransactionsCsv(null, null, null, null, null,
                null, null, null, null, reason));
        verify(transactions, times(3)).findAll(any(Specification.class), any(Pageable.class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"=1+1\nnext", "  +1", "\t@SUM(1)"})
    void exportedTextCannotBecomeASpreadsheetFormula(String contact) {
        UserContext.setCurrentUser(User.builder().id(7L).build());
        when(transactions.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(Transaction.builder().contactName(contact).build())));
        String csv = new String(service.exportTransactionsCsv(null, null, null, null, null, null, null, null, null),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"'" + contact + "\""));
    }

    @Test
    void oversizedCsvExportRequiresNarrowerFilters() {
        UserContext.setCurrentUser(User.builder().id(7L).build());
        when(transactions.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(Transaction.builder().build()),
                        PageRequest.of(0, 10001), 10002));
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.exportTransactionsCsv(
                null, null, null, null, null, null, null, null, null)).getStatusCode().value());
    }

    private TransactionSyncRequest request() {
        TransactionSyncRequest request = new TransactionSyncRequest();
        request.setAmount(new BigDecimal("12.50"));
        request.setFlowType(FlowType.EXPENSE);
        request.setContactName("Test merchant");
        request.setTransactionDate(LocalDateTime.of(2026, 9, 1, 12, 0));
        request.setTransactionHash("shared-hash");
        request.setRawNotificationText("Test notification");
        return request;
    }
}
