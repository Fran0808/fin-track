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
    void clearContext() {
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
