package com.store.api.service.transaction;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.transaction.TransactionClassificationRequest;
import com.store.api.model.entity.Transaction;
import com.store.api.model.entity.User;
import com.store.api.repository.CategoryRepository;
import com.store.api.repository.RawNotificationRepository;
import com.store.api.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClassificationValidationTest {
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final TransactionService service = new TransactionService(transactions,
            mock(RawNotificationRepository.class), categories);
    private final User owner = User.builder().id(1L).build();

    @AfterEach void clear() { UserContext.clear(); }

    private Transaction prepare(String category) {
        UserContext.setCurrentUser(owner);
        Transaction tx = Transaction.builder().id(10L).user(owner).category(category).build();
        when(transactions.findByIdAndUser(10L, owner)).thenReturn(Optional.of(tx));
        when(transactions.save(any())).thenAnswer(call -> call.getArgument(0));
        return tx;
    }

    @Test void acceptsOwnedActiveCustomCategoryWithOneHundredCharacters() {
        prepare(null);
        String name = "a".repeat(100);
        when(categories.existsByUserAndActiveTrueAndNameIgnoreCase(owner, name)).thenReturn(true);
        assertEquals(name, service.updateClassification(10L,
                TransactionClassificationRequest.builder().category(" " + name + " ").build()).getCategory());
    }

    @Test void rejectsCategoryOwnedOnlyByAnotherUser() {
        Transaction tx = prepare("FOOD");
        User other = User.builder().id(2L).build();
        when(categories.existsByUserAndActiveTrueAndNameIgnoreCase(other, "Private")).thenReturn(true);
        var error = assertThrows(ResponseStatusException.class, () -> service.updateClassification(10L,
                TransactionClassificationRequest.builder().category("Private").build()));
        assertEquals(400, error.getStatusCode().value());
        assertEquals("FOOD", tx.getCategory());
        verify(categories).existsByUserAndActiveTrueAndNameIgnoreCase(owner, "Private");
        verify(transactions, never()).save(any());
    }

    @Test void rejectsInactiveOrUnknownCategory() {
        prepare(null);
        assertThrows(ResponseStatusException.class, () -> service.updateClassification(10L,
                TransactionClassificationRequest.builder().category("Inactive").build()));
        verify(transactions, never()).save(any());
    }

    @Test void preservesLegacyCategoryWhenEditingNotes() {
        prepare("FOOD");
        var result = service.updateClassification(10L,
                TransactionClassificationRequest.builder().category("FOOD").notes("Test note").build());
        assertEquals("FOOD", result.getCategory());
        assertEquals("Test note", result.getNotes());
        verifyNoInteractions(categories);
    }

    @Test void blankClearsCategoryAndNullLeavesItUnchanged() {
        prepare("FOOD");
        assertEquals("FOOD", service.updateClassification(10L, new TransactionClassificationRequest()).getCategory());
        assertNull(service.updateClassification(10L,
                TransactionClassificationRequest.builder().category(" ").build()).getCategory());
        verifyNoInteractions(categories);
    }

    @Test void anonymousClassificationCannotAccessRepositories() {
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.updateClassification(10L,
                new TransactionClassificationRequest())).getStatusCode().value());
        verifyNoInteractions(transactions, categories);
    }
}
