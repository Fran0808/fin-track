package com.store.api.service;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.TransactionSyncRequest;
import com.store.api.model.entity.Transaction;
import com.store.api.model.entity.User;
import com.store.api.model.enums.FlowType;
import com.store.api.repository.RawNotificationRepository;
import com.store.api.repository.TransactionRepository;
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
    private final TransactionService service = new TransactionService(transactions, rawNotifications);

    @AfterEach
    public void clearContext() {
        UserContext.clear();
    }

    @Test
    void anonymousOperationsCannotReadOrWriteAnything() {
        UserContext.clear();
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.processAndSave(request())).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.processBatch(List.of())).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.getTransactions(null, null, null, null, PageRequest.of(0, 10)))
                .getStatusCode().value());
        verifyNoInteractions(transactions, rawNotifications);
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
    @SuppressWarnings("unchecked")
    void listingAlwaysIncludesTheCurrentUserPredicate() {
        Root<Transaction> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path<Object> userPath = mock(Path.class);
        Predicate ownerPredicate = mock(Predicate.class);
        when(root.get("user")).thenReturn(userPath);
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
        verify(builder, times(2)).and(new Predicate[] {ownerPredicate});
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

        when(transactions.findByIdAndUser(50L, user)).thenReturn(Optional.of(existing));
        when(transactions.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.store.api.model.dto.TransactionClassificationRequest request = com.store.api.model.dto.TransactionClassificationRequest.builder()
                .category("ALIMENTACION")
                .tags(List.of("almuerzo", "reembolsable"))
                .notes("Almuerzo con compañeros")
                .build();

        var response = service.updateClassification(50L, request);

        assertEquals("ALIMENTACION", response.getCategory());
        assertEquals(List.of("almuerzo", "reembolsable"), response.getTags());
        assertEquals("Almuerzo con compañeros", response.getNotes());
        verify(transactions).save(existing);
    }

    @Test
    void updateClassificationRejectsUnknownOrUnauthorizedTransaction() {
        User user = User.builder().id(5L).email("user@example.test").build();
        UserContext.setCurrentUser(user);
        when(transactions.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

        com.store.api.model.dto.TransactionClassificationRequest request = new com.store.api.model.dto.TransactionClassificationRequest();
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
                .tags("comida,snacks")
                .channel("TARJETA_DEBITO_BCP")
                .cardLast4("1234")
                .notes("Compra semanal")
                .transactionDate(LocalDateTime.of(2026, 9, 20, 10, 30))
                .transactionHash("hash-export-1")
                .build();

        when(transactions.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(t1));

        byte[] csvBytes = service.exportTransactionsCsv(null, null, null, null, null, null, null, null, null, null);
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("\uFEFF"), "CSV must start with UTF-8 BOM");
        assertTrue(csv.contains("ID,Fecha,Comercio o contacto,Importe,Tipo,Categoria,Etiquetas,Medio,Tarjeta,Notas,Hash"));
        assertTrue(csv.contains("\"Metro Express\""));
        assertTrue(csv.contains("\"ALIMENTACION\""));
        assertTrue(csv.contains("\"comida,snacks\""));
        assertTrue(csv.contains("\"Compra semanal\""));
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
